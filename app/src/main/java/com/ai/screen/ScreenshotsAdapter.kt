package com.ai.screen

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ScreenshotsAdapter(
    private val onItemClick: (Uri) -> Unit
) : ListAdapter<Uri, ScreenshotsAdapter.ScreenshotViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScreenshotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_screenshot, parent, false)
        return ScreenshotViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScreenshotViewHolder, position: Int) {
        val uri = getItem(position)
        Glide.with(holder.itemView.context)
            .load(uri)
            .fitCenter()
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            onItemClick(uri)
        }
    }

    class ScreenshotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    class DiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
    }
}
