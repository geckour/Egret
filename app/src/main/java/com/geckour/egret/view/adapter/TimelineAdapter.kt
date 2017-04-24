package com.geckour.egret.view.adapter

import android.databinding.DataBindingUtil
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import com.geckour.egret.R
import com.geckour.egret.databinding.ItemRecycleTimelineBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.RoundedCornerTransformation
import com.geckour.egret.view.adapter.model.TimelineContent
import com.squareup.picasso.Picasso
import java.util.*

class TimelineAdapter(val listener: IListenr) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

    private val contents: ArrayList<TimelineContent> = ArrayList()

    inner class ViewHolder(val binding: ItemRecycleTimelineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindData(content: TimelineContent) {
            binding.content = content

            // TODO: 以下2つもdatabindingできる
            binding.timeString = Common.getReadableDateString(content.time)
            Picasso.with(binding.icon.context).load(content.iconUrl).transform(RoundedCornerTransformation(8f, 0f)).into(binding.icon)
            binding.fav.setColorFilter(ContextCompat.getColor(binding.fav.context, if (content.favourited) R.color.colorAccent else R.color.icon_tint_dark))
            binding.boost.setColorFilter(ContextCompat.getColor(binding.boost.context, if (content.reblogged) R.color.colorAccent else R.color.icon_tint_dark))

            binding.icon.setOnClickListener { listener.onClickIcon(content.accountId) }
            binding.fav.setOnClickListener { listener.onFavStatus(content.id, binding.fav) }
            binding.boost.setOnClickListener { listener.onBoostStatus(content.id, binding.boost) }
            binding.body.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    interface IListenr {
        fun onClickIcon(accountId: Long)

        fun onFavStatus(statusId: Long, view: ImageView)

        fun onBoostStatus(statusId: Long, view: ImageView)
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

    fun getContents(): List<TimelineContent> = this.contents

    fun addContent(content: TimelineContent, limit: Int = DEFAULT_ITEMS_LIMIT) {
        this.contents.add(0, content)
        notifyItemInserted(0)
        removeItemsWhenOverLimit(limit)
    }

    fun addAllContents(contents: List<TimelineContent>, limit: Int = DEFAULT_ITEMS_LIMIT) {
        this.contents.addAll(0, contents)
        notifyItemRangeInserted(0, contents.size)
        removeItemsWhenOverLimit(limit)
    }

    fun addAllContentsInLast(contents: List<TimelineContent>, limit: Int = DEFAULT_ITEMS_LIMIT) {
        val size = this.contents.size
        this.contents.addAll(contents)
        notifyItemRangeInserted(size, contents.size)
        removeItemsWhenOverLimit(limit)
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

    fun removeContentByTootId(id: Long) {
        val shouldRemoveContents: ArrayList<TimelineContent> = ArrayList()
        contents.forEach { content ->
            if (content.id == id) shouldRemoveContents.add(content)
        }
        shouldRemoveContents.forEach { content ->
            val index = contents.indexOf(content)
            contents.remove(content)
            notifyItemRemoved(index)
        }
    }

    private fun removeItemsWhenOverLimit(limit: Int) {
        if (limit > 0 && contents.size > limit) {
            val size = contents.size
            contents.removeIf { content -> contents.indexOf(content) > limit - 1 }
            notifyItemRangeRemoved(limit, size - limit)
        }
    }

    companion object {
        val DEFAULT_ITEMS_LIMIT = 100
    }
}