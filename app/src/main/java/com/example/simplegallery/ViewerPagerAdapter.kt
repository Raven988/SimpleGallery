package com.example.simplegallery

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.simplegallery.databinding.PageMediaBinding

/** Страница ViewPager2: фото через Glide с зумом, видео через VideoView с кнопкой play/pause и seekbar. */
class ViewerPagerAdapter(
    private val items: List<MediaItem>
) : RecyclerView.Adapter<ViewerPagerAdapter.PageViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())

    inner class PageViewHolder(val binding: PageMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var hideRunnable: Runnable? = null
        var progressRunnable: Runnable? = null
        var isSeeking = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = PageMediaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val item = items[position]
        if (item.isVideo) {
            holder.binding.image.visibility = View.GONE
            holder.binding.video.visibility = View.VISIBLE
            holder.binding.seekBar.progress = 0
            showControls(holder)

            holder.binding.video.setVideoURI(item.uri)

            holder.binding.video.setOnPreparedListener { mp ->
                mp.setOnInfoListener { _, _, _ -> true }
                holder.binding.seekBar.max = holder.binding.video.duration
            }

            holder.binding.video.setOnCompletionListener {
                cancelHide(holder)
                cancelProgress(holder)
                holder.binding.playPauseButton.setImageResource(R.drawable.ic_play)
                holder.binding.seekBar.progress = holder.binding.seekBar.max
                showControls(holder)
            }

            holder.binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    holder.isSeeking = true
                    cancelHide(holder)
                    cancelProgress(holder)
                }
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) holder.binding.video.seekTo(progress)
                }
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    holder.isSeeking = false
                    if (holder.binding.video.isPlaying) {
                        startProgress(holder)
                        scheduleHide(holder)
                    }
                }
            })

            holder.binding.playPauseButton.setOnClickListener {
                cancelHide(holder)
                if (holder.binding.video.isPlaying) {
                    holder.binding.video.pause()
                    cancelProgress(holder)
                    holder.binding.playPauseButton.setImageResource(R.drawable.ic_play)
                } else {
                    holder.binding.video.start()
                    holder.binding.seekBar.max = holder.binding.video.duration
                    holder.binding.playPauseButton.setImageResource(R.drawable.ic_pause)
                    startProgress(holder)
                    scheduleHide(holder)
                }
            }

            holder.binding.video.setOnClickListener {
                cancelHide(holder)
                if (holder.binding.playPauseButton.visibility == View.VISIBLE) {
                    if (holder.binding.video.isPlaying) fadeOutControls(holder)
                } else {
                    showControls(holder)
                    if (holder.binding.video.isPlaying) scheduleHide(holder)
                }
            }
        } else {
            holder.binding.video.visibility = View.GONE
            holder.binding.playPauseButton.visibility = View.GONE
            holder.binding.seekBar.visibility = View.GONE
            holder.binding.image.visibility = View.VISIBLE
            holder.binding.image.reset()
            Glide.with(holder.binding.image).load(item.uri).into(holder.binding.image)
        }
    }

    // --- Progress updater ---

    private fun startProgress(holder: PageViewHolder) {
        cancelProgress(holder)
        val runnable = object : Runnable {
            override fun run() {
                if (!holder.isSeeking && holder.binding.video.isPlaying) {
                    holder.binding.seekBar.progress = holder.binding.video.currentPosition
                    handler.postDelayed(this, 500)
                }
            }
        }
        holder.progressRunnable = runnable
        handler.post(runnable)
    }

    private fun cancelProgress(holder: PageViewHolder) {
        holder.progressRunnable?.let { handler.removeCallbacks(it) }
        holder.progressRunnable = null
    }

    // --- Controls visibility ---

    private fun scheduleHide(holder: PageViewHolder) {
        val runnable = Runnable { fadeOutControls(holder) }
        holder.hideRunnable = runnable
        handler.postDelayed(runnable, 2500)
    }

    private fun cancelHide(holder: PageViewHolder) {
        holder.hideRunnable?.let { handler.removeCallbacks(it) }
        holder.hideRunnable = null
    }

    private fun showControls(holder: PageViewHolder) {
        holder.binding.playPauseButton.animate().cancel()
        holder.binding.seekBar.animate().cancel()
        holder.binding.playPauseButton.alpha = 1f
        holder.binding.seekBar.alpha = 1f
        holder.binding.playPauseButton.visibility = View.VISIBLE
        holder.binding.seekBar.visibility = View.VISIBLE
    }

    private fun fadeOutControls(holder: PageViewHolder) {
        listOf(holder.binding.playPauseButton, holder.binding.seekBar).forEach { view ->
            view.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { view.visibility = View.GONE }
                .start()
        }
    }

    // --- Cleanup ---

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        cleanupHolder(holder)
    }

    override fun onViewDetachedFromWindow(holder: PageViewHolder) {
        super.onViewDetachedFromWindow(holder)
        cleanupHolder(holder)
    }

    private fun cleanupHolder(holder: PageViewHolder) {
        cancelHide(holder)
        cancelProgress(holder)
        if (holder.binding.video.isPlaying) holder.binding.video.stopPlayback()
        holder.binding.playPauseButton.animate().cancel()
        holder.binding.seekBar.animate().cancel()
        holder.binding.playPauseButton.setImageResource(R.drawable.ic_play)
        holder.binding.seekBar.progress = 0
    }

    override fun getItemCount(): Int = items.size
}
