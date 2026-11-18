package com.example.data

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.AdaptedAppSnapshot
import com.example.model.AdaptedItemNode
import com.example.model.AdaptedTabNode
import com.example.model.AppAdaptationMode
import com.example.ui.theme.BlueVistaTokens
import com.example.ui.theme.VistaThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap

object AppAdaptationRuntime {
    @Volatile
    var isAdaptationEnabled: Boolean = false

    @Volatile
    var currentThemeTokens: VistaThemeTokens = BlueVistaTokens

    private val modesMap = ConcurrentHashMap<String, AppAdaptationMode>()
    private val appLabelsMap = ConcurrentHashMap<String, String>()

    private val _activeSnapshot = MutableStateFlow<AdaptedAppSnapshot?>(null)
    val activeSnapshot: StateFlow<AdaptedAppSnapshot?> = _activeSnapshot.asStateFlow()

    private val _embeddedSnapshot = MutableStateFlow<AdaptedAppSnapshot?>(null)
    val embeddedSnapshot: StateFlow<AdaptedAppSnapshot?> = _embeddedSnapshot.asStateFlow()

    @Volatile
    var onCommandCallback: ((AdaptationCommand) -> Unit)? = null

    @Volatile
    var onDisablePackageCallback: ((String) -> Unit)? = null

    fun updateModes(modes: Map<String, AppAdaptationMode>, labels: Map<String, String>) {
        modesMap.clear()
        modesMap.putAll(modes)
        appLabelsMap.putAll(labels)
    }

    fun modeFor(packageName: String): AppAdaptationMode {
        return modesMap[packageName] ?: AppAdaptationMode.DISABLED
    }

    fun labelFor(packageName: String): String {
        return appLabelsMap[packageName] ?: packageName.substringAfterLast('.')
    }

    fun updateSnapshot(snapshot: AdaptedAppSnapshot?) {
        _activeSnapshot.value = snapshot
        if (snapshot != null && snapshot.mode != AppAdaptationMode.DISABLED) {
            _embeddedSnapshot.value = snapshot
        }
    }

    fun updateEmbeddedSnapshot(snapshot: AdaptedAppSnapshot?) {
        _embeddedSnapshot.value = snapshot
    }

    fun sendCommand(cmd: AdaptationCommand) {
        if (cmd is AdaptationCommand.DisablePackage) {
            onDisablePackageCallback?.invoke(cmd.packageName)
            if (_embeddedSnapshot.value?.packageName == cmd.packageName) {
                _embeddedSnapshot.value = null
            }
        }
        onCommandCallback?.invoke(cmd)
    }

    fun isDefaultMessengerPackage(packageName: String, label: String): Boolean {
        val pkg = packageName.lowercase()
        val lbl = label.lowercase()
        return pkg.contains("telegram") ||
            pkg.contains("exteragram") ||
            pkg.contains("nekogram") ||
            pkg.contains("ayugram") ||
            pkg.contains("challegram") ||
            pkg.contains("whatsapp") ||
            pkg.contains("viber") ||
            pkg.contains("discord") ||
            pkg.contains("signal") ||
            pkg == "com.vkontakte.android" ||
            pkg.contains("vk.im") ||
            lbl.contains("telegram") ||
            lbl.contains("whatsapp") ||
            lbl.contains("viber") ||
            lbl.contains("мессенджер")
    }

    fun isDefaultSettingsPackage(packageName: String, label: String): Boolean {
        val pkg = packageName.lowercase()
        val lbl = label.lowercase()
        return pkg == "com.android.settings" ||
            pkg.endsWith(".settings") ||
            pkg == "com.samsung.android.settings" ||
            pkg == "com.miui.securitycenter" ||
            lbl == "настройки" ||
            lbl == "settings"
    }

    fun recommendedDefaultMode(packageName: String, label: String): AppAdaptationMode {
        return when {
            isDefaultMessengerPackage(packageName, label) -> AppAdaptationMode.MESSENGER
            isDefaultSettingsPackage(packageName, label) -> AppAdaptationMode.STANDARD
            else -> AppAdaptationMode.DISABLED
        }
    }
}

sealed interface AdaptationCommand {
    data class ClickTab(val index: Int) : AdaptationCommand
    data class MoveFocus(val delta: Int) : AdaptationCommand
    data object ActivateFocused : AdaptationCommand
    data class ScrollContent(val forward: Boolean) : AdaptationCommand
    data class SendText(val text: String) : AdaptationCommand
    data object NavigateBack : AdaptationCommand
    data object ToggleCollapse : AdaptationCommand
    data object RefreshTree : AdaptationCommand
    data class DisablePackage(val packageName: String) : AdaptationCommand
}

/**
 * Extracts navigable tabs (for F1-F6), interactive rows/chats (for arrow-key cursor),
 * and input/send nodes from AccessibilityNodeInfo window trees.
 */
class AppAdaptationTreeEngine {

    private var lastTabNodes: List<AccessibilityNodeInfo> = emptyList()
    private var lastItemNodes: List<AccessibilityNodeInfo> = emptyList()
    private var lastInputNode: AccessibilityNodeInfo? = null
    private var lastSendNode: AccessibilityNodeInfo? = null
    private var lastScrollNode: AccessibilityNodeInfo? = null

    fun clearRefs() {
        lastTabNodes = emptyList()
        lastItemNodes = emptyList()
        lastInputNode = null
        lastSendNode = null
        lastScrollNode = null
    }

    fun parseWindow(
        root: AccessibilityNodeInfo?,
        packageName: String,
        appLabel: String,
        mode: AppAdaptationMode,
        previousFocusIndex: Int,
        collapsed: Boolean
    ): AdaptedAppSnapshot? {
        if (root == null) return null

        val tabPairs = mutableListOf<Pair<AdaptedTabNode, AccessibilityNodeInfo>>()
        val itemPairs = mutableListOf<Pair<AdaptedItemNode, AccessibilityNodeInfo>>()
        var foundInput: AccessibilityNodeInfo? = null
        var inputHint = ""
        var foundSend: AccessibilityNodeInfo? = null
        var foundScroll: AccessibilityNodeInfo? = null

        val visited = HashSet<Int>(160)

        fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int = 0) {
            if (depth > 4 || out.size >= 4) return
            val t = node.text?.toString()?.trim().orEmpty()
            val d = node.contentDescription?.toString()?.trim().orEmpty()
            val chosen = t.ifBlank { d }
            if (chosen.isNotBlank() && out.none { it == chosen }) {
                out.add(chosen)
            }
            for (i in 0 until node.childCount) {
                val ch = node.getChild(i) ?: continue
                collectTexts(ch, out, depth + 1)
            }
        }

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 14 || visited.size > 260) return
            val hash = System.identityHashCode(node)
            if (!visited.add(hash)) return

            val cls = node.className?.toString().orEmpty()

            if (foundScroll == null && node.isScrollable) {
                foundScroll = node
            }

            if (node.isEditable && foundInput == null) {
                foundInput = node
                inputHint = node.hintText?.toString()?.trim()
                    ?: node.text?.toString()?.trim()
                    ?: "Сообщение..."
            }

            // Detect tab containers (TabWidget, TabLayout, NavigationBar, horizontal nav bar)
            val isTabContainer = cls.contains("TabWidget") ||
                cls.contains("TabLayout") ||
                cls.contains("BottomNavigation") ||
                cls.contains("NavigationBar")
            if (isTabContainer && tabPairs.isEmpty()) {
                for (i in 0 until node.childCount) {
                    val tabChild = node.getChild(i) ?: continue
                    val labels = mutableListOf<String>()
                    collectTexts(tabChild, labels)
                    val label = labels.firstOrNull()?.take(18) ?: "Вкл.${tabPairs.size + 1}"
                    if (tabChild.isClickable || tabChild.childCount > 0) {
                        val clickTarget = if (tabChild.isClickable) tabChild else findClickableDescendant(tabChild) ?: tabChild
                        tabPairs.add(
                            AdaptedTabNode(
                                index = tabPairs.size,
                                label = label,
                                viewId = clickTarget.viewIdResourceName,
                                isSelected = clickTarget.isSelected || tabChild.isSelected
                            ) to clickTarget
                        )
                    }
                    if (tabPairs.size >= 6) break
                }
            }

            // Detect send button near editable text
            val descOrText = (node.contentDescription?.toString().orEmpty() + " " + node.text?.toString().orEmpty()).lowercase()
            if (foundSend == null && node.isClickable && !node.isEditable && (
                    descOrText.contains("отправ") ||
                        descOrText.contains("send") ||
                        node.viewIdResourceName?.lowercase()?.contains("send") == true
                    )
            ) {
                foundSend = node
            }

            // Detect list/chat/feed rows or clickable cards
            val isListContainer = cls.contains("RecyclerView") || cls.contains("ListView") || cls.contains("GridView")
            if (isListContainer && itemPairs.size < 24) {
                for (i in 0 until node.childCount) {
                    val row = node.getChild(i) ?: continue
                    val texts = mutableListOf<String>()
                    collectTexts(row, texts)
                    if (texts.isNotEmpty()) {
                        val title = texts[0].take(64)
                        val subtitle = texts.drop(1).joinToString(" • ").take(90)
                        val clickTarget = if (row.isClickable) row else findClickableDescendant(row) ?: row
                        itemPairs.add(
                            AdaptedItemNode(
                                index = itemPairs.size,
                                title = title,
                                subtitle = subtitle,
                                badge = if (mode == AppAdaptationMode.MESSENGER && texts.any { it.all(Char::isDigit) && it.length <= 4 }) {
                                    texts.first { it.all(Char::isDigit) && it.length <= 4 }
                                } else "",
                                isClickable = clickTarget.isClickable,
                                isEditable = clickTarget.isEditable
                            ) to clickTarget
                        )
                    }
                    if (itemPairs.size >= 24) break
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }

        walk(root, 0)

        // Fallback: if no RecyclerView rows were extracted, collect top clickable buttons/texts
        if (itemPairs.isEmpty()) {
            collectFallbackClickables(root, itemPairs, tabPairs.map { it.second }.toSet())
        }

        // If app didn't expose TabLayout explicitly, look for top/bottom action tabs or provide messenger defaults
        if (tabPairs.isEmpty()) {
            collectHeuristicTabs(root, tabPairs)
        }

        lastTabNodes = tabPairs.map { it.second }
        lastItemNodes = itemPairs.map { it.second }
        lastInputNode = foundInput
        lastSendNode = foundSend
        lastScrollNode = foundScroll

        val coercedFocus = if (itemPairs.isEmpty()) 0 else previousFocusIndex.coerceIn(0, itemPairs.lastIndex)

        return AdaptedAppSnapshot(
            packageName = packageName,
            appLabel = appLabel,
            mode = mode,
            tabs = tabPairs.map { it.first },
            items = itemPairs.map { it.first },
            focusedItemIndex = coercedFocus,
            hasInputField = foundInput != null,
            inputHint = inputHint,
            hasSendButton = foundSend != null,
            overlayCollapsed = collapsed
        )
    }

    private fun findClickableDescendant(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 3) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isClickable) return child
            val nested = findClickableDescendant(child, depth + 1)
            if (nested != null) return nested
        }
        return null
    }

    private fun collectFallbackClickables(
        node: AccessibilityNodeInfo,
        out: MutableList<Pair<AdaptedItemNode, AccessibilityNodeInfo>>,
        excludedTabs: Set<AccessibilityNodeInfo>,
        depth: Int = 0
    ) {
        if (depth > 10 || out.size >= 20) return
        if (!excludedTabs.contains(node) && node.isClickable && !node.isEditable) {
            val label = (node.text?.toString() ?: node.contentDescription?.toString()).orEmpty().trim()
            if (label.isNotBlank() && label.length >= 2) {
                out.add(
                    AdaptedItemNode(
                        index = out.size,
                        title = label.take(64),
                        subtitle = "",
                        isClickable = true
                    ) to node
                )
            }
        }
        for (i in 0 until node.childCount) {
            val ch = node.getChild(i) ?: continue
            collectFallbackClickables(ch, out, excludedTabs, depth + 1)
        }
    }

    private fun collectHeuristicTabs(
        root: AccessibilityNodeInfo,
        out: MutableList<Pair<AdaptedTabNode, AccessibilityNodeInfo>>,
        depth: Int = 0
    ) {
        if (depth > 8 || out.size >= 6) return
        if (root.childCount in 2..6) {
            val clickableChildren = (0 until root.childCount)
                .mapNotNull { root.getChild(it) }
                .filter { it.isClickable }
            if (clickableChildren.size in 2..6) {
                val labeled = clickableChildren.mapNotNull { ch ->
                    val txt = (ch.text?.toString() ?: ch.contentDescription?.toString()).orEmpty().trim()
                    if (txt.isNotBlank() && txt.length <= 20) ch to txt else null
                }
                if (labeled.size >= 2) {
                    labeled.take(6).forEach { (node, label) ->
                        out.add(
                            AdaptedTabNode(
                                index = out.size,
                                label = label,
                                viewId = node.viewIdResourceName,
                                isSelected = node.isSelected
                            ) to node
                        )
                    }
                    return
                }
            }
        }
        for (i in 0 until root.childCount) {
            val ch = root.getChild(i) ?: continue
            collectHeuristicTabs(ch, out, depth + 1)
            if (out.isNotEmpty()) return
        }
    }

    fun clickTab(index: Int): Boolean {
        val target = lastTabNodes.getOrNull(index) ?: return false
        return runCatching { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
    }

    fun clickItem(index: Int): Boolean {
        val target = lastItemNodes.getOrNull(index) ?: return false
        return runCatching { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
    }

    fun scroll(forward: Boolean): Boolean {
        val target = lastScrollNode ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return runCatching { target.performAction(action) }.getOrDefault(false)
    }

    fun sendInputText(text: String): Boolean {
        val input = lastInputNode ?: return false
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, clean)
        }
        val setOk = runCatching { input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)
        val sendOk = lastSendNode?.let { send ->
            runCatching { send.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        } ?: false
        return setOk || sendOk
    }

    companion object {
        private val boundsRegex = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""")

        /**
         * Parses `uiautomator dump` XML when running via Shizuku/Root without AccessibilityService.
         * Returns the snapshot + tap centers `(x, y)` for tabs & items so shell `input tap x y` works.
         */
        fun parseRootDumpXml(
            xml: String,
            packageName: String,
            appLabel: String,
            mode: AppAdaptationMode
        ): Pair<AdaptedAppSnapshot, List<Pair<Int, Int>>>? {
            if (xml.isBlank()) return null
            val tabs = mutableListOf<AdaptedTabNode>()
            val items = mutableListOf<AdaptedItemNode>()
            val itemCenters = mutableListOf<Pair<Int, Int>>()
            var hasInput = false

            runCatching {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(StringReader(xml))
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "node") {
                        val cls = parser.getAttributeValue(null, "class").orEmpty()
                        val text = parser.getAttributeValue(null, "text").orEmpty().trim()
                        val desc = parser.getAttributeValue(null, "content-desc").orEmpty().trim()
                        val clickable = parser.getAttributeValue(null, "clickable") == "true"
                        val selected = parser.getAttributeValue(null, "selected") == "true"
                        val boundsStr = parser.getAttributeValue(null, "bounds").orEmpty()
                        val label = text.ifBlank { desc }

                        if (cls.contains("EditText")) {
                            hasInput = true
                        }
                        if (clickable && label.isNotBlank()) {
                            val match = boundsRegex.find(boundsStr)
                            val cx = match?.let {
                                (it.groupValues[1].toInt() + it.groupValues[3].toInt()) / 2
                            } ?: 0
                            val cy = match?.let {
                                (it.groupValues[2].toInt() + it.groupValues[4].toInt()) / 2
                            } ?: 0

                            if (label.length <= 16 && (cls.contains("Tab") || cy > 1800) && tabs.size < 6) {
                                tabs.add(AdaptedTabNode(tabs.size, label, isSelected = selected))
                            } else if (items.size < 24) {
                                items.add(
                                    AdaptedItemNode(
                                        index = items.size,
                                        title = label.take(64),
                                        subtitle = if (cls.contains("Switch")) "Переключатель" else "",
                                        isClickable = true
                                    )
                                )
                                itemCenters.add(cx to cy)
                            }
                        }
                    }
                    event = parser.next()
                }
            }

            if (items.isEmpty() && tabs.isEmpty()) return null
            val snapshot = AdaptedAppSnapshot(
                packageName = packageName,
                appLabel = appLabel,
                mode = mode,
                tabs = tabs,
                items = items,
                focusedItemIndex = 0,
                hasInputField = hasInput,
                inputHint = if (mode == AppAdaptationMode.MESSENGER) "Сообщение..." else "Поиск / Ввод...",
                hasSendButton = hasInput,
                overlayCollapsed = false
            )
            return snapshot to itemCenters
        }
    }
}
