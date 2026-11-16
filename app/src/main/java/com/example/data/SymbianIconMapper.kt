package com.example.data

import com.example.R

data class SymbianIconOption(
    val key: String,
    val label: String,
    val resId: Int
)

/**
 * Maps Symbian S60v3 / Nokia E61i applications and modern Android counterparts
 * ("бывшие на симбиан и близкие по духу") to custom Symbian E61i vector icons,
 * and provides the full selectable catalog of Symbian icons for user customization.
 */
object SymbianIconMapper {

    const val OVERRIDE_ORIGINAL = "ORIGINAL"
    const val PREFIX_SYMBIAN = "SYMBIAN:"
    const val PREFIX_CUSTOM = "CUSTOM:"

    val symbianIconCatalog: List<SymbianIconOption> = listOf(
        SymbianIconOption("phone", "Телефон", R.drawable.ic_symbian_phone),
        SymbianIconOption("contacts", "Контакты", R.drawable.ic_symbian_contacts),
        SymbianIconOption("messaging", "Чаты / SMS", R.drawable.ic_symbian_messaging),
        SymbianIconOption("web_news", "Браузер / RSS", R.drawable.ic_symbian_web_news),
        SymbianIconOption("files", "Файлы / X-plore", R.drawable.ic_symbian_files),
        SymbianIconOption("notes", "Заметки / Офис", R.drawable.ic_symbian_notes),
        SymbianIconOption("docx", "Документ / Словарь", R.drawable.ic_symbian_docx),
        SymbianIconOption("editor", "Книги / Редактор", R.drawable.ic_symbian_editor),
        SymbianIconOption("music", "Музыка / Плеер", R.drawable.ic_symbian_music),
        SymbianIconOption("video", "Видео / RealPlayer", R.drawable.ic_symbian_video),
        SymbianIconOption("camera", "Камера", R.drawable.ic_symbian_camera),
        SymbianIconOption("gallery", "Галерея / Фото", R.drawable.ic_symbian_gallery),
        SymbianIconOption("calendar", "Календарь / Часы", R.drawable.ic_symbian_calendar),
        SymbianIconOption("calc", "Калькулятор", R.drawable.ic_symbian_calc),
        SymbianIconOption("maps", "Карты / 2ГИС", R.drawable.ic_symbian_maps),
        SymbianIconOption("games", "Игры / N-Gage", R.drawable.ic_symbian_games),
        SymbianIconOption("settings", "Настройки / Termux", R.drawable.ic_symbian_settings),
        SymbianIconOption("tools", "Инструменты", R.drawable.ic_symbian_tools),
        SymbianIconOption("shizuku", "Shizuku / Сервис", R.drawable.ic_symbian_shizuku),
        SymbianIconOption("log", "Журнал", R.drawable.ic_symbian_log),
        SymbianIconOption("keyboard", "Клавиатура", R.drawable.ic_symbian_keyboard),
        SymbianIconOption("link", "Веб-ссылка", R.drawable.ic_symbian_link),
        SymbianIconOption("search", "Поиск", R.drawable.ic_symbian_search),
        SymbianIconOption("lock", "Блокировка", R.drawable.ic_symbian_lock),
        SymbianIconOption("menu", "Меню S60", R.drawable.ic_symbian_menu),
        SymbianIconOption("apk", "Пакет APK", R.drawable.ic_symbian_apk)
    )

    val catalog: List<SymbianIconOption>
        get() = symbianIconCatalog

    private val catalogByKey: Map<String, SymbianIconOption> =
        symbianIconCatalog.associateBy { it.key }

    fun resolveByKey(key: String?): Int? = key?.let { catalogByKey[it]?.resId }

    fun resolveSymbianIconRes(packageName: String, label: String): Int? {
        val pkg = packageName.lowercase()
        val lbl = label.lowercase()

        fun hasAny(vararg tokens: String): Boolean =
            tokens.any { pkg.contains(it) || lbl.contains(it) }

        // Symbian File Managers (X-plore, Total Commander, ZArchiver, Solid, Files)
        if (hasAny(
                "xplore", "lonelycatgames", "totalcmd", "total commander", "solidexplorer",
                "zarchiver", "mixplorer", "cxinventor", "documentsui", "files", "проводник", "файлы"
            )
        ) {
            return R.drawable.ic_symbian_files
        }

        // Symbian Office / Books / Notes (Quickoffice, SmartOffice, ReadEra, FBReader, CoolReader, AlReader, Keep, Evernote, Obsidian, Notion)
        if (hasAny(
                "quickoffice", "smartoffice", "wps", "office", "docs", "fbreader", "alreader",
                "coolreader", "readera", "moonreader", "librera", "evernote", "obsidian",
                "notion", "keep", "simplenote", "joplin", "writer", "блокнот", "заметк", "читалк"
            )
        ) {
            return R.drawable.ic_symbian_notes
        }

        // Symbian Phone / Dialer
        if (hasAny("dialer", "telecom", "incallui", "contacts.dialer", "truecaller", "звон", "телефон")) {
            return R.drawable.ic_symbian_phone
        }

        // Symbian Contacts
        if (hasAny("contacts", "контакт")) {
            return R.drawable.ic_symbian_contacts
        }

        // Symbian Messaging / IM / Mail (Telegram, Viber, WhatsApp, ICQ, Mail, Gmail, SMS, ProfiMail)
        if (hasAny(
                "telegram", "exteragram", "nekogram", "whatsapp", "viber", "signal", "discord",
                "slack", "vk.im", "messenger", "skype", "icq", "profimail", "k9mail", "fairemail",
                "thunderbird", "gmail", "outlook", "yandex.mail", "mail.ru", "messaging", "mms", "сообщ"
            )
        ) {
            return R.drawable.ic_symbian_messaging
        }

        // Symbian Browsers & News (Opera Mini/Mobile, UC Browser, Firefox, Chrome, Vivaldi, 4PDA, Habr, RSS)
        if (hasAny(
                "opera", "ucbrowser", "firefox", "fennec", "chrome", "vivaldi", "yabrowser",
                "duckduckgo", "adblock", "edge", "browser", "браузер", "habr", "4pda", "feedly",
                "inoreader", "rss", "news", "новост", "reddit"
            )
        ) {
            return R.drawable.ic_symbian_web_news
        }

        // Symbian Music / Jukebox (Poweramp, AIMP, VLC, Foobar2000, LCG Jukebox, Yandex Music, Spotify, Zvuk)
        if (hasAny(
                "jukebox", "poweramp", "aimp", "foobar", "neutron", "vlc", "music", "spotify",
                "yandex.music", "звук", "музык", "плеер", "deezer", "tidal", "soundcloud", "radio", "радио"
            )
        ) {
            return R.drawable.ic_symbian_music
        }

        // Symbian RealPlayer / Video
        if (hasAny("youtube", "smarttube", "mxtech", "mpv", "kodi", "kino", "video", "видео", "реалплеер", "realplayer")) {
            return R.drawable.ic_symbian_video
        }

        // Symbian Camera
        if (hasAny("camera", "opencamera", "gcam", "камера")) {
            return R.drawable.ic_symbian_camera
        }

        // Symbian Gallery / Photo Viewers (Resco Photo Viewer, QuickPic, Google Photos, Gallery)
        if (hasAny("gallery", "photos", "quickpic", "simplemobiletools.gallery", "fossify.gallery", "fstop", "галерея", "фото")) {
            return R.drawable.ic_symbian_gallery
        }

        // Symbian Calendar / Clock
        if (hasAny("calendar", "deskclock", "clock", "alarm", "календар", "часы", "будильник")) {
            return R.drawable.ic_symbian_calendar
        }

        // Symbian Calculator
        if (hasAny("calculator", "calc", "калькулятор")) {
            return R.drawable.ic_symbian_calc
        }

        // Symbian Nokia Maps / Ovi Maps / Navigation (2GIS, OsmAnd, Organic Maps, Yandex Maps, Google Maps)
        if (hasAny("maps", "2gis", "dublgis", "osmand", "organicmaps", "navigator", "waze", "карт", "навигатор")) {
            return R.drawable.ic_symbian_maps
        }

        // Symbian N-Gage / Emulators / Games
        if (hasAny("ngage", "j2me", "ppsspp", "aethersx2", "retroarch", "duckstation", "dolphin", "scummvm", "dosbox", "drastic", "game", "angrybirds")) {
            return R.drawable.ic_symbian_games
        }

        // Shizuku & Root/Shell Tools
        if (hasAny("shizuku")) {
            return R.drawable.ic_symbian_shizuku
        }

        // Symbian Terminal / SSH / System Tools / Authenticator / Settings
        if (hasAny("termux", "connectbot", "juicessh", "terminal", "authenticator", "settings", "настройк")) {
            return R.drawable.ic_symbian_settings
        }

        // Symbian Translators / Dictionaries (ABBYY Lingvo, GoldenDict, DeepL, Google Translate)
        if (hasAny("translate", "lingvo", "goldendict", "deepl", "словар", "перевод")) {
            return R.drawable.ic_symbian_docx
        }

        return null
    }
}
