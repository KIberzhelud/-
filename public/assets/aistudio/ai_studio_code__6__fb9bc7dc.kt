// ui/LauncherViewModel.kt
package com.yourname.wplauncher.ui

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.wplauncher.core.bridge.RecentTaskItem
import com.yourname.wplauncher.core.bridge.ShizukuBridge
import com.yourname.wplauncher.core.model.AppInfo
import com.yourname.wplauncher.core.model.MetroTile
import com.yourname.wplauncher.core.model.TileSize
import com.yourname.wplauncher.data.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    val repository = AppRepository(app)
    val systemBridge = ShizukuBridge(app)

    private val _pinnedPackages = MutableStateFlow<List<String>>(emptyList())
    private val _recentTasks = MutableStateFlow<List<RecentTaskItem>>(emptyList())
    private val _selectedSwitcherIndex = MutableStateFlow(0)

    // Закреплённые справа (по умолчанию первые 10, пока пользователь не настроил свои)
    val pinnedApps: StateFlow<List<AppInfo>> = combine(repository.apps, _pinnedPackages) { all, pinned ->
        if (pinned.isEmpty()) all.take(12)
        else pinned.mapNotNull { pkg -> all.find { it.packageName == pkg } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Плитки Windows Phone слева
    val tiles: StateFlow<List<MetroTile>> = repository.apps.map { apps ->
        val colors = listOf("#0078D7", "#1BA1E2", "#00ABA9", "#60A917", "#FA6800")
        apps.take(8).mapIndexed { idx, appInfo ->
            MetroTile(
                id = appInfo.packageName,
                title = appInfo.label,
                app = appInfo,
                accentColor = Color.parseColor(colors[idx % colors.size]),
                size = if (idx % 3 == 0) TileSize.WIDE_2X1 else TileSize.SMALL_1X1
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Кандидаты для переключения стрелочками (сначала недавние задачи из Shizuku, иначе закреплённые)
    val switcherTargets: StateFlow<List<RecentTaskItem>> = combine(_recentTasks, pinnedApps) { recent, pinned ->
        recent.ifEmpty {
            pinned.map { RecentTaskItem(it.packageName, it.componentName.flattenToShortString()) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentSwitchLabel: StateFlow<String> = combine(
        switcherTargets, _selectedSwitcherIndex, repository.apps
    ) { targets, idx, all ->
        val item = targets.getOrNull(idx) ?: return@combine "Нет активных"
        all.find { it.packageName == item.packageName }?.label ?: item.packageName
    }.stateIn(viewModelScope, SharingStarted.Lazily, "Нет активных")

    init {
        viewModelScope.launch { repository.loadApps() }
    }

    fun refreshRecents() {
        viewModelScope.launch {
            val tasks = systemBridge.getRecentTasks()
            _recentTasks.value = tasks
            _selectedSwitcherIndex.value = 0
        }
    }

    fun switchPrev(launchImmediately: Boolean = true) {
        val list = switcherTargets.value
        if (list.isEmpty()) return
        val next = (_selectedSwitcherIndex.value - 1 + list.size) % list.size
        _selectedSwitcherIndex.value = next
        if (launchImmediately) {
            viewModelScope.launch { systemBridge.switchToTask(list[next]) }
        }
    }

    fun switchNext(launchImmediately: Boolean = true) {
        val list = switcherTargets.value
        if (list.isEmpty()) return
        val next = (_selectedSwitcherIndex.value + 1) % list.size
        _selectedSwitcherIndex.value = next
        if (launchImmediately) {
            viewModelScope.launch { systemBridge.switchToTask(list[next]) }
        }
    }

    fun togglePin(packageName: String) {
        val current = _pinnedPackages.value.toMutableList()
        if (current.contains(packageName)) current.remove(packageName) else current.add(packageName)
        _pinnedPackages.value = current
    }

    fun onSwipeDownToNotifications() {
        viewModelScope.launch { systemBridge.expandNotificationShade() }
    }

    override fun onCleared() {
        systemBridge.unbind()
        repository.release()
    }
}