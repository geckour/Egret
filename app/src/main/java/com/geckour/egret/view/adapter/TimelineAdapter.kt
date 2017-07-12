package com.geckour.egret.view.adapter

import android.databinding.DataBindingUtil
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageView
import android.widget.PopupMenu
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Notification
import com.geckour.egret.databinding.ItemRecycleTimelineBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.model.TimelineContent
import com.squareup.picasso.Picasso
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*

class TimelineAdapter(val listener: Callbacks, val doFilter: Boolean = true) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

    enum class ContentType {
        Status,
        Notification
    }

    private val contents: ArrayList<TimelineContent> = ArrayList()

    interface Callbacks {
        val showTootInBrowser: (content: TimelineContent.TimelineStatus) -> Any

        val copyTootToClipboard: (content: TimelineContent.TimelineStatus) -> Any

        val showMuteDialog: (content: TimelineContent.TimelineStatus) -> Any

        val showProfile: (accountId: Long) -> Any

        val onReply: (content: TimelineContent.TimelineStatus) -> Any

        val onFavStatus: (statusId: Long, view: ImageView) -> Any

        val onBoostStatus: (statusId: Long, view: ImageView) -> Any

        val onClickMedia: (urls: List<String>, position: Int) -> Any
    }

    inner class ViewHolder(val binding: ItemRecycleTimelineBinding): RecyclerView.ViewHolder(binding.root) {
        fun bindData(content: TimelineContent.TimelineStatus) {
            initVisibility()

            binding.status = content

            binding.body.visibility = View.VISIBLE

            content.mediaUrls.indices.forEach {
                when (it) {
                    0 -> {
                        if (content.isSensitive) toggleMediaSpoiler(binding.mediaSpoilerWrap1, true)
                        setupMedia(binding.media1, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                    1 -> {
                        if (content.isSensitive) toggleMediaSpoiler(binding.mediaSpoilerWrap2, true)
                        setupMedia(binding.media2, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                    2 -> {
                        if (content.isSensitive) toggleMediaSpoiler(binding.mediaSpoilerWrap3, true)
                        setupMedia(binding.media3, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                    3 -> {
                        if (content.isSensitive) toggleMediaSpoiler(binding.mediaSpoilerWrap4, true)
                        setupMedia(binding.media4, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                }
            }

            if (binding.status.rebloggedStatusContent != null) {
                showRebloggedBy()
            }

            binding.fav.setColorFilter(
                    ContextCompat.getColor(
                            binding.fav.context,
                            if (binding.status.rebloggedStatusContent?.favourited ?: binding.status.favourited) R.color.colorAccent else R.color.icon_tint_dark))
            binding.boost.setColorFilter(
                    ContextCompat.getColor(
                            binding.boost.context,
                            if (binding.status.rebloggedStatusContent?.reblogged ?: binding.status.reblogged) R.color.colorAccent else R.color.icon_tint_dark))

            binding.opt.setOnClickListener { showPopup(it) }
            binding.icon.setOnClickListener { listener.showProfile(binding.status.rebloggedStatusContent?.accountId ?: binding.status.accountId) }
            binding.reply.setOnClickListener { listener.onReply(binding.status.rebloggedStatusContent ?: binding.status) }
            binding.fav.setOnClickListener { listener.onFavStatus(binding.status.rebloggedStatusContent?.id ?: binding.status.id, binding.fav) }
            binding.boost.setOnClickListener { listener.onBoostStatus(binding.status.rebloggedStatusContent?.id ?: binding.status.id, binding.boost) }
            binding.body.movementMethod = Common.getMovementMethodFromPreference(binding.body.context)
        }

        fun bindData(content: TimelineContent.TimelineNotification) {
            initVisibility()

            binding.notification = content

            when (content.type) {
                Notification.Companion.NotificationType.mention.name -> {}
                Notification.Companion.NotificationType.reblog.name -> {}
                Notification.Companion.NotificationType.favourite.name -> {}
                Notification.Companion.NotificationType.follow.name -> {}
            }
        }

        fun initVisibility() {
            binding.body.apply {
                text = null
                visibility = View.GONE
            }
            listOf(binding.indicateReblog, binding.rebloggedBy, binding.rebloggedName)
                    .forEach { it.visibility = View.GONE }
            listOf(binding.media1, binding.media2, binding.media3, binding.media4)
                    .forEach {
                        it.apply {
                            setImageBitmap(null)
                            visibility = View.GONE
                        }
                    }
            listOf(binding.mediaSpoilerWrap1, binding.mediaSpoilerWrap2, binding.mediaSpoilerWrap3, binding.mediaSpoilerWrap4)
                    .forEach { toggleMediaSpoiler(it, false) }
        }

        fun showRebloggedBy() {
            listOf(binding.indicateReblog, binding.rebloggedBy, binding.rebloggedName)
                    .forEach {
                        it.apply {
                            visibility = View.VISIBLE
                            setOnClickListener { listener.showProfile(binding.status.accountId) }
                        }
                    }
        }

        fun showPopup(view: View) {
            val popup = PopupMenu(view.context, view)
            val currentAccountId = OrmaProvider.db.selectFromAccessToken().isCurrentEq(true).last().accountId
            val contentAccountId = binding.status.accountId

            popup.setOnMenuItemClickListener { item ->
                when (item?.itemId) {
                    R.id.action_open -> {
                        listener.showTootInBrowser(binding.status)
                        true
                    }

                    R.id.action_copy -> {
                        listener.copyTootToClipboard(binding.status)
                        true
                    }

                    R.id.action_mute -> {
                        listener.showMuteDialog(binding.status)
                        true
                    }

                    R.id.action_block -> {
                        Common.resetAuthInfo()?.let {
                            MastodonClient(it).blockAccount(binding.status.accountId)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        Snackbar.make(view, "Blocked: ${binding.status.nameWeak}", Snackbar.LENGTH_SHORT).show()
                                    }, Throwable::printStackTrace)
                        }
                        true
                    }

                    R.id.action_delete -> {
                        Common.resetAuthInfo()?.let {
                            MastodonClient(it).deleteToot(binding.status.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        removeContentByTootId(binding.status.id)
                                        Snackbar.make(view, "Deleted: ${binding.status.body}", Snackbar.LENGTH_SHORT).show()
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

        fun toggleMediaSpoiler(view: View, show: Boolean) {
            view.apply {
                setOnClickListener { it.visibility = View.GONE }
                visibility = if (show) View.VISIBLE else View.GONE
            }
        }

        fun setupMedia(view: ImageView, previewUrls: List<String>, urls: List<String>, position: Int) {
            view.apply {
                visibility = View.VISIBLE
                setOnClickListener { listener.onClickMedia(urls, position) }
            }
            Picasso.with(binding.media1.context).load(previewUrls[position]).into(view)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return contents[position].let { if (it.status != null) ContentType.Status.ordinal else if (it.notification != null) ContentType.Notification.ordinal else -1 }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        if (viewType == ContentType.Status.ordinal || viewType == ContentType.Notification.ordinal) {
            val binding = DataBindingUtil.inflate<ItemRecycleTimelineBinding>(LayoutInflater.from(parent?.context), R.layout.item_recycle_timeline, parent, false)
            return ViewHolder(binding)
        } else throw IllegalArgumentException("viewType does not match any defined type.")
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        val item = contents[position]
        item.status?.let { holder?.bindData(it) }
        item.notification?.let { holder?.bindData(it) }
    }

    override fun getItemCount(): Int {
        return contents.size
    }

    fun getContent(index: Int): TimelineContent = this.contents[index]

    fun getContents(): List<TimelineContent> = this.contents

    fun addContent(content: TimelineContent, limit: Int = DEFAULT_ITEMS_LIMIT) {
        content.takeIf { !shouldMute(it) }?.let {
            this.contents.add(0, it)
            notifyItemInserted(0)
            removeItemsWhenOverLimit(limit)
        }
    }

    fun addAllContents(contents: List<TimelineContent>, limit: Int = DEFAULT_ITEMS_LIMIT) {
        contents.filter { !shouldMute(it) }
                .let {
                    if (it.isEmpty()) return

                    this.contents.addAll(0, it)
                    notifyItemRangeInserted(0, it.size)
                    removeItemsWhenOverLimit(limit)
                }
    }

    fun addAllContentsAtLast(contents: List<TimelineContent>, limit: Int = DEFAULT_ITEMS_LIMIT) {
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

    fun resetContents(contents: List<TimelineContent>) {
        clearContents()
        addAllContents(contents)
    }

    fun removeContentByTootId(id: Long) {
        val shouldRemoveContents: ArrayList<TimelineContent> = ArrayList()
        this.contents.forEach { content ->
            if (content.status?.id == id) shouldRemoveContents.add(content)
        }
        shouldRemoveContents.forEach { content ->
            val index = this.contents.indexOf(content)
            this.contents.remove(content)
            notifyItemRemoved(index)
        }
    }

    fun shouldMute(content: TimelineContent): Boolean {
        if (!doFilter) return false
        OrmaProvider.db.selectFromMuteClient().forEach {
            if (content.status?.app == it.client) return true
        }
        OrmaProvider.db.selectFromMuteHashTag().forEach { tag ->
            content.status?.tags?.forEach { if (tag.hashTag == it) return true }
        }
        OrmaProvider.db.selectFromMuteKeyword().forEach {
            if (it.isRegex) {
                if (content.status?.body?.toString()?.matches(Regex(it.keyword, RegexOption.MULTILINE)) ?: false) return true
            } else if (content.status?.body?.toString()?.contains(it.keyword) ?: false) return true
        }
        OrmaProvider.db.selectFromMuteInstance().forEach {
            var instance = content.status?.nameWeak?.replace(Regex("^@.+@(.+)$"), "@$1") ?: ""
            if (content.status?.nameWeak == instance) {
                instance = Common.getCurrentAccessToken()?.instanceId?.let {
                    "@${OrmaProvider.db.selectFromInstanceAuthInfo().idEq(it).last().instance}"
                } ?: ""
            }

            if (it.instance == instance) return true
        }

        return false
    }

    private fun removeItemsWhenOverLimit(limit: Int = DEFAULT_ITEMS_LIMIT) {
        itemCount.let {
            this.contents.removeAll { this.contents.indexOf(it) > limit - 1 }
            notifyItemRangeRemoved(limit, it - limit)
        }
    }

    companion object {
        val DEFAULT_ITEMS_LIMIT = 100
    }
}