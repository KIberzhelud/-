package com.example.data

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.data.AdaptationCommand
import com.example.model.AdaptedAppSnapshot
import com.example.model.AppAdaptationMode
import com.example.model.HubNotificationItem
import com.example.model.HubSubTab
import com.example.ui.theme.VistaThemeTokens

/**
 * Renders the Symbian / Windows Vista Aero overlay redraw over adapted apps (Standard & Messenger modes),
 * adapting colors to the active VistaThemeTokens (Blue, Gray, or Wallpaper Aero Glass),
 * with F1..F6 tab bar, arrow-key focus cursor, and Messenger chat/quick-reply controls.
 */
class AdaptedAppOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false

    private var headerTitleView: TextView? = null
    private var modeBadgeView: TextView? = null
    private var collapseBtnView: TextView? = null
    private var tabsContainer: LinearLayout? = null
    private var itemsContainer: LinearLayout? = null
    private var bodyScrollView: ScrollView? = null
    private var inputRowContainer: LinearLayout? = null
    private var replyEditText: EditText? = null
    private var sendBtnView: TextView? = null
    private var footerControlsRow: LinearLayout? = null

    fun showOrUpdate(
        snapshot: AdaptedAppSnapshot,
        notifications: List<HubNotificationItem>,
        tokens: VistaThemeTokens
    ) {
        val wm = windowManager
        if (!ShizukuBridge.hasOverlayPermission(context) || wm == null) {
            hide()
            return
        }

        if (!isAttached) {
            createAndAttachOverlay(snapshot, tokens)
        }

        val root = rootView ?: return
        val params = layoutParams ?: return

        val bgArgb = if (tokens.isGlassMode) {
            tokens.surface.copy(alpha = 0.84f).toArgb()
        } else {
            tokens.background.copy(alpha = 0.96f).toArgb()
        }
        val borderArgb = tokens.chromeBorderOuter.toArgb()
        val accentArgb = tokens.accent.toArgb()
        val focusArgb = tokens.focusBorder.toArgb()
        val headerTop = tokens.glassTop.toArgb()
        val headerBot = tokens.glassBottom.toArgb()

        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(bgArgb, tokens.surfaceElevated.copy(alpha = 0.94f).toArgb())
        ).apply {
            cornerRadius = dp(6).toFloat()
            setStroke(dp(1), borderArgb)
        }

        if (snapshot.overlayCollapsed) {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.gravity = Gravity.TOP or Gravity.END
            params.x = dp(8)
            params.y = dp(42)

            headerTitleView?.text = "${snapshot.appLabel} • F1–F6"
            modeBadgeView?.visibility = View.GONE
            collapseBtnView?.text = "Развернуть"
            tabsContainer?.visibility = View.GONE
            bodyScrollView?.visibility = View.GONE
            inputRowContainer?.visibility = View.GONE
            footerControlsRow?.visibility = View.GONE
        } else {
            val metrics = context.resources.displayMetrics
            val screenW = metrics.widthPixels.coerceAtLeast(360)
            val screenH = metrics.heightPixels.coerceAtLeast(640)
            // Dock overlay strictly inside the launcher's tile frame so Player, Search, A-Z rail & Taskbar remain accessible
            val leftInset = dp(6)
            val rightRailReserve = dp(60)
            val topHeaderReserve = dp(104)
            val bottomTaskbarReserve = dp(62)

            params.width = (screenW - leftInset - rightRailReserve).coerceAtLeast(dp(240))
            params.height = (screenH - topHeaderReserve - bottomTaskbarReserve).coerceAtLeast(dp(260))
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.gravity = Gravity.TOP or Gravity.START
            params.x = leftInset
            params.y = topHeaderReserve

            val isMessenger = snapshot.mode == AppAdaptationMode.MESSENGER
            headerTitleView?.text = if (isMessenger) {
                "KEYDECK MESSENGER • ${snapshot.appLabel}"
            } else {
                "KEYDECK АДАПТЕР • ${snapshot.appLabel}"
            }
            modeBadgeView?.visibility = View.VISIBLE
            modeBadgeView?.text = if (isMessenger) "МЕССЕНДЖЕР" else "СТАНДАРТ"
            modeBadgeView?.background = pillBg(accentArgb, focusArgb, alpha = 0.35f)
            collapseBtnView?.text = "Оригинал"
            tabsContainer?.visibility = View.VISIBLE
            bodyScrollView?.visibility = View.VISIBLE
            footerControlsRow?.visibility = View.VISIBLE

            // Header bar styling
            (headerTitleView?.parent as? View)?.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(headerTop, headerBot)
            ).apply {
                setStroke(dp(1), borderArgb)
            }

            // Populate F1..F6 Tabs
            populateTabs(snapshot, tokens)

            // Populate Items (in Messenger mode, merge live Hub chats for this package + extracted screen chats)
            populateItems(snapshot, notifications, tokens)

            // Messenger Quick Input bar if editable node exists
            if (isMessenger || snapshot.hasInputField) {
                inputRowContainer?.visibility = View.VISIBLE
                replyEditText?.hint = snapshot.inputHint.ifBlank { "Написать сообщение..." }
                sendBtnView?.background = pillBg(accentArgb, focusArgb, alpha = 0.85f)
            } else {
                inputRowContainer?.visibility = View.GONE
            }
        }

        runCatching { wm.updateViewLayout(root, params) }
    }

    private fun populateTabs(snapshot: AdaptedAppSnapshot, tokens: VistaThemeTokens) {
        val container = tabsContainer ?: return
        container.removeAllViews()

        val displayTabs = if (snapshot.tabs.isNotEmpty()) {
            snapshot.tabs
        } else if (snapshot.mode == AppAdaptationMode.MESSENGER) {
            listOf("Чаты", "Личные", "Каналы", "Звонки", "Поиск").mapIndexed { idx, lbl ->
                com.example.model.AdaptedTabNode(index = idx, label = lbl, isSelected = idx == 0)
            }
        } else {
            listOf("Экран", "Вверх", "Вниз", "Назад").mapIndexed { idx, lbl ->
                com.example.model.AdaptedTabNode(index = idx, label = lbl, isSelected = idx == 0)
            }
        }

        displayTabs.take(6).forEachIndexed { idx, tab ->
            val btn = TextView(context).apply {
                text = "F${idx + 1} ${tab.label}"
                textSize = 11.5f
                typeface = if (tab.isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (tab.isSelected) Color.White.toArgb() else 0xFFEAF2FA.toInt())
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = pillBg(
                    if (tab.isSelected) tokens.primaryButtonLowerMid.toArgb() else tokens.surfaceElevated.toArgb(),
                    if (tab.isSelected) tokens.focusBorder.toArgb() else tokens.chromeBorderOuter.toArgb(),
                    alpha = 0.92f
                )
                setOnClickListener {
                    if (snapshot.tabs.isNotEmpty()) {
                        AppAdaptationRuntime.sendCommand(AdaptationCommand.ClickTab(idx))
                    } else if (snapshot.mode != AppAdaptationMode.MESSENGER) {
                        when (idx) {
                            1 -> AppAdaptationRuntime.sendCommand(AdaptationCommand.ScrollContent(false))
                            2 -> AppAdaptationRuntime.sendCommand(AdaptationCommand.ScrollContent(true))
                            3 -> AppAdaptationRuntime.sendCommand(AdaptationCommand.NavigateBack)
                        }
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
            container.addView(btn, lp)
        }
    }

    private fun populateItems(
        snapshot: AdaptedAppSnapshot,
        notifications: List<HubNotificationItem>,
        tokens: VistaThemeTokens
    ) {
        val container = itemsContainer ?: return
        container.removeAllViews()

        val isMessenger = snapshot.mode == AppAdaptationMode.MESSENGER

        // Messenger mode shows live incoming Hub stream for this messenger at top when available
        if (isMessenger) {
            val pkgMsgs = notifications.filter {
                it.subTab == HubSubTab.CHATS && it.packageName == snapshot.packageName
            }.take(4)
            if (pkgMsgs.isNotEmpty()) {
                val hdr = TextView(context).apply {
                    text = "ВХОДЯЩИЕ (${pkgMsgs.size})"
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.accent.toArgb())
                    setPadding(dp(4), dp(2), dp(4), dp(4))
                }
                container.addView(hdr)

                pkgMsgs.forEach { msg ->
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(10), dp(7), dp(10), dp(7))
                        background = pillBg(
                            tokens.surfaceElevated.toArgb(),
                            tokens.accent.copy(alpha = 0.65f).toArgb(),
                            alpha = 0.88f
                        )
                    }
                    card.addView(TextView(context).apply {
                        text = "${msg.title} • ${msg.timeFormatted}"
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.focusBorder.toArgb())
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    card.addView(TextView(context).apply {
                        text = msg.bigText.ifBlank { msg.text }
                        textSize = 11.5f
                        setTextColor(0xFFD6E2F0.toInt())
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(5) }
                    container.addView(card, lp)
                }
            }
        }

        val sectionHdr = TextView(context).apply {
            text = if (isMessenger) {
                "ДИАЛОГИ И ЧАТЫ (↑↓ Enter — открыть)"
            } else {
                "ЭЛЕМЕНТЫ ЭКРАНА (↑↓ Enter — нажать)"
            }
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.focusBorder.toArgb())
            setPadding(dp(4), dp(4), dp(4), dp(6))
        }
        container.addView(sectionHdr)

        if (snapshot.items.isEmpty()) {
            container.addView(TextView(context).apply {
                text = "Ожидание элементов интерфейса… Нажмите «Обновить» или «Оригинал»."
                textSize = 12f
                setTextColor(0xFFA2B5CA.toInt())
                setPadding(dp(8), dp(12), dp(8), dp(12))
            })
            return
        }

        snapshot.items.forEachIndexed { idx, item ->
            val isFocused = idx == snapshot.focusedItemIndex
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = if (isFocused) {
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            tokens.primaryButtonUpperMid.copy(alpha = 0.88f).toArgb(),
                            tokens.primaryButtonLowerMid.copy(alpha = 0.88f).toArgb()
                        )
                    ).apply {
                        cornerRadius = dp(4).toFloat()
                        setStroke(dp(2), tokens.focusBorder.toArgb())
                    }
                } else {
                    pillBg(
                        if (tokens.isGlassMode) tokens.surface.copy(alpha = 0.65f).toArgb()
                        else tokens.surfaceElevated.toArgb(),
                        tokens.divider.toArgb(),
                        alpha = 0.90f
                    )
                }
                setOnClickListener {
                    AppAdaptationRuntime.sendCommand(AdaptationCommand.MoveFocus(idx - snapshot.focusedItemIndex))
                    AppAdaptationRuntime.sendCommand(AdaptationCommand.ActivateFocused)
                }
            }

            if (isMessenger) {
                val avatarText = item.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
                val avatar = TextView(context).apply {
                    text = avatarText
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.White.toArgb())
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(tokens.accent.copy(alpha = 0.5f).toArgb())
                        setStroke(dp(1), tokens.focusBorder.toArgb())
                    }
                }
                val avatarLp = LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(9) }
                row.addView(avatar, avatarLp)
            }

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            textCol.addView(TextView(context).apply {
                text = item.title
                textSize = 13f
                typeface = if (isFocused || isMessenger) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isFocused) Color.White.toArgb() else 0xFFF2F6FB.toInt())
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            if (item.subtitle.isNotBlank()) {
                textCol.addView(TextView(context).apply {
                    text = item.subtitle
                    textSize = 11.5f
                    setTextColor(if (isFocused) 0xFFE6F4FF.toInt() else 0xFFA2B5CA.toInt())
                    maxLines = if (isMessenger) 2 else 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            val textLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(textCol, textLp)

            if (item.badge.isNotBlank()) {
                val badgeView = TextView(context).apply {
                    text = item.badge
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.White.toArgb())
                    setPadding(dp(7), dp(2), dp(7), dp(2))
                    background = pillBg(tokens.accent.toArgb(), tokens.focusBorder.toArgb(), alpha = 0.9f)
                }
                row.addView(badgeView)
            }

            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(5)
            }
            container.addView(row, rowLp)
        }
    }

    private fun createAndAttachOverlay(snapshot: AdaptedAppSnapshot, tokens: VistaThemeTokens) {
        val wm = windowManager ?: return
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(6))
        }

        // Top Window Bar (Long-press disables adaptation for current app)
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnLongClickListener {
                AppAdaptationRuntime.activeSnapshot.value?.packageName?.let { pkg ->
                    AppAdaptationRuntime.sendCommand(AdaptationCommand.DisablePackage(pkg))
                }
                true
            }
        }
        val titleTv = TextView(context).apply {
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.focusBorder.toArgb())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val badgeTv = TextView(context).apply {
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.White.toArgb())
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val disableTv = TextView(context).apply {
            text = "Откл."
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFF8F8F.toInt())
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = pillBg(tokens.surfaceElevated.toArgb(), 0xFFB84545.toInt(), alpha = 0.9f)
            setOnClickListener {
                AppAdaptationRuntime.activeSnapshot.value?.packageName?.let { pkg ->
                    AppAdaptationRuntime.sendCommand(AdaptationCommand.DisablePackage(pkg))
                }
            }
        }
        val collapseTv = TextView(context).apply {
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFF2F6FB.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = pillBg(tokens.surfaceElevated.toArgb(), tokens.chromeBorderOuter.toArgb(), alpha = 0.9f)
            setOnClickListener { AppAdaptationRuntime.sendCommand(AdaptationCommand.ToggleCollapse) }
        }
        headerBar.addView(titleTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerBar.addView(badgeTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(4) })
        headerBar.addView(disableTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(4) })
        headerBar.addView(collapseTv)
        root.addView(headerBar)

        // F1..F6 Horizontal Tabs Strip
        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val tabLinear = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        tabScroll.addView(tabLinear)
        root.addView(tabScroll)

        // Body ScrollView
        val scroll = ScrollView(context).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val itemsLinear = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(itemsLinear)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Quick-reply row for Messenger
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(4))
            visibility = View.GONE
        }
        val edit = EditText(context).apply {
            setTextColor(0xFFF2F6FB.toInt())
            setHintTextColor(0xFF7B93AC.toInt())
            textSize = 13f
            imeOptions = EditorInfo.IME_ACTION_SEND
            maxLines = 2
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = pillBg(tokens.insetBg.toArgb(), tokens.chromeBorderOuter.toArgb(), alpha = 0.96f)
        }
        val sendBtn = TextView(context).apply {
            text = "Отправить"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.White.toArgb())
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setOnClickListener {
                val txt = edit.text?.toString().orEmpty()
                if (txt.isNotBlank()) {
                    AppAdaptationRuntime.sendCommand(AdaptationCommand.SendText(txt))
                    edit.setText("")
                }
            }
        }
        inputRow.addView(edit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })
        inputRow.addView(sendBtn)
        root.addView(inputRow)

        // Bottom Navigation Controls Bar (← Назад | ▲ Вверх | ▼ Вниз | Обновить)
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        listOf(
            "◀ Назад" to AdaptationCommand.NavigateBack,
            "▲ Вверх" to AdaptationCommand.ScrollContent(false),
            "▼ Вниз" to AdaptationCommand.ScrollContent(true),
            "↻ Обновить" to AdaptationCommand.RefreshTree
        ).forEach { (label, cmd) ->
            val btn = TextView(context).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(0xFFF2F6FB.toInt())
                setPadding(dp(6), dp(7), dp(6), dp(7))
                background = pillBg(tokens.surfaceElevated.toArgb(), tokens.chromeBorderOuter.toArgb(), alpha = 0.9f)
                setOnClickListener { AppAdaptationRuntime.sendCommand(cmd) }
            }
            footer.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }
        root.addView(footer)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val attached = runCatching {
            wm.addView(root, params)
            true
        }.getOrDefault(false)

        if (attached) {
            rootView = root
            layoutParams = params
            headerTitleView = titleTv
            modeBadgeView = badgeTv
            collapseBtnView = collapseTv
            tabsContainer = tabLinear
            itemsContainer = itemsLinear
            bodyScrollView = scroll
            inputRowContainer = inputRow
            replyEditText = edit
            sendBtnView = sendBtn
            footerControlsRow = footer
            isAttached = true
        }
    }

    fun hide() {
        val wm = windowManager ?: return
        val root = rootView ?: return
        if (isAttached) {
            runCatching { wm.removeViewImmediate(root) }
        }
        isAttached = false
        rootView = null
        layoutParams = null
        headerTitleView = null
        modeBadgeView = null
        collapseBtnView = null
        tabsContainer = null
        itemsContainer = null
        bodyScrollView = null
        inputRowContainer = null
        replyEditText = null
        sendBtnView = null
        footerControlsRow = null
    }

    private fun pillBg(fillArgb: Int, strokeArgb: Int, alpha: Float): GradientDrawable {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        val withAlpha = (fillArgb and 0x00FFFFFF) or (a shl 24)
        return GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(withAlpha)
            setStroke(dp(1), strokeArgb)
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
