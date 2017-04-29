package com.geckour.egret.view.adapter

import android.databinding.DataBindingUtil
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.ImageView
import android.widget.PopupMenu
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.databinding.ItemRecycleTimelineBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.model.TimelineContent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*

class TimelineAdapter(val listener: IListener) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

    private val contents: ArrayList<TimelineContent> = ArrayList()

    inner class ViewHolder(val binding: ItemRecycleTimelineBinding): RecyclerView.ViewHolder(binding.root) {
        fun bindData(content: TimelineContent) {
            binding.content = content

            if (binding.content.rebloggedStatusContent != null) {
                binding.wrapRebloggedBy.visibility = View.VISIBLE
                binding.wrapRebloggedBy.setOnClickListener { listener.showProfile(binding.content.accountId) }
            }

            binding.fav.setColorFilter(
                    ContextCompat.getColor(
                            binding.fav.context,
                            if (binding.content.rebloggedStatusContent?.favourited ?: binding.content.favourited) R.color.colorAccent else R.color.icon_tint_dark))
            binding.boost.setColorFilter(
                    ContextCompat.getColor(
                            binding.boost.context,
                            if (binding.content.rebloggedStatusContent?.reblogged ?: binding.content.reblogged) R.color.colorAccent else R.color.icon_tint_dark))

            binding.opt.setOnClickListener { showPopup(it) }
            binding.icon.setOnClickListener { listener.showProfile(binding.content.rebloggedStatusContent?.accountId ?: binding.content.accountId) }
            binding.reply.setOnClickListener { listener.onReply(binding.content.rebloggedStatusContent ?: binding.content) }
            binding.fav.setOnClickListener { listener.onFavStatus(binding.content.rebloggedStatusContent?.id ?: binding.content.id, binding.fav) }
            binding.boost.setOnClickListener { listener.onBoostStatus(binding.content.rebloggedStatusContent?.id ?: binding.content.id, binding.boost) }
            binding.body.movementMethod = LinkMovementMethod.getInstance()
        }

        fun showPopup(view: View) {
            val popup = PopupMenu(view.context, view)
            val currentAccountId = OrmaProvider.db.selectFromAccessToken().isCurrentEq(true).last().accountId
            val contentAccountId = binding.content.accountId

            popup.setOnMenuItemClickListener { item ->
                when (item?.itemId) {
                    R.id.action_mute -> {
                        listener.showMuteDialog(binding.content)
                        true
                    }

                    R.id.action_block -> {
                        Common.resetAuthInfo()?.let {
                            MastodonClient(it).blockAccount(binding.content.accountId)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        Snackbar.make(view, "Blocked: ${binding.content.nameWeak}", Snackbar.LENGTH_SHORT).show()
                                    }, Throwable::printStackTrace)
                        }
                        true
                    }

                    R.id.action_delete -> {
                        Common.resetAuthInfo()?.let {
                            MastodonClient(it).deleteToot(binding.content.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        removeContentByTootId(binding.content.id)
                                        Snackbar.make(view, "Deleted: ${binding.content.body}", Snackbar.LENGTH_SHORT).show()
                                    }, Throwable::printStackTrace)
                        }
                        true
                    }

                    else -> false
                }
            }
            popup.inflate(if (contentAccountId == currentAccountId) R.menu.toot_own else R.menu.toot_general)
            popup.show()
        }
    }

    interface IListener {
        fun showMuteDialog(content: TimelineContent)

        fun showProfile(accountId: Long)

        fun onReply(content: TimelineContent)

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

    fun getContent(index: Int): TimelineContent = this.contents[index]

    fun getContents(): List<TimelineContent> = this.contents

    fun addContent(content: TimelineContent, limit: Int = DEFAULT_ITEMS_LIMIT) {
        val c = content.takeIf { c -> shouldMute(c) }?.let {
            this.contents.add(0, it)
            notifyItemInserted(0)
            removeItemsWhenOverLimit(limit)
        }
    }

    fun addAllContents(contents: List<TimelineContent>, limit: Int = DEFAULT_ITEMS_LIMIT) {
        val c = contents.takeWhile { c -> shouldMute(c) }

        if (c.isNotEmpty()) {
            this.contents.addAll(0, c)
            notifyItemRangeInserted(0, contents.size)
            removeItemsWhenOverLimit(limit)
        }
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

    fun shouldMute(content: TimelineContent): Boolean {
        OrmaProvider.db.selectFromMuteKeyword().forEach {
            if (it.isRegex) {
                if (content.body.matches(Regex(it.keyword))) return false
            } else if (content.body.contains(it.keyword)) return false
        }
        OrmaProvider.db.selectFromMuteInstance().forEach {
            if (content.nameWeak.matches(Regex("@.+${it.instance}"))) return false
        }
        OrmaProvider.db.selectFromMuteClient().forEach {
            if (content.app == it.client) return false
        }

        return true
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