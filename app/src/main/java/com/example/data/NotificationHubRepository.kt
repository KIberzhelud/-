package com.example.data

import android.app.Notification
import android.app.PendingIntent
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.model.HubActionSummary
import com.example.model.HubChatMessage
import com.example.model.HubNotificationItem
import com.example.model.HubSubTab
import com.example.model.LockedChatThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private data class CachedNotificationActions(
    val packageName: String,
    val contentIntent: PendingIntent?,
    val replyAction: NotificationCompat.Action?,
    val replyInput: RemoteInput?,
    val extraActions: List<NotificationCompat.Action>
)

object NotificationHubRepository {

    private val _notifications = MutableStateFlow<List<HubNotificationItem>>(emptyList())
    val notifications: StateFlow<List<HubNotificationItem>> = _notifications.asStateFlow()

    private val actionCache = ConcurrentHashMap<String, CachedNotificationActions>()
    private val appIconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val appLabelCache = ConcurrentHashMap<String, String>()
    private val archivedThreads = LinkedHashMap<String, ArchivedChatSummary>()
    private val archiveLock = Any()
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    @Volatile
    private var archiveLoaded = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var serviceRef: WeakReference<LauncherMediaListenerService>? = null

    private data class ArchivedChatSummary(
        val id: String,
        val packageName: String,
        val appName: String,
        val contactName: String,
        val lastMessage: String,
        val timestampMs: Long,
        val notificationKey: String,
        val senderHandle: String? = null
    )

    fun attachService(service: LauncherMediaListenerService) {
        appContext = service.applicationContext
        ensureArchiveLoaded(service.applicationContext)
        serviceRef = WeakReference(service)
        syncFromService(service)
    }

    fun detachService() {
        serviceRef = null
    }

    fun refreshFromAttachedService() {
        serviceRef?.get()?.let { syncFromService(it) }
    }

    fun syncFromService(service: LauncherMediaListenerService) {
        appContext = service.applicationContext
        ensureArchiveLoaded(service.applicationContext)
        val active = runCatching { service.activeNotifications }.getOrNull() ?: return
        val selfPkg = service.packageName
        val parsed = active
            .asSequence()
            .filter { sbn ->
                sbn.packageName != selfPkg && !isPureMediaTransport(sbn)
            }
            .mapNotNull { sbn ->
                parseNotification(service, sbn)?.let { item ->
                    sbn.notification?.category to item
                }
            }
            .sortedWith(
                compareBy<Pair<String?, HubNotificationItem>> { (category, item) ->
                    notificationPriorityTier(
                        packageName = item.packageName,
                        appName = item.appName,
                        category = category
                    )
                }.thenByDescending { (_, item) -> item.postTime }
            )
            .map { it.second }
            .toList()

        val validKeys = parsed.mapTo(HashSet()) { it.key }
        actionCache.keys.retainAll(validKeys)
        _notifications.value = parsed
        recordChatsIntoArchive(service.applicationContext, parsed)
    }

    fun ensureArchiveLoaded(context: Context) {
        if (archiveLoaded) return
        synchronized(archiveLock) {
            if (archiveLoaded) return
            val file = File(context.filesDir, "messenger_chats_archive.json")
            if (file.exists()) {
                runCatching {
                    val arr = JSONArray(file.readText())
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").trim()
                        val pkg = obj.optString("packageName").trim()
                        val contact = obj.optString("contactName").trim()
                        if (id.isEmpty() || pkg.isEmpty() || contact.isEmpty()) continue
                        archivedThreads[id] = ArchivedChatSummary(
                            id = id,
                            packageName = pkg,
                            appName = obj.optString("appName", pkg),
                            contactName = contact,
                            lastMessage = obj.optString("lastMessage", ""),
                            timestampMs = obj.optLong("timestampMs", 0L),
                            notificationKey = obj.optString("notificationKey", id),
                            senderHandle = obj.optString("senderHandle").takeIf { it.isNotBlank() }
                        )
                    }
                }
            }
            archiveLoaded = true
        }
    }

    private fun recordChatsIntoArchive(context: Context, items: List<HubNotificationItem>) {
        val chatItems = items.filter { it.subTab == HubSubTab.CHATS && it.title.isNotBlank() }
        if (chatItems.isEmpty()) return
        synchronized(archiveLock) {
            var changed = false
            chatItems.forEach { item ->
                val cleanTitle = item.title.trim()
                val threadId = "archive:${item.packageName}:${cleanTitle.lowercase()}"
                val lastText = item.messages.lastOrNull()?.text?.ifBlank { item.text } ?: item.text
                val handle = Regex("""(@[A-Za-z0-9_]{3,}|[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}|\+?\d{7,15})""")
                    .find("$cleanTitle $lastText")?.value
                val prev = archivedThreads[threadId]
                if (prev == null || prev.lastMessage != lastText || prev.timestampMs < item.postTime) {
                    archivedThreads[threadId] = ArchivedChatSummary(
                        id = threadId,
                        packageName = item.packageName,
                        appName = item.appName,
                        contactName = cleanTitle,
                        lastMessage = lastText,
                        timestampMs = item.postTime,
                        notificationKey = item.key,
                        senderHandle = handle ?: prev?.senderHandle
                    )
                    changed = true
                }
            }
            if (archivedThreads.size > 120) {
                val sortedKeys = archivedThreads.entries
                    .sortedBy { it.value.timestampMs }
                    .take(archivedThreads.size - 120)
                    .map { it.key }
                sortedKeys.forEach { archivedThreads.remove(it) }
                changed = true
            }
            if (changed) {
                saveArchiveLocked(context)
            }
        }
    }

    private fun saveArchiveLocked(context: Context) {
        runCatching {
            val arr = JSONArray()
            archivedThreads.values
                .sortedByDescending { it.timestampMs }
                .take(120)
                .forEach { entry ->
                    arr.put(
                        JSONObject().apply {
                            put("id", entry.id)
                            put("packageName", entry.packageName)
                            put("appName", entry.appName)
                            put("contactName", entry.contactName)
                            put("lastMessage", entry.lastMessage)
                            put("timestampMs", entry.timestampMs)
                            put("notificationKey", entry.notificationKey)
                            entry.senderHandle?.let { put("senderHandle", it) }
                        }
                    )
                }
            File(context.filesDir, "messenger_chats_archive.json").writeText(arr.toString())
        }
    }

    fun searchArchivedThreads(context: Context, query: String): List<LockedChatThread> {
        ensureArchiveLoaded(context)
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val snapshot = synchronized(archiveLock) { archivedThreads.values.toList() }
        val activeNotifs = _notifications.value
        return snapshot
            .filter {
                KeyboardSearchMatcher.matches(it.contactName, clean) ||
                    KeyboardSearchMatcher.matches(it.appName, clean) ||
                    KeyboardSearchMatcher.matches(it.lastMessage, clean) ||
                    (it.senderHandle != null && it.senderHandle.contains(clean, ignoreCase = true))
            }
            .sortedByDescending { it.timestampMs }
            .take(10)
            .map { entry ->
                val liveMatch = activeNotifs.firstOrNull { n ->
                    n.packageName == entry.packageName &&
                        n.title.equals(entry.contactName, ignoreCase = true)
                }
                val resolvedKey = liveMatch?.key ?: entry.notificationKey.ifBlank { entry.id }
                val timeStr = if (entry.timestampMs > 0L) timeFmt.format(Date(entry.timestampMs)) else ""
                LockedChatThread(
                    key = resolvedKey,
                    packageName = entry.packageName,
                    appName = entry.appName,
                    contactName = entry.contactName,
                    messages = if (entry.lastMessage.isNotBlank()) {
                        listOf(
                            HubChatMessage(
                                sender = entry.contactName,
                                text = entry.lastMessage,
                                timestamp = entry.timestampMs
                            )
                        )
                    } else emptyList(),
                    canReplyInline = liveMatch?.canReplyInline == true,
                    supportsMediaReply = liveMatch?.supportsMediaReply == true,
                    lastTimestamp = entry.timestampMs,
                    timeFormatted = timeStr,
                    fallbackPhoneNumber = entry.senderHandle,
                    appIcon = resolveAppIcon(context, entry.packageName)
                )
            }
    }

    fun isSmsPackage(packageName: String): Boolean {
        val low = packageName.lowercase()
        return low.contains("mms") || low.contains("apps.messaging") || low.endsWith(".messaging") || low == "sms"
    }

    fun openChatThreadInMessenger(context: Context, thread: LockedChatThread, messageText: String = ""): Boolean {
        if (thread.key.isNotBlank() &&
            !thread.key.startsWith("archive:") &&
            !thread.key.startsWith("contact:") &&
            openNotification(context, thread.key)
        ) {
            return true
        }
        val pkg = thread.packageName.lowercase()
        val handle = thread.fallbackPhoneNumber?.trim().orEmpty()
        if (pkg.contains("telegram") || pkg.contains("exteragram") || pkg.contains("nekogram") ||
            pkg.contains("ayugram") || pkg.contains("challegram") || pkg.contains("nagram")
        ) {
            if (handle.startsWith("@") && messageText.isBlank()) {
                val tgUri = Uri.parse("tg://resolve?domain=${handle.removePrefix("@")}")
                val tgIntent = Intent(Intent.ACTION_VIEW, tgUri).apply {
                    if (thread.packageName.isNotBlank()) setPackage(thread.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (runCatching { context.startActivity(tgIntent); true }.getOrDefault(false)) return true
            }
        }
        if (pkg.contains("whatsapp") && handle.any { it.isDigit() }) {
            val digits = handle.filter { it.isDigit() }
            if (digits.length >= 7) {
                val url = if (messageText.isNotBlank()) {
                    "https://wa.me/$digits?text=${Uri.encode(messageText)}"
                } else {
                    "https://wa.me/$digits"
                }
                val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    if (thread.packageName.isNotBlank()) setPackage(thread.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (runCatching { context.startActivity(waIntent); true }.getOrDefault(false)) return true
            }
        }
        if ((pkg.contains("gmail") || pkg.contains("mail") || pkg.contains("outlook")) && handle.contains("@")) {
            val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$handle")).apply {
                if (messageText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, messageText)
                if (thread.packageName.isNotBlank()) setPackage(thread.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(mailIntent); true }.getOrDefault(false)) return true
        }
        if (messageText.isNotBlank() && thread.packageName.isNotBlank() && !isSmsPackage(thread.packageName)) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                setPackage(thread.packageName)
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, messageText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(sendIntent); true }.getOrDefault(false)) return true
        }
        if (thread.packageName.isNotBlank()) {
            val launch = context.packageManager.getLaunchIntentForPackage(thread.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launch != null && runCatching { context.startActivity(launch); true }.getOrDefault(false)) {
                return true
            }
        }
        return false
    }

    fun onPosted(service: LauncherMediaListenerService, sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == service.packageName) return
        syncFromService(service)
    }

    fun onRemoved(service: LauncherMediaListenerService, sbn: StatusBarNotification?) {
        if (sbn != null) {
            actionCache.remove(sbn.key)
        }
        syncFromService(service)
    }

    fun openNotification(context: Context, key: String): Boolean {
        val cached = actionCache[key] ?: return false
        val pi = cached.contentIntent ?: return false
        return runCatching {
            pi.send()
            true
        }.getOrDefault(false)
    }

    fun invokeAction(key: String, actionIndex: Int): Boolean {
        val cached = actionCache[key] ?: return false
        val action = cached.extraActions.getOrNull(actionIndex) ?: return false
        val pi = action.actionIntent ?: return false
        return runCatching {
            pi.send()
            true
        }.getOrDefault(false)
    }

    fun dismissNotification(key: String): Boolean {
        val service = serviceRef?.get() ?: return false
        return runCatching {
            service.cancelNotification(key)
            actionCache.remove(key)
            _notifications.value = _notifications.value.filterNot { it.key == key }
            true
        }.getOrDefault(false)
    }

    fun clearAllNotifications() {
        val service = serviceRef?.get()
        val dismissible = _notifications.value.filterNot { it.isOngoing }
        dismissible.forEach { item ->
            runCatching { service?.cancelNotification(item.key) }
            actionCache.remove(item.key)
        }
        _notifications.value = _notifications.value.filter { it.isOngoing }
    }

    fun replyToNotification(
        context: Context,
        key: String,
        replyText: String,
        attachmentUri: Uri? = null,
        attachmentMimeType: String? = null
    ): Result<String> {
        val cached = actionCache[key]
            ?: return Result.failure(IllegalStateException("Уведомление устарело или уже закрыто"))

        val replyAction = cached.replyAction
        val remoteInput = cached.replyInput
        val actionIntent = replyAction?.actionIntent

        // Case 1: Attachment provided
        if (attachmentUri != null) {
            val mime = attachmentMimeType ?: context.contentResolver.getType(attachmentUri) ?: "*/*"
            runCatching {
                context.grantUriPermission(
                    cached.packageName,
                    attachmentUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val allowedTypes = remoteInput?.allowedDataTypes.orEmpty()
            val acceptsInlineMedia = remoteInput != null && actionIntent != null && allowedTypes.any { allowed ->
                allowed == "*/*" || allowed.equals(mime, ignoreCase = true) ||
                    (allowed.endsWith("/*") && mime.startsWith(allowed.substringBefore("/"), ignoreCase = true))
            }

            if (acceptsInlineMedia) {
                return runCatching {
                    val fillIntent = Intent().apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(context.contentResolver, "attachment", attachmentUri)
                    }
                    if (replyText.isNotBlank()) {
                        val bundle = Bundle().apply {
                            putCharSequence(remoteInput!!.resultKey, replyText)
                        }
                        RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillIntent, bundle)
                    }
                    RemoteInput.addDataResultToIntent(
                        remoteInput!!,
                        fillIntent,
                        mapOf(mime to attachmentUri)
                    )
                    actionIntent!!.send(context, 0, fillIntent)
                    "Ответ с вложением отправлен"
                }
            }

            // Otherwise send text via RemoteInput if present, and hand the file directly to the target app
            if (replyText.isNotBlank() && remoteInput != null && actionIntent != null) {
                runCatching {
                    val fillIntent = Intent()
                    val bundle = Bundle().apply {
                        putCharSequence(remoteInput.resultKey, replyText)
                    }
                    RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillIntent, bundle)
                    actionIntent.send(context, 0, fillIntent)
                }
            }

            return runCatching {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    setPackage(cached.packageName)
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, attachmentUri)
                    if (replyText.isNotBlank()) {
                        putExtra(Intent.EXTRA_TEXT, replyText)
                    }
                    clipData = ClipData.newUri(context.contentResolver, "attachment", attachmentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sendIntent)
                "Файл передан в приложение для отправки"
            }
        }

        // Case 2: Text-only inline reply via RemoteInput
        if (replyText.isBlank()) {
            return Result.failure(IllegalArgumentException("Введите текст или прикрепите файл"))
        }
        if (remoteInput == null || actionIntent == null) {
            return Result.failure(IllegalStateException("Это уведомление не поддерживает быстрый ответ"))
        }

        return runCatching {
            val fillIntent = Intent()
            val bundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillIntent, bundle)
            actionIntent.send(context, 0, fillIntent)
            "Ответ отправлен"
        }
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val expected = ComponentName(context, LauncherMediaListenerService::class.java).flattenToString()
        return enabledListeners.split(':').any { entry ->
            entry.equals(expected, ignoreCase = true) ||
                entry.contains(context.packageName, ignoreCase = true)
        }
    }

    private fun parseNotification(
        context: Context,
        sbn: StatusBarNotification
    ): HubNotificationItem? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: Bundle.EMPTY

        val rawTitle = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val rawText = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val convTitle = extras.getCharSequence(NotificationCompat.EXTRA_CONVERSATION_TITLE)?.toString()?.trim().orEmpty()

        val messagingStyle = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        }.getOrNull()

        val messages = messagingStyle?.messages?.takeLast(5)?.map { msg ->
            HubChatMessage(
                sender = msg.person?.name?.toString()?.takeIf { it.isNotBlank() } ?: rawTitle,
                text = msg.text?.toString().orEmpty(),
                timestamp = msg.timestamp
            )
        }.orEmpty()

        val displayTitle = convTitle.ifBlank { rawTitle.ifBlank { resolveAppLabel(context, sbn.packageName) } }
        val displayBody = when {
            messages.isNotEmpty() -> messages.last().text
            bigText.isNotBlank() -> bigText
            else -> rawText
        }

        if (displayTitle.isBlank() && displayBody.isBlank()) return null

        val allActions = extractAllActions(notification)
        var replyAction: NotificationCompat.Action? = null
        var replyInput: RemoteInput? = null
        val extraActions = mutableListOf<NotificationCompat.Action>()

        for (action in allActions) {
            val remoteInputs = action.remoteInputs
            val freeForm = remoteInputs?.firstOrNull { it.allowFreeFormInput || !it.allowedDataTypes.isNullOrEmpty() }
            if (freeForm != null && replyAction == null) {
                replyAction = action
                replyInput = freeForm
            } else if (!action.title.isNullOrBlank() && action.actionIntent != null) {
                extraActions.add(action)
            }
        }

        val supportsMedia = replyInput?.allowedDataTypes?.isNotEmpty() == true
        val subTab = categorizeNotification(
            packageName = sbn.packageName,
            category = notification.category,
            hasReply = replyAction != null,
            hasMessagingStyle = messagingStyle != null,
            title = displayTitle,
            body = displayBody
        )

        val extractedUrl = urlRegex.find("$bigText\n$rawText")?.value

        actionCache[sbn.key] = CachedNotificationActions(
            packageName = sbn.packageName,
            contentIntent = notification.contentIntent,
            replyAction = replyAction,
            replyInput = replyInput,
            extraActions = extraActions
        )

        val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sbn.postTime))

        return HubNotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = resolveAppLabel(context, sbn.packageName),
            title = displayTitle,
            text = rawText.ifBlank { displayBody },
            bigText = bigText,
            conversationTitle = convTitle,
            postTime = sbn.postTime,
            timeFormatted = timeFormatted,
            subTab = subTab,
            canReplyInline = replyAction != null && replyInput != null,
            supportsMediaReply = supportsMedia,
            messages = messages,
            actions = extraActions.mapIndexed { idx, act ->
                HubActionSummary(idx, act.title?.toString().orEmpty())
            },
            extractedUrl = extractedUrl,
            isOngoing = sbn.isOngoing,
            appIcon = resolveAppIcon(context, sbn.packageName)
        )
    }

    private fun extractAllActions(notification: Notification): List<NotificationCompat.Action> {
        val result = mutableListOf<NotificationCompat.Action>()
        val count = NotificationCompat.getActionCount(notification)
        for (i in 0 until count) {
            NotificationCompat.getAction(notification, i)?.let { result.add(it) }
        }
        val wearableActions = NotificationCompat.WearableExtender(notification).actions
        wearableActions.forEach { wAction ->
            if (result.none { it.title == wAction.title }) {
                result.add(wAction)
            }
        }
        return result
    }

    private fun isPureMediaTransport(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        if (n.category == Notification.CATEGORY_TRANSPORT) return true
        return n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
    }

    private fun categorizeNotification(
        packageName: String,
        category: String?,
        hasReply: Boolean,
        hasMessagingStyle: Boolean,
        title: String,
        body: String
    ): HubSubTab = categorizeNotificationForTest(
        packageName = packageName,
        category = category,
        hasReply = hasReply,
        hasMessagingStyle = hasMessagingStyle,
        title = title,
        body = body
    )

    internal fun categorizeNotificationForTest(
        packageName: String,
        category: String?,
        hasReply: Boolean,
        hasMessagingStyle: Boolean,
        title: String,
        body: String
    ): HubSubTab {
        val pkg = packageName.lowercase()
        val combined = "$title $body".lowercase()

        val isGoogleNewsOrDiscoverPkg = pkg == "com.google.android.apps.magazines" ||
            pkg == "com.google.android.apps.news" ||
            pkg == "com.google.android.googlequicksearchbox"

        val isNewsApp = pkg.contains("news") || pkg.contains("rss") || pkg.contains("feedly") ||
            pkg.contains("inoreader") || pkg.contains("flipboard") || pkg.contains("habr") ||
            pkg.contains("bbc") || pkg.contains("cnn") || pkg.contains("rbc") ||
            pkg.contains("tass") || pkg.contains("meduza") || pkg.contains("dzen") ||
            pkg.contains("reddit") || pkg.contains("medium") || pkg.contains("substack") ||
            pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("opera") ||
            pkg.contains("yandex.browser") || pkg.contains("vivaldi") || pkg.contains("edge")

        val hasNewsContentHints = combined.contains("главные новости") ||
            combined.contains("google новости") ||
            combined.contains("подборка новостей") ||
            combined.contains("новости") ||
            combined.contains("discover") ||
            combined.contains("breaking news") ||
            combined.contains("news digest") ||
            combined.contains("статья") ||
            combined.contains("дайджест")

        if (isNewsApp || category == Notification.CATEGORY_RECOMMENDATION ||
            (isGoogleNewsOrDiscoverPkg && !hasReply && !hasMessagingStyle) ||
            (isGoogleNewsOrDiscoverPkg && hasNewsContentHints)
        ) {
            return HubSubTab.NEWS
        }

        // Keep VK and Email inside CHATS so user still sees them, just below TG/WhatsApp/IM clients
        val isKnownChatPkg = pkg.contains("telegram") || pkg.contains("challegram") ||
            pkg.contains("exteragram") || pkg.contains("nekogram") || pkg.contains("ayugram") ||
            pkg.contains("nagram") || pkg.contains("forkgram") || pkg.contains("kotatogram") ||
            pkg.contains("cherrygram") || pkg.contains("whatsapp") ||
            pkg.contains("viber") || pkg.contains("signal") || pkg.contains("discord") ||
            pkg.contains("vk") || pkg.contains("messenger") || pkg.contains("slack") ||
            pkg.contains("teams") || pkg.contains("mms") || pkg.contains("messaging") ||
            pkg.contains("gmail") || pkg.contains("mail") || pkg.contains("outlook") ||
            pkg.contains("threema") || pkg.contains("element") || pkg.contains("icq") ||
            pkg.contains("tamtam")

        if (hasReply || hasMessagingStyle ||
            category == Notification.CATEGORY_MESSAGE ||
            category == Notification.CATEGORY_EMAIL ||
            isKnownChatPkg
        ) {
            return HubSubTab.CHATS
        }

        if (hasNewsContentHints || (urlRegex.containsMatchIn(combined) && combined.contains("подборк"))) {
            return HubSubTab.NEWS
        }

        return HubSubTab.OTHER
    }

    /**
     * Hub priority tiers (lower value = shown higher in list even when older):
     * 0: Telegram (official, X, and third-party TG clients) & WhatsApp (+ Business/clients)
     * 1: Other messaging / chat clients (Viber, Signal, Discord, SMS, Element, Slack, etc.)
     * 2: General notifications (neither TG/WA/IM nor VK/Email)
     * 3: VK & Email clients (explicitly kept below TG, WhatsApp, and IM clients)
     */
    fun notificationPriorityTier(
        packageName: String,
        appName: String = "",
        category: String? = null
    ): Int {
        val pkg = packageName.lowercase()
        val label = appName.lowercase()

        fun matchesAny(vararg tokens: String): Boolean =
            tokens.any { pkg.contains(it) || label.contains(it) }

        val isTelegramOrClient = matchesAny(
            "org.telegram", "telegram", "challegram", "exteragram", "nekogram",
            "ayugram", "nagram", "forkgram", "kotatogram", "cherrygram", "graphmessenger",
            "telegreat", "bgram", "octogram", "momogram", "owlgram", "turrit", "nicegram",
            "uzgram", "vidogram", "plusmessenger", "messenger.plus", "imem", "telegraph",
            "телеграм", "extera", "neko"
        ) || pkg.endsWith(".ime") || pkg.contains(".ime.")

        val isWhatsApp = matchesAny("whatsapp", "com.whatsapp", "ватсап", "wa business")

        if (isTelegramOrClient || isWhatsApp) {
            return 0
        }

        val isVk = matchesAny(
            "com.vkontakte", "com.vk.", "vk.im", "vkontakte", "vkvideo", "vkmusic",
            "vkclips", "sferum", "вконтакте", "vk мессенджер"
        ) || label == "vk"

        val isEmail = category == Notification.CATEGORY_EMAIL || matchesAny(
            "google.android.gm", "gmail", "outlook", "office.outlook", "yandex.mail",
            "ru.yandex.mail", "mail.ru", "ru.mail", "k9mail", "fairemail", "thunderbird",
            "protonmail", "ch.protonmail", "proton.android.mail", "sparkmail", "bluemail",
            "aquamail", "android.email", "samsung.android.email", "mymail", "zoho.mail",
            "почта", "email", "e-mail"
        )

        if (isVk || isEmail) {
            return 3
        }

        val isOtherMessenger = category == Notification.CATEGORY_MESSAGE || matchesAny(
            "signal", "viber", "threema", "element", "matrix", "discord", "slack",
            "teams", "icq", "tamtam", "skype", "mattermost", "rocketchat", "simplex",
            "session", "conversations", "briar", "mms", "messaging", "apps.messaging",
            "imo", "wechat", "line", "kakao", "messenger"
        )

        return if (isOtherMessenger) 1 else 2
    }

    fun sortNotificationsByPriority(items: List<HubNotificationItem>): List<HubNotificationItem> {
        return items.sortedWith(
            compareBy<HubNotificationItem> {
                notificationPriorityTier(it.packageName, it.appName, null)
            }.thenByDescending { it.postTime }
        )
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        return appLabelCache.getOrPut(packageName) {
            runCatching {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            }.getOrDefault(packageName)
        }
    }

    private fun resolveAppIcon(context: Context, packageName: String): ImageBitmap? {
        appIconCache[packageName]?.let { return it }
        val bmp = runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable).asImageBitmap()
        }.getOrNull() ?: return null
        appIconCache[packageName] = bmp
        return bmp
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.coerceIn(48, 96)
        val h = drawable.intrinsicHeight.coerceIn(48, 96)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }
}
