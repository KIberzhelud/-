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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    @Volatile
    private var serviceRef: WeakReference<LauncherMediaListenerService>? = null

    fun attachService(service: LauncherMediaListenerService) {
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
        val active = runCatching { service.activeNotifications }.getOrNull() ?: return
        val selfPkg = service.packageName
        val parsed = active
            .asSequence()
            .filter { sbn ->
                sbn.packageName != selfPkg && !isPureMediaTransport(sbn)
            }
            .mapNotNull { sbn -> parseNotification(service, sbn) }
            .sortedByDescending { it.postTime }
            .toList()

        val validKeys = parsed.mapTo(HashSet()) { it.key }
        actionCache.keys.retainAll(validKeys)
        _notifications.value = parsed
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
    ): HubSubTab {
        val pkg = packageName.lowercase()
        val isNewsApp = pkg.contains("news") || pkg.contains("rss") || pkg.contains("feedly") ||
            pkg.contains("inoreader") || pkg.contains("flipboard") || pkg.contains("habr") ||
            pkg.contains("bbc") || pkg.contains("cnn") || pkg.contains("rbc") ||
            pkg.contains("tass") || pkg.contains("meduza") || pkg.contains("dzen") ||
            pkg.contains("reddit") || pkg.contains("medium") || pkg.contains("substack") ||
            pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("opera") ||
            pkg.contains("yandex.browser") || pkg.contains("vivaldi") || pkg.contains("edge")

        if (isNewsApp || category == Notification.CATEGORY_RECOMMENDATION) {
            return HubSubTab.NEWS
        }

        val isKnownChatPkg = pkg.contains("telegram") || pkg.contains("whatsapp") ||
            pkg.contains("viber") || pkg.contains("signal") || pkg.contains("discord") ||
            pkg.contains("vk") || pkg.contains("messenger") || pkg.contains("slack") ||
            pkg.contains("teams") || pkg.contains("mms") || pkg.contains("messaging") ||
            pkg.contains("gmail") || pkg.contains("mail") || pkg.contains("threema") ||
            pkg.contains("element") || pkg.contains("icq") || pkg.contains("tamtam")

        if (hasReply || hasMessagingStyle ||
            category == Notification.CATEGORY_MESSAGE ||
            category == Notification.CATEGORY_EMAIL ||
            isKnownChatPkg
        ) {
            return HubSubTab.CHATS
        }

        val combined = "$title $body".lowercase()
        if (urlRegex.containsMatchIn(combined) &&
            (combined.contains("статья") || combined.contains("новост") || combined.contains("дайджест"))
        ) {
            return HubSubTab.NEWS
        }

        return HubSubTab.OTHER
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
