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
import com.geckour.egret.databinding.ItemRecycleNotificationBinding
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
        val copyTootUrlToClipboard: (url: String) -> Any

        val showTootInBrowser: (content: TimelineContent.TimelineStatus) -> Any

        val copyTootToClipboard: (content: TimelineContent.TimelineStatus) -> Any

        val showMuteDialog: (content: TimelineContent.TimelineStatus) -> Any

        val showProfile: (accountId: Long) -> Any

        val onReply: (content: TimelineContent.TimelineStatus) -> Any

        val onFavStatus: (content: TimelineContent.TimelineStatus, view: ImageView) -> Any

        val onBoostStatus: (content: TimelineContent.TimelineStatus, view: ImageView) -> Any

        val onClickMedia: (urls: List<String>, position: Int) -> Any
    }

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        lateinit private var timelineBinding: ItemRecycleTimelineBinding
        lateinit private var notificationBinding: ItemRecycleNotificationBinding

        constructor(binding: ItemRecycleTimelineBinding): this(binding.root) {
            timelineBinding = binding
        }

        constructor(binding: ItemRecycleNotificationBinding): this(binding.root) {
            notificationBinding = binding
        }

        fun bindData(content: TimelineContent.TimelineStatus) {
            initVisibility(ContentType.Status)

            timelineBinding.status = content

            timelineBinding.body.visibility = View.VISIBLE
            if (content.spoilerText.isNotEmpty()) toggleBodySpoiler(ContentType.Status, true)

            content.mediaUrls.indices.forEach {
                when (it) {
                    0 -> {
                        if (content.isSensitive ?: false) toggleMediaSpoiler(timelineBinding.mediaSpoilerWrap1, true)
                        setupMedia(timelineBinding.media1, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                    1 -> {
                        if (content.isSensitive ?: false) toggleMediaSpoiler(timelineBinding.mediaSpoilerWrap2, true)
                        setupMedia(timelineBinding.media2, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                    2 -> {
                        if (content.isSensitive ?: false) toggleMediaSpoiler(timelineBinding.mediaSpoilerWrap3, true)
                        setupMedia(timelineBinding.media3, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                    3 -> {
                        if (content.isSensitive ?: false) toggleMediaSpoiler(timelineBinding.mediaSpoilerWrap4, true)
                        setupMedia(timelineBinding.media4, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                }
            }

            if (timelineBinding.status.rebloggedStatusContent != null) {
                bindAction(ContentType.Status, Notification.NotificationType.reblog)
            }

            timelineBinding.fav.setColorFilter(
                    ContextCompat.getColor(
                            timelineBinding.fav.context,
                            if (timelineBinding.status.rebloggedStatusContent?.favourited ?: timelineBinding.status.favourited) R.color.colorAccent else R.color.icon_tint_dark))
            timelineBinding.boost.setColorFilter(
                    ContextCompat.getColor(
                            timelineBinding.boost.context,
                            if (timelineBinding.status.rebloggedStatusContent?.reblogged ?: timelineBinding.status.reblogged) R.color.colorAccent else R.color.icon_tint_dark))

            timelineBinding.opt.setOnClickListener { showPopup(ContentType.Status, it) }
            timelineBinding.clearSpoiler.setOnClickListener { toggleBodySpoiler(ContentType.Status, timelineBinding.bodyAdditional.visibility == View.VISIBLE) }
            timelineBinding.icon.setOnClickListener { listener.showProfile(timelineBinding.status.rebloggedStatusContent?.accountId ?: timelineBinding.status.accountId) }
            timelineBinding.reply.setOnClickListener { listener.onReply(timelineBinding.status.rebloggedStatusContent ?: timelineBinding.status) }
            timelineBinding.fav.setOnClickListener { listener.onFavStatus(timelineBinding.status, timelineBinding.fav) }
            timelineBinding.boost.setOnClickListener { listener.onBoostStatus(timelineBinding.status, timelineBinding.boost) }
            timelineBinding.body.movementMethod = Common.getMovementMethodFromPreference(timelineBinding.body.context)

            toggleStatus(ContentType.Status, true)
        }

        fun bindData(content: TimelineContent.TimelineNotification) {
            initVisibility(ContentType.Notification)

            notificationBinding.notification = content

            notificationBinding.notification.status?.let { if (it.spoilerText.isNotEmpty()) toggleBodySpoiler(ContentType.Notification, true) }

            notificationBinding.opt.setOnClickListener { showPopup(ContentType.Notification, it) }
            notificationBinding.clearSpoiler.setOnClickListener { toggleBodySpoiler(ContentType.Notification, notificationBinding.bodyAdditional.visibility == View.VISIBLE) }
            notificationBinding.notification.status?.let { status -> notificationBinding.icon.setOnClickListener { listener.showProfile(status.rebloggedStatusContent?.accountId ?: status.accountId) } }
            notificationBinding.notification.status?.let { status -> notificationBinding.reply.setOnClickListener { listener.onReply(status.rebloggedStatusContent ?: status) } }
            notificationBinding.notification.status?.let { status -> notificationBinding.fav.setOnClickListener { listener.onFavStatus(status, notificationBinding.fav) } }
            notificationBinding.notification.status?.let { status -> notificationBinding.boost.setOnClickListener { listener.onBoostStatus(status, notificationBinding.boost) } }
            notificationBinding.body.movementMethod = Common.getMovementMethodFromPreference(notificationBinding.body.context)

            val type = content.type.let { Notification.NotificationType.valueOf(it) }
            bindAction(ContentType.Notification, type)
            if (type == Notification.NotificationType.follow) toggleStatus(ContentType.Notification, false)
            else toggleStatus(ContentType.Notification, true)
        }

        fun initVisibility(type: ContentType) {
            toggleAction(type, false)
            toggleStatus(type, false)
            initSpoiler(type)

            when(type) {
                ContentType.Status -> {
                    timelineBinding.body.apply {
                        text = null
                        visibility = View.GONE
                    }

                    listOf(timelineBinding.media1, timelineBinding.media2, timelineBinding.media3, timelineBinding.media4)
                            .forEach {
                                it.apply {
                                    setImageBitmap(null)
                                    visibility = View.GONE
                                }
                            }

                    listOf(timelineBinding.mediaSpoilerWrap1, timelineBinding.mediaSpoilerWrap2, timelineBinding.mediaSpoilerWrap3, timelineBinding.mediaSpoilerWrap4)
                            .forEach { toggleMediaSpoiler(it, false) }
                }

                ContentType.Notification -> {
                    notificationBinding.body.apply {
                        text = null
                        visibility = View.GONE
                    }

                    listOf(notificationBinding.media1, notificationBinding.media2, notificationBinding.media3, notificationBinding.media4)
                            .forEach {
                                it.apply {
                                    setImageBitmap(null)
                                    visibility = View.GONE
                                }
                            }

                    listOf(notificationBinding.mediaSpoilerWrap1, notificationBinding.mediaSpoilerWrap2, notificationBinding.mediaSpoilerWrap3, notificationBinding.mediaSpoilerWrap4)
                            .forEach { toggleMediaSpoiler(it, false) }
                }
            }
        }

        fun bindAction(contentType: ContentType, notificationType: Notification.NotificationType) {
            when (contentType) {
                ContentType.Status -> {
                    if (notificationType == Notification.NotificationType.reblog) {
                        timelineBinding.indicateAction.setImageResource(R.drawable.ic_repeat_black_24px)
                        timelineBinding.actionBy.setText(R.string.reblogged_by)
                        toggleAction(contentType, true)
                    }
                }

                ContentType.Notification -> {
                    when (notificationType) {
                        Notification.NotificationType.mention -> {
                            notificationBinding.indicateAction.setImageResource(R.drawable.ic_reply_black_24px)
                            notificationBinding.actionBy.setText(R.string.mentioned_by)
                        }
                        Notification.NotificationType.reblog -> {
                            notificationBinding.indicateAction.setImageResource(R.drawable.ic_repeat_black_24px)
                            notificationBinding.actionBy.setText(R.string.reblogged_by)
                        }
                        Notification.NotificationType.favourite -> {
                            notificationBinding.indicateAction.setImageResource(R.drawable.ic_star_black_24px)
                            notificationBinding.actionBy.setText(R.string.favourited_by)
                        }
                        Notification.NotificationType.follow -> {
                            notificationBinding.indicateAction.setImageResource(R.drawable.ic_person_add_black_24px)
                            notificationBinding.actionBy.setText(R.string.followed_by)
                        }
                    }
                    toggleAction(contentType, true)
                }
            }
        }

        fun toggleAction(type: ContentType, show: Boolean) {
            when(type) {
                ContentType.Status -> {
                    listOf(timelineBinding.indicateAction, timelineBinding.actionIcon, timelineBinding.actionBy, timelineBinding.actionName)
                            .forEach {
                                it.apply {
                                    visibility = if (show) View.VISIBLE else View.GONE
                                    if (show) setOnClickListener { listener.showProfile(timelineBinding.status.accountId) }
                                }
                            }
                }

                ContentType.Notification -> {
                    listOf(notificationBinding.indicateAction, notificationBinding.actionIcon, notificationBinding.actionBy, notificationBinding.actionName)
                            .forEach {
                                it.apply {
                                    visibility = if (show) View.VISIBLE else View.GONE
                                    if (show) setOnClickListener { listener.showProfile(notificationBinding.notification.accountId) }
                                }
                            }
                }
            }
        }

        fun toggleStatus(type: ContentType, show: Boolean) {
            when(type) {
                ContentType.Status -> {
                    listOf(
                            timelineBinding.icon,
                            timelineBinding.nameStrong,
                            timelineBinding.nameWeak,
                            timelineBinding.body,
                            timelineBinding.reply,
                            timelineBinding.fav,
                            timelineBinding.favCount,
                            timelineBinding.boost,
                            timelineBinding.boostCount,
                            timelineBinding.padding)
                            .forEach {
                                it.apply { visibility = if (show) View.VISIBLE else View.GONE }
                            }
                }

                ContentType.Notification -> {
                    listOf(
                            notificationBinding.icon,
                            notificationBinding.nameStrong,
                            notificationBinding.nameWeak,
                            notificationBinding.body,
                            notificationBinding.reply,
                            notificationBinding.fav,
                            notificationBinding.favCount,
                            notificationBinding.boost,
                            notificationBinding.boostCount,
                            notificationBinding.padding)
                            .forEach {
                                it.apply { visibility = if (show) View.VISIBLE else View.GONE }
                            }
                }
            }
        }

        fun initSpoiler(type: ContentType) {
            when(type) {
                ContentType.Status -> {
                    timelineBinding.bodyAdditional.visibility = View.GONE
                    timelineBinding.clearSpoiler.visibility = View.GONE
                }

                ContentType.Notification -> {
                    notificationBinding.bodyAdditional.visibility = View.GONE
                    notificationBinding.clearSpoiler.visibility = View.GONE
                }
            }
        }

        fun toggleBodySpoiler(type: ContentType, show: Boolean) {
            when(type) {
                ContentType.Status -> {
                    timelineBinding.clearSpoiler.apply {
                        setText(if (show) R.string.button_read_more else R.string.button_enable_spoiler)
                        visibility = View.VISIBLE
                    }
                    timelineBinding.bodyAdditional.visibility = if (show) View.GONE else View.VISIBLE
                }

                ContentType.Notification -> {
                    notificationBinding.notification.status?.let {
                        notificationBinding.clearSpoiler.apply {
                            setText(if (show) R.string.button_read_more else R.string.button_enable_spoiler)
                            visibility = View.VISIBLE
                        }
                        notificationBinding.bodyAdditional.visibility = if (show) View.GONE else View.VISIBLE
                    }
                }
            }
        }

        fun showPopup(type: ContentType, view: View) {
            val popup = PopupMenu(view.context, view)
            val currentAccountId = OrmaProvider.db.selectFromAccessToken().isCurrentEq(true).last().accountId
            val contentAccountId = when(type) {
                ContentType.Status -> timelineBinding.status.accountId
                ContentType.Notification -> notificationBinding.notification.accountId
            }

            when(type) {
                ContentType.Status -> {
                    popup.setOnMenuItemClickListener { item ->
                        when (item?.itemId) {
                            R.id.action_url -> {
                                listener.copyTootUrlToClipboard(timelineBinding.status.tootUrl)
                                true
                            }

                            R.id.action_open -> {
                                listener.showTootInBrowser(timelineBinding.status)
                                true
                            }

                            R.id.action_copy -> {
                                listener.copyTootToClipboard(timelineBinding.status)
                                true
                            }

                            R.id.action_mute -> {
                                listener.showMuteDialog(timelineBinding.status)
                                true
                            }

                            R.id.action_block -> {
                                Common.resetAuthInfo()?.let {
                                    MastodonClient(it).blockAccount(timelineBinding.status.accountId)
                                            .subscribeOn(Schedulers.newThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({
                                                Snackbar.make(view, "Blocked: ${timelineBinding.status.nameWeak}", Snackbar.LENGTH_SHORT).show()
                                            }, Throwable::printStackTrace)
                                }
                                true
                            }

                            R.id.action_delete -> {
                                Common.resetAuthInfo()?.let {
                                    MastodonClient(it).deleteToot(timelineBinding.status.id)
                                            .subscribeOn(Schedulers.newThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({
                                                removeContentByTootId(timelineBinding.status.id)
                                                Snackbar.make(view, "Deleted: ${timelineBinding.status.body}", Snackbar.LENGTH_SHORT).show()
                                            }, Throwable::printStackTrace)
                                }
                                true
                            }

                            else -> false
                        }
                    }
                }

                ContentType.Notification -> {
                    popup.setOnMenuItemClickListener { item ->
                        when (item?.itemId) { // TODO: オプションメニューを実装する
                            else -> false
                        }
                    }
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
            Picasso.with(view.context).load(previewUrls[position]).into(view)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return contents[position].let { if (it.status != null) ContentType.Status.ordinal else if (it.notification != null) ContentType.Notification.ordinal else -1 }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        if (viewType == ContentType.Status.ordinal) {
            val binding = DataBindingUtil.inflate<ItemRecycleTimelineBinding>(LayoutInflater.from(parent?.context), R.layout.item_recycle_timeline, parent, false)
            return ViewHolder(binding)
        }
        if (viewType == ContentType.Notification.ordinal) {
            val binding = DataBindingUtil.inflate<ItemRecycleNotificationBinding>(LayoutInflater.from(parent?.context), R.layout.item_recycle_notification, parent, false)
            return ViewHolder(binding)
        }
        throw IllegalArgumentException("viewType does not match any defined type.")
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
        contents.filter { !shouldMute(it) }
                .let {
                    if (it.isEmpty()) return

                    val size = this.contents.size
                    this.contents.addAll(contents)
                    notifyItemRangeInserted(size, contents.size)
                    removeItemsWhenOverLimit(limit)
                }
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