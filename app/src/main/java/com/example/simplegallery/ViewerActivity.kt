package com.example.simplegallery

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.simplegallery.databinding.ActivityViewerBinding

/** Полноэкранный просмотр: свайпы, зум фото, воспроизведение видео. */
class ViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerBinding
    private val items = ArrayList<MediaItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Контент рисуется под системными барами
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Скрываем бары сразу — без анимации и без перераспределения layout
        // Свайп от края экрана временно покажет их (без изменения размеров контента)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        @Suppress("DEPRECATION")
        val passed: ArrayList<MediaItem> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(EXTRA_ITEMS, MediaItem::class.java)
            } else {
                intent.getParcelableArrayListExtra(EXTRA_ITEMS)
            } ?: arrayListOf()
        items.addAll(passed)

        val startPosition = intent.getIntExtra(EXTRA_POSITION, 0)
        binding.pager.adapter = ViewerPagerAdapter(items)
        binding.pager.setCurrentItem(startPosition, false)
    }

    companion object {
        const val EXTRA_ITEMS = "extra_items"
        const val EXTRA_POSITION = "extra_position"
    }
}
