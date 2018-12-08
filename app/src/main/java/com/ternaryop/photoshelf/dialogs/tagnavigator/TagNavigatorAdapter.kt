package com.ternaryop.photoshelf.dialogs.tagnavigator

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ternaryop.photoshelf.R

interface TagNavigatorListener {
    fun onClick(item: TagCounter)
}

class TagNavigatorAdapter(
    private val context: Context,
    list: List<TagCounter>,
    private val tagNavigatorListener: TagNavigatorListener)
    : RecyclerView.Adapter<TagNavigatorViewHolder>(), View.OnClickListener  {

    val items = list.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagNavigatorViewHolder {
        return TagNavigatorViewHolder(LayoutInflater.from(context)
            .inflate(R.layout.tag_navigator_row, parent, false))
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: TagNavigatorViewHolder, position: Int) {
        val item = items[position]

        holder.bindModel(item)
        holder.setOnClickListeners(this)
    }

    override fun onClick(v: View) {
        tagNavigatorListener.onClick(items[v.tag as Int])
    }

    fun sortByTagCount() {
        items.sortWith(Comparator { lhs, rhs ->
            // sort descending
            val sign = rhs.count - lhs.count
            if (sign == 0) lhs.compareTagTo(rhs) else sign
        })
        notifyDataSetChanged()
    }

    fun sortByTagName() {
        items.sortWith(Comparator { lhs, rhs -> lhs.compareTagTo(rhs) })
        notifyDataSetChanged()
    }
}
