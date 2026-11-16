// ui/MainActivity.kt
package com.yourname.wplauncher.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourname.wplauncher.core.model.TileSize
import com.yourname.wplauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()

    private val binderListener = Shizuku.OnBinderReceivedListener {
        viewModel.systemBridge.requestPermissionIfNeeded()
        viewModel.systemBridge.bind()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Shizuku.addBinderReceivedListenerSticky(binderListener)

        setupTilesGrid()
        setupPinnedRightList()
        setupSwitcherArrows()
        setupSwipeToExpandShade()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        // Обновляем список задач только в момент возврата на рабочий стол (0 затрат в фоне!)
        viewModel.refreshRecents()
    }

    private fun setupTilesGrid() {
        // При повороте экрана автоматически меняем число колонок: 2 в портрете, 4 в ландшафте
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape) 4 else 2

        val tileAdapter = TileAdapter(
            getIcon = { viewModel.repository.getIcon(it) },
            onClick = { tile -> tile.app?.let { viewModel.repository.launch(it) } }
        )

        val gridLayoutManager = GridLayoutManager(this, spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val item = tileAdapter.currentList.getOrNull(position)
                    return if (item?.size == TileSize.WIDE_2X1) 2 else 1
                }
            }
        }

        binding.rvTiles.apply {
            layoutManager = gridLayoutManager
            adapter = tileAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupPinnedRightList() {
        val pinnedAdapter = PinnedAppsAdapter(
            getIcon = { viewModel.repository.getIcon(it) },
            onClick = { app -> viewModel.repository.launch(app) },
            onLongClick = { app -> viewModel.togglePin(app.packageName) }
        )
        binding.rvPinnedRight.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = pinnedAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSwitcherArrows() {
        binding.btnPrevApp.setOnClickListener { viewModel.switchPrev(launchImmediately = true) }
        binding.btnNextApp.setOnClickListener { viewModel.switchNext(launchImmediately = true) }
    }

    private fun setupSwipeToExpandShade() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                // Быстрый свайп вниз в любом месте -> открываем шторку через Shizuku
                if (dy > 140 && abs(dy) > abs(dx) * 1.5f && velocityY > 400) {
                    viewModel.onSwipeDownToNotifications()
                    return true
                }
                return false
            }
        })

        binding.rootContainer.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tiles.collect {
                        (binding.rvTiles.adapter as TileAdapter).submitList(it)
                    }
                }
                launch {
                    viewModel.pinnedApps.collect {
                        (binding.rvPinnedRight.adapter as PinnedAppsAdapter).submitList(it)
                    }
                }
                launch {
                    viewModel.currentSwitchLabel.collect {
                        binding.tvCurrentApp.text = it
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        super.onDestroy()
    }
}