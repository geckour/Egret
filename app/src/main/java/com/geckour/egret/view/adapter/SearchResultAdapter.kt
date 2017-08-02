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
import com.geckour.egret.api.model.Result
import com.geckour.egret.databinding.*
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.model.SearchResultAccount
import com.geckour.egret.view.adapter.model.SearchResultContent
import com.geckour.egret.view.adapter.model.TimelineContent
import com.geckour.egret.view.fragment.SearchResultFragment
import com.squareup.picasso.Picasso
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlin.collections.ArrayList

class SearchResultAdapter(val listener: TimelineAdapter.Callbacks) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    private val contents: ArrayList<SearchResultContent> = ArrayList()

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        lateinit private var accountBinding: ItemRecycleAccountBinding
        lateinit private var statusBinding: ItemRecycleStatusBinding
        lateinit private var hashTagBinding: ItemRecycleHashtagBinding

        constructor(binding: ItemRecycleAccountBinding) : this(binding.root) {
            accountBinding = binding
        }

        constructor(binding: ItemRecycleStatusBinding) : this(binding.root) {
            statusBinding = binding
        }

        constructor(binding: ItemRecycleHashtagBinding) : this(binding.root) {
            hashTagBinding = binding
        }

        fun bindData(content: TimelineContent.TimelineStatus) {
            initVisibility(SearchResultFragment.Category.Status)

            statusBinding.status = content

            statusBinding.body.visibility = View.VISIBLE

            content.mediaUrls.indices.forEach {
                when (it) {
                    0 -> {
                        if (content.isSensitive ?: false) toggleMediaSpoiler(statusBinding.mediaSpoilerWrap1, true)
                        setupMedia(statusBinding.media1, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                    1 -> {
                        if (content.isSensitive ?: false) toggleMediaSpoiler(statusBinding.mediaSpoilerWrap2, true)
                        setupMedia(statusBinding.media2, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                    2 -> {
                        if (content.isSensitive ?: false) toggleMediaSpoiler(statusBinding.mediaSpoilerWrap3, true)
                        setupMedia(statusBinding.media3, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                    3 -> {
                        if (content.isSensitive ?: false) toggleMediaSpoiler(statusBinding.mediaSpoilerWrap4, true)
                        setupMedia(statusBinding.media4, content.mediaPreviewUrls, content.mediaUrls, it)
                    }
                }
            }

            if (statusBinding.status.rebloggedStatusContent != null) {
                bindAction(Notification.NotificationType.reblog)
            }

            statusBinding.fav.setColorFilter(
                    ContextCompat.getColor(
                            statusBinding.fav.context,
                            if (statusBinding.status.rebloggedStatusContent?.favourited ?: statusBinding.status.favourited) R.color.colorAccent else R.color.icon_tint_dark))
            statusBinding.boost.setColorFilter(
                    ContextCompat.getColor(
                            statusBinding.boost.context,
                            if (statusBinding.status.rebloggedStatusContent?.reblogged ?: statusBinding.status.reblogged) R.color.colorAccent else R.color.icon_tint_dark))

            statusBinding.opt.setOnClickListener { showPopup(SearchResultFragment.Category.Status, it) }
            statusBinding.clearSpoiler.setOnClickListener { toggleBodySpoiler(statusBinding.status.rebloggedStatusContent ?: statusBinding.status, statusBinding.bodyAdditional.visibility == View.VISIBLE) }
            statusBinding.icon.setOnClickListener { listener.showProfile(statusBinding.status.rebloggedStatusContent?.accountId ?: statusBinding.status.accountId) }
            statusBinding.reply.setOnClickListener { listener.onReply(statusBinding.status.rebloggedStatusContent ?: statusBinding.status) }
            statusBinding.fav.setOnClickListener { listener.onFavStatus(statusBinding.status, statusBinding.fav) }
            statusBinding.boost.setOnClickListener { listener.onBoostStatus(statusBinding.status, statusBinding.boost) }
            statusBinding.body.movementMethod = Common.getMovementMethodFromPreference(statusBinding.body.context)

            toggleStatus(true)
        }

        fun bindData(content: SearchResultAccount) {
            initVisibility(SearchResultFragment.Category.Account)

            (accountBinding.root as ViewGroup).apply {
                for (i in 0..childCount - 1) getChildAt(i).setOnClickListener { listener.showProfile(accountBinding.account.id) }
            }
            accountBinding.account = content
        }

        fun bindData(hashTag: String) {
            initVisibility(SearchResultFragment.Category.HashTag)

            hashTagBinding.hashtag.text = "#$hashTag"
        }

        fun initVisibility(type: SearchResultFragment.Category) {
            when (type) {
                SearchResultFragment.Category.All -> {
                }

                SearchResultFragment.Category.Status -> {
                    toggleAction(false)
                    toggleStatus(false)
                    statusBinding.body.text = null

                    listOf(statusBinding.media1, statusBinding.media2, statusBinding.media3, statusBinding.media4)
                            .forEach {
                                it.apply {
                                    setImageBitmap(null)
                                    visibility = View.GONE
                                }
                            }

                    listOf(statusBinding.mediaSpoilerWrap1, statusBinding.mediaSpoilerWrap2, statusBinding.mediaSpoilerWrap3, statusBinding.mediaSpoilerWrap4)
                            .forEach { toggleMediaSpoiler(it, false) }
                }

                SearchResultFragment.Category.Account -> {
                }

                SearchResultFragment.Category.HashTag -> {
                }
            }
        }

        fun bindAction(notificationType: Notification.NotificationType) {
            if (notificationType == Notification.NotificationType.reblog) {
                statusBinding.indicateAction.setImageResource(R.drawable.ic_repeat_black_24px)
                statusBinding.actionBy.setText(R.string.reblogged_by)
                toggleAction(true)
            }
        }

        fun toggleAction(show: Boolean) {
            listOf(statusBinding.indicateAction, statusBinding.actionIcon, statusBinding.actionBy, statusBinding.actionName)
                    .forEach {
                        it.apply {
                            visibility = if (show) View.VISIBLE else View.GONE
                            if (show) setOnClickListener { listener.showProfile(statusBinding.status.accountId) }
                        }
                    }
        }

        fun toggleStatus(show: Boolean) {
            listOf(
                    statusBinding.icon,
                    statusBinding.nameStrong,
                    statusBinding.nameWeak,
                    statusBinding.body,
                    statusBinding.reply,
                    statusBinding.fav,
                    statusBinding.favCount,
                    statusBinding.boost,
                    statusBinding.boostCount,
                    statusBinding.padding)
                    .forEach {
                        it.apply { visibility = if (show) View.VISIBLE else View.GONE }
                    }
        }

        fun toggleBodySpoiler(content: TimelineContent.TimelineStatus, show: Boolean) {
            statusBinding.clearSpoiler.setText(if (show) R.string.button_read_more else R.string.button_enable_spoiler)
            statusBinding.bodyAdditional.visibility = if (show) View.GONE else View.VISIBLE
        }

        fun showPopup(type: SearchResultFragment.Category, view: View) {
            val popup = PopupMenu(view.context, view)
            val currentAccountId = OrmaProvider.db.selectFromAccessToken().isCurrentEq(true).last().accountId
            val contentAccountId = when(type) {
                SearchResultFragment.Category.Account -> accountBinding.account.id
                SearchResultFragment.Category.Status -> statusBinding.status.accountId
                else -> null
            }

            when(type) {
                SearchResultFragment.Category.All -> {
                    popup.setOnMenuItemClickListener { item ->
                        when (item?.itemId) { // TODO: オプションメニューを実装する
                            else -> false
                        }
                    }
                }

                SearchResultFragment.Category.Account -> {
                    popup.setOnMenuItemClickListener { item ->
                        when (item?.itemId) { // TODO: オプションメニューを実装する
                            else -> false
                        }
                    }
                }

                SearchResultFragment.Category.Status -> {
                    popup.setOnMenuItemClickListener { item ->
                        when (item?.itemId) {
                            R.id.action_url -> {
                                listener.copyTootUrlToClipboard(statusBinding.status.tootUrl)
                                true
                            }

                            R.id.action_open -> {
                                listener.showTootInBrowser(statusBinding.status)
                                true
                            }

                            R.id.action_copy -> {
                                listener.copyTootToClipboard(statusBinding.status)
                                true
                            }

                            R.id.action_mute -> {
                                listener.showMuteDialog(statusBinding.status)
                                true
                            }

                            R.id.action_block -> {
                                Common.resetAuthInfo()?.let {
                                    MastodonClient(it).blockAccount(statusBinding.status.accountId)
                                            .subscribeOn(Schedulers.newThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({
                                                Snackbar.make(view, "Blocked: ${statusBinding.status.nameWeak}", Snackbar.LENGTH_SHORT).show()
                                            }, Throwable::printStackTrace)
                                }
                                true
                            }

                            R.id.action_delete -> {
                                Common.resetAuthInfo()?.let {
                                    MastodonClient(it).deleteToot(statusBinding.status.id)
                                            .subscribeOn(Schedulers.newThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({
                                                removeStatusContentByTootId(statusBinding.status.id)
                                                Snackbar.make(view, "Deleted: ${statusBinding.status.body}", Snackbar.LENGTH_SHORT).show()
                                            }, Throwable::printStackTrace)
                                }
                                true
                            }

                            else -> false
                        }
                    }
                    popup.inflate(if (contentAccountId == currentAccountId) R.menu.toot_own else R.menu.toot_general)
                }

                SearchResultFragment.Category.HashTag -> {
                    popup.setOnMenuItemClickListener { item ->
                        when (item?.itemId) { // TODO: オプションメニューを実装する
                            else -> false
                        }
                    }
                }
            }
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
        return getContent(position).let {
            if (it.account != null) SearchResultFragment.Category.Account.ordinal
            else if (it.status != null) SearchResultFragment.Category.Status.ordinal
            else if (it.hashTag != null) SearchResultFragment.Category.HashTag.ordinal
            else -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        if (viewType == SearchResultFragment.Category.Account.ordinal) {
            val binding = DataBindingUtil.inflate<ItemRecycleAccountBinding>(LayoutInflater.from(parent?.context), R.layout.item_recycle_account, parent, false)
            return ViewHolder(binding)
        }
        if (viewType == SearchResultFragment.Category.Status.ordinal) {
            val binding = DataBindingUtil.inflate<ItemRecycleStatusBinding>(LayoutInflater.from(parent?.context), R.layout.item_recycle_status, parent, false)
            return ViewHolder(binding)
        }
        if (viewType == SearchResultFragment.Category.HashTag.ordinal) {
            val binding = DataBindingUtil.inflate<ItemRecycleHashtagBinding>(LayoutInflater.from(parent?.context), R.layout.item_recycle_hashtag, parent, false)
            return ViewHolder(binding)
        }
        throw IllegalArgumentException("viewType does not match any defined type.")
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        val item = getContent(position)
        item.account?.let { holder?.bindData(it) }
        item.status?.let { holder?.bindData(it) }
        item.hashTag?.let { holder?.bindData(it) }
    }

    override fun getItemCount(): Int {
        return contents.size
    }

    fun getContent(index: Int): SearchResultContent = contents[index]

    fun addAllContentsByResult(result: Result) {
        result.accounts?.map { SearchResultContent(account = Common.getSearchResultAccountContent(it)) }?.let { addAllContents(it) }
        result.statuses?.map { SearchResultContent(status = Common.getTimelineContent(status = it).status!!) }?.let { addAllContents(it) }
        result.hashTags?.let { it.map { SearchResultContent(hashTag = it) }.let { addAllContents(it) } }
    }

    fun addContent(content: SearchResultContent) {
        val size = itemCount
        this.contents.add(content)
        notifyItemInserted(size)
    }

    fun addAllContents(contents: List<SearchResultContent>) {
        if (contents.isEmpty()) return

        var size = itemCount
        this.contents.addAll(contents)
        notifyItemRangeInserted(size, contents.size)
    }

    fun clearContents() {
        val size = this.contents.size
        this.contents.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun resetContents(contents: List<SearchResultContent>) {
        clearContents()
        addAllContents(contents)
    }

    fun resetContentsByResult(result: Result) {
        clearContents()
        addAllContentsByResult(result)
    }

    fun removeStatusContentByTootId(id: Long) {
        val shouldRemoveContents: ArrayList<SearchResultContent> = ArrayList()
        this.contents.forEach { content ->
            if (content.status != null && content.status.id == id) shouldRemoveContents.add(content)
        }
        shouldRemoveContents.forEach { content ->
            val index = this.contents.indexOf(content)
            this.contents.remove(content)
            notifyItemRemoved(index)
        }
    }
}