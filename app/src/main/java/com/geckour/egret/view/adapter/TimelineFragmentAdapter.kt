package com.geckour.egret.view.adapter

import android.databinding.DataBindingUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.databinding.ItemRecycleTimelineBinding
import com.geckour.egret.view.adapter.model.TimelineContent
import com.squareup.picasso.Picasso
import java.util.*

class TimelineFragmentAdapter: RecyclerView.Adapter<TimelineFragmentAdapter.ViewHolder>() {

    inner class ViewHolder(v: View): RecyclerView.ViewHolder(v) {
        val binding: ItemRecycleTimelineBinding = DataBindingUtil.bind(v)

        fun bindData(position: Int) {
            val content = contents[position]

            Picasso.with(binding.icon.context).load(content.iconUrl).into(binding.icon)
            binding.nameStrong.text = content.nameStrong
            binding.nameWeak.text = content.nameWeak
            binding.time.text = Date(content.time).toString()
            binding.body.text = content.body
        }
    }

    private val contents: ArrayList<TimelineContent> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.item_recycle_timeline, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        holder?.bindData(position)
    }

    override fun getItemCount(): Int {
        return contents.size
    }

    fun addContent(content: TimelineContent) {
        this.contents.add(content)
        notifyItemInserted(this.contents.size)
    }

    fun addAllContents(contents: List<TimelineContent>) {
        val size = this.contents.size
        this.contents.addAll(contents)
        notifyItemRangeInserted(size, contents.size)
    }

    fun clearContents() {
        val size = this.contents.size
        this.contents.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun resetContent(contents: List<TimelineContent>) {
        clearContents()
        addAllContents(contents)
    }
}