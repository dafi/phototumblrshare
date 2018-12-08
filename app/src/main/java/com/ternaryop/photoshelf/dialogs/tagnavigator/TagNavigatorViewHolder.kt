package com.ternaryop.photoshelf.dialogs.tagnavigator

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ternaryop.photoshelf.R

class TagNavigatorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val tagView: TextView = itemView.findViewById(R.id.tag)
    private val countView: TextView = itemView.findViewById(R.id.count)

    fun bindModel(tagCounter: TagCounter) {
        tagView.text = tagCounter.tag
        countView.text = String.format("%3d", tagCounter.count)
    }

    fun setOnClickListeners(listener: View.OnClickListener) {
        itemView.setOnClickListener(listener)
        itemView.tag = adapterPosition
    }
}