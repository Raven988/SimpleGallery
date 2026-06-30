package com.example.simplegallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.simplegallery.databinding.ItemAlbumBinding

class DateGroupAdapter(
    private val items: List<DateGroup>,
    private val onClick: (DateGroup) -> Unit
) : RecyclerView.Adapter<DateGroupAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = items[position]
        Glide.with(holder.binding.cover).load(group.coverUri).centerCrop().into(holder.binding.cover)
        holder.binding.name.text = group.label
        holder.binding.count.text = group.count.toString()
        holder.binding.root.setOnClickListener { onClick(group) }
    }

    override fun getItemCount(): Int = items.size
}
