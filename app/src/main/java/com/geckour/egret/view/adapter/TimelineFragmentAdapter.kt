package com.geckour.egret.view.adapter

import android.databinding.DataBindingUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.databinding.ItemRecycleTimelineBinding
import com.geckour.egret.view.adapter.model.TimelineContent
import com.squareup.picasso.Picasso
import java.util.*
import kotlin.collections.ArrayList

class TimelineFragmentAdapter(val listener: IListenr) : RecyclerView.Adapter<TimelineFragmentAdapter.ViewHolder>() {

    private val contents: ArrayList<TimelineContent> = ArrayList()

    inner class ViewHolder(val binding: ItemRecycleTimelineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindData(content: TimelineContent) {
            binding.content = content

            // TODO: 以下2つもdatabindingできる
            binding.timeString = Date(content.time).toString()
            Picasso.with(binding.icon.context).load(content.iconUrl).into(binding.icon)

            binding.icon.setOnClickListener { listener.onClickIcon(content.accountId) }
        }
    }

    interface IListenr {
        fun onClickIcon(accountId: Long)
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val binding = DataBindingUtil.inflate<ItemRecycleTimelineBinding>(LayoutInflater.from(parent?.context), R.layout.item_recycle_timeline, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        val item = contents[position]
        holder?.bindData(item)
    }

    override fun getItemCount(): Int {
        return contents.size
    }

    fun addContent(content: TimelineContent) {
        this.contents.add(0, content)
        notifyItemInserted(0)
    }

    fun addAllContents(contents: List<TimelineContent>) {
        this.contents.addAll(0, contents)
        notifyItemRangeInserted(0, contents.size)
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