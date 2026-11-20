package com.example.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.model.AppLanguage
import com.example.model.CallsSubTab
import com.example.model.HubSubTab
import com.example.model.MainTab
import com.example.model.MusicDrawerTab
import com.example.model.PlayerCoverStyle

val LocalAppLang = staticCompositionLocalOf { AppLanguage.RU }

object L10n {
    fun tab(lang: AppLanguage, t: MainTab): String = when (lang) {
        AppLanguage.RU -> t.title
        AppLanguage.EN -> when (t) {
            MainTab.HOME -> "Home"
            MainTab.CALLS -> "Calls"
            MainTab.HUB -> "Hub"
        }
    }

    fun hubSub(lang: AppLanguage, s: HubSubTab): String = when (lang) {
        AppLanguage.RU -> s.title
        AppLanguage.EN -> when (s) {
            HubSubTab.CHATS -> "Chats"
            HubSubTab.NEWS -> "News"
            HubSubTab.OTHER -> "Other"
            HubSubTab.SETTINGS -> "Settings"
        }
    }

    fun callsSub(lang: AppLanguage, s: CallsSubTab): String = when (lang) {
        AppLanguage.RU -> s.title
        AppLanguage.EN -> when (s) {
            CallsSubTab.LOG -> "Recents"
            CallsSubTab.CONTACTS -> "Contacts"
        }
    }

    fun musicTab(lang: AppLanguage, t: MusicDrawerTab): String = when (lang) {
        AppLanguage.RU -> t.title
        AppLanguage.EN -> when (t) {
            MusicDrawerTab.STREAMING -> "Streaming & TG"
            MusicDrawerTab.LOCAL -> "Files"
        }
    }

    fun coverStyle(lang: AppLanguage, style: PlayerCoverStyle): String = when (lang) {
        AppLanguage.RU -> style.title
        AppLanguage.EN -> when (style) {
            PlayerCoverStyle.COVER -> "Cover"
            PlayerCoverStyle.DISC -> "Spinning Disc"
        }
    }

    fun camera(lang: AppLanguage) = if (lang == AppLanguage.RU) "Камера" else "Camera"
    fun books(lang: AppLanguage) = if (lang == AppLanguage.RU) "Книги" else "Books"
    fun searchPlaceholder(lang: AppLanguage) = if (lang == AppLanguage.RU) {
        "Поиск: приложения, чаты, *apk, *deb, музыка…"
    } else {
        "Search apps, chats, *apk, *deb, music…"
    }
    fun noActiveTrack(lang: AppLanguage) = if (lang == AppLanguage.RU) "Нет активного трека" else "No active track"
    fun chooseServiceOrTg(lang: AppLanguage) = if (lang == AppLanguage.RU) {
        "Откройте стриминг или вкладку «Файлы»"
    } else {
        "Open a streaming app or Files tab"
    }
    fun like(lang: AppLanguage) = if (lang == AppLanguage.RU) "Нравится" else "Like"
    fun dislike(lang: AppLanguage) = if (lang == AppLanguage.RU) "Не нравится" else "Dislike"
    fun myWave(lang: AppLanguage) = if (lang == AppLanguage.RU) "Моя Волна" else "My Vibe"
    fun recentsTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Активные приложения" else "Active Frames"
    fun closeAll(lang: AppLanguage) = if (lang == AppLanguage.RU) "Закрыть все" else "Close all"
    fun pinchBackHint(lang: AppLanguage) = if (lang == AppLanguage.RU) {
        "Щипок внутрь или Esc — назад • Ctrl+R — недавние"
    } else {
        "Pinch in or Esc to return • Ctrl+R recents"
    }
    fun noRecentApps(lang: AppLanguage) = if (lang == AppLanguage.RU) {
        "Нет недавних приложений в фоне"
    } else {
        "No recent background apps"
    }
    fun appearanceTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) {
        "Оформление, язык и масштаб"
    } else {
        "Appearance, Language & Scale"
    }
    fun languageLabel(lang: AppLanguage) = if (lang == AppLanguage.RU) "Язык интерфейса" else "Interface Language"
    fun scaleLabel(lang: AppLanguage) = if (lang == AppLanguage.RU) "Масштаб интерфейса" else "UI Scale"
    fun playerStyleLabel(lang: AppLanguage) = if (lang == AppLanguage.RU) "Стиль плеера" else "Player Style"
    fun appsFolder(lang: AppLanguage, count: Int) = if (lang == AppLanguage.RU) "Приложения ($count)" else "Apps ($count)"
    fun chatsFolder(lang: AppLanguage, count: Int) = if (lang == AppLanguage.RU) "Чаты и почта ($count)" else "Chats & Mail ($count)"
    fun photosFolder(lang: AppLanguage, count: Int) = if (lang == AppLanguage.RU) "Фото ($count)" else "Photos ($count)"
    fun filesFolder(lang: AppLanguage, count: Int) = if (lang == AppLanguage.RU) "Файлы ($count)" else "Files ($count)"
    fun linuxFolder(lang: AppLanguage, count: Int) = if (lang == AppLanguage.RU) "Пакеты Linux ($count)" else "Linux Packages ($count)"
    fun musicFolder(lang: AppLanguage, count: Int) = if (lang == AppLanguage.RU) "Музыка и стриминги ($count)" else "Music & Streaming ($count)"
    fun internetFolder(lang: AppLanguage) = if (lang == AppLanguage.RU) "Интернет" else "Web"
    fun workspaceLabel(lang: AppLanguage, x: Int, y: Int) = if (lang == AppLanguage.RU) {
        "Стол ($x, $y)"
    } else {
        "Space ($x, $y)"
    }
    fun createWorkspace(lang: AppLanguage) = if (lang == AppLanguage.RU) "Создать рабочий стол" else "Create Workspace"
    fun deleteWorkspace(lang: AppLanguage) = if (lang == AppLanguage.RU) "Удалить стол" else "Delete Space"
    fun pickWorkspaceApp(lang: AppLanguage) = if (lang == AppLanguage.RU) "Выбрать приложение" else "Choose App"
    fun dropToChatHint(lang: AppLanguage, targetName: String) = if (lang == AppLanguage.RU) {
        "Перетащите файл сюда или нажмите «В чат ⤵» → $targetName"
    } else {
        "Drop a file here or tap «To Chat ⤵» → $targetName"
    }
}
