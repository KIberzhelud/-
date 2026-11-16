// core/model/Models.kt
package com.yourname.wplauncher.core.model

import android.content.ComponentName
import android.graphics.Color
import android.os.UserHandle

data class AppInfo(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val user: UserHandle
)

enum class TileSize(val spanWidth: Int) {
    SMALL_1X1(1),
    WIDE_2X1(2)
}

data class MetroTile(
    val id: String,
    val title: String,
    val app: AppInfo? = null,
    val accentColor: Int = Color.parseColor("#0078D7"), // Windows Phone Cobalt
    val size: TileSize = TileSize.SMALL_1X1,
    val badgeCount: Int = 0 // Задел под будущие Live Tiles (счётчик уведомлений)
)