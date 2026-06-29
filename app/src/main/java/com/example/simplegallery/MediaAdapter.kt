package com.example.simplegallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.simplegallery.databinding.ItemMediaBinding

/** Адаптер сетки миниатюр с поддержкой режима выделения. */
class MediaAdapter(
    private val items: List<MediaItem>,
    private val onClick: (position: Int) -> Unit,
    private val onLongClick: (position: Int) -> Unit,
    private val onSelectionChanged: (count: Int) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    val selectedPositions = mutableSetOf<Int>()
    var isSelectionMode = false
        private set

    inner class MediaViewHolder(val binding: ItemMediaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val item = items[position]
        val selected = position in selectedPositions

        Glide.with(holder.binding.thumbnail)
            .load(item.uri)
            .centerCrop()
            .into(holder.binding.thumbnail)

        holder.binding.videoBadge.visibility =
            if (item.isVideo && !isSelectionMode) View.VISIBLE else View.GONE
        holder.binding.checkbox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.binding.checkbox.isChecked = selected
        holder.binding.selectionOverlay.visibility = if (selected) View.VISIBLE else View.GONE

        holder.binding.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (isSelectionMode) toggleSelection(pos) else onClick(pos)
        }

        holder.binding.root.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            if (!isSelectionMode) {
                enterSelectionMode(pos)
            } else {
                onLongClick(pos)
            }
            true
        }
    }

    fun toggleSelection(position: Int) {
        if (position in selectedPositions) selectedPositions.remove(position)
        else selectedPositions.add(position)
        notifyItemChanged(position)
        onSelectionChanged(selectedPositions.size)
    }

    fun enterSelectionMode(position: Int) {
        isSelectionMode = true
        selectedPositions.clear()
        selectedPositions.add(position)
        notifyDataSetChanged()
        onSelectionChanged(selectedPositions.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    override fun getItemCount(): Int = items.size
}
