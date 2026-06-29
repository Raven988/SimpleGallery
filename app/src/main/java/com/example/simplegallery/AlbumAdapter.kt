package com.example.simplegallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.simplegallery.databinding.ItemAlbumBinding

/** Сетка папок-альбомов с обложкой, названием и количеством. */
class AlbumAdapter(
    private val items: List<Album>,
    private val onClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    inner class AlbumViewHolder(val binding: ItemAlbumBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = items[position]
        Glide.with(holder.binding.cover)
            .load(album.coverUri)
            .centerCrop()
            .into(holder.binding.cover)
        holder.binding.name.text = album.name
        holder.binding.count.text = album.count.toString()
        holder.binding.root.setOnClickListener { onClick(album) }
    }

    override fun getItemCount(): Int = items.size
}
