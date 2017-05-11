package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.Parcelable
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Notification
import com.geckour.egret.api.model.Status
import com.geckour.egret.api.service.MastodonService
import com.geckour.egret.databinding.FragmentTimelineBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.BaseActivity
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.google.gson.Gson
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class TimelineFragment: BaseFragment() {

    enum class Category(val rawValue: Int) {
        Public(0),
        Local(1),
        User(2),
        HashTag(3),
        Unknown(4)
    }

    companion object {
        val TAG = "timelineFragment"
        val ARGS_KEY_CATEGORY = "category"
        val STATE_ARGS_KEY_CONTENTS = "contents"
        val STATE_KEY_THEME_MODE = "theme mode"

        fun newInstance(category: Category): TimelineFragment {
            val fragment = TimelineFragment()
            val args = Bundle()
            args.putString(ARGS_KEY_CATEGORY, category.name)
            fragment.arguments = args

            return fragment
        }

        fun getCategoryById(rawValue: Int): Category = Category.values()[rawValue]
    }

    lateinit private var binding: FragmentTimelineBinding
    lateinit private var adapter: TimelineAdapter
    private var onTop = true
    private var inTouch = false
    private val bundle = Bundle()

    private var publicStream: Disposable? = null
    private var localStream: Disposable? = null
    private var userStream: Disposable? = null

    private var waitingContent = false
    private var waitingNotification = false
    private var waitingDeletedId = false

    private var nextId: Long? = -1

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_timeline, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val instanceId = Common.getCurrentAccessToken()?.instanceId
        (activity as MainActivity).supportActionBar?.show()
        val domain = if (instanceId == null) "not logged in" else OrmaProvider.db.selectFromInstanceAuthInfo().idEq(instanceId).last().instance
        val category = getCategory()
        (activity as MainActivity).supportActionBar?.title = "$category TL - $domain"

        binding.recyclerView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    inTouch = true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    inTouch = false
                }
            }
            false
        }
        binding.recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val scrollY: Int = recyclerView?.computeVerticalScrollOffset() ?: -1
                onTop = scrollY == 0 || onTop && !(inTouch && scrollY > 0)

                if (!onTop) {
                    val y = scrollY + (recyclerView?.height ?: -1)
                    val h = recyclerView?.computeVerticalScrollRange() ?: -1
                    if (y == h) {
                        when (getCategory()) {
                            Category.Public -> {}
                            Category.Local -> {}
                            Category.User -> showUserTimeline(loadNext = true)
                            Category.HashTag -> {}
                        }
                    }
                }
            }
        })
        adapter = TimelineAdapter((activity as MainActivity).timelineListener)
        binding.recyclerView.adapter = adapter
    }

    override fun onPause() {
        super.onPause()

        stopTimelineStreams()
        bundle.putParcelableArrayList(STATE_ARGS_KEY_CONTENTS, ArrayList(adapter.getContents()))
        bundle.putBoolean(STATE_KEY_THEME_MODE, (activity as BaseActivity).isModeDark())
    }

    override fun onResume() {
        super.onResume()

        if (bundle.containsKey(STATE_KEY_THEME_MODE) && (bundle.getBoolean(STATE_KEY_THEME_MODE, false) xor (activity as BaseActivity).isModeDark())) {
            bundle.clear()
            bundle.putBoolean(STATE_KEY_THEME_MODE, (activity as BaseActivity).isModeDark())
            (activity as MainActivity).showDefaultTimeline()
        }

        restoreTimeline(bundle)

        (activity as MainActivity).resetSelectionNavItem(
                when (getCategory()) {
                    Category.Public -> MainActivity.NAV_ITEM_TL_PUBLIC
                    Category.Local -> MainActivity.NAV_ITEM_TL_LOCAL
                    Category.User -> MainActivity.NAV_ITEM_TL_USER
                    else -> -1
                })
    }

    fun getCategory(): Category = if (arguments != null && arguments.containsKey(ARGS_KEY_CATEGORY)) Category.valueOf(arguments.getString(ARGS_KEY_CATEGORY, "Unknown")) else Category.Unknown

    fun restoreTimeline(bundle: Bundle) {
        if (bundle.containsKey(STATE_ARGS_KEY_CONTENTS)) {
            adapter.clearContents()
            val parcelables: ArrayList<Parcelable> = bundle.getParcelableArrayList(STATE_ARGS_KEY_CONTENTS)
            adapter.addAllContents(parcelables.map { it as TimelineContent })

            showTimelineByCategory(getCategory(), true)
        } else {
            showTimelineByCategory(getCategory())
        }
        bundle.clear()
    }

    fun showTimelineByCategory(category: Category, hasContents: Boolean = false) {
        if (hasContents) {
            when (category) {
                Category.Public -> startPublicTimelineStream()
                Category.Local -> startLocalTimelineStream()
                Category.User -> startUserTimelineStream()
                Category.HashTag -> {}
            }
        } else {
            when (category) {
                Category.Public -> startPublicTimelineStream()
                Category.Local -> startLocalTimelineStream()
                Category.User -> showUserTimeline(true)
                Category.HashTag -> {}
            }
        }
    }

    fun stopTimelineStreams() {
        stopPublicTimelineStream()
        stopLocalTimelineStream()
        stopUserTimelineStream()
    }

    fun startPublicTimelineStream() {
        Common.resetAuthInfo()?.let {
            publicStream =
                    MastodonClient(it).getPublicTimelineAsStream()
                            .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                parseTimelineStream(it)
                            }, Throwable::printStackTrace)
        }
    }

    fun stopPublicTimelineStream() {
        if (getCategory() == Category.Public && !(publicStream?.isDisposed ?: true)) publicStream?.dispose()
    }

    fun startUserTimelineStream() {
        Common.resetAuthInfo()?.let {
            userStream =
                    MastodonClient(it).getUserTimelineAsStream()
                            .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                parseTimelineStream(it)
                            }, Throwable::printStackTrace)
        }
    }

    fun stopUserTimelineStream() {
        if (getCategory() == Category.User && !(userStream?.isDisposed ?: true)) userStream?.dispose()
    }

    fun showUserTimeline(loadStream: Boolean = false, loadNext: Boolean = false) {
        val next = loadNext && nextId != null && (nextId?.compareTo(-1) ?: 0) == 1
        if (nextId != null) MastodonClient(Common.resetAuthInfo() ?: return).getUserTimeline(if (next) nextId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ result ->
                    if (next) adapter.addAllContentsInLast(result.response().body().map { Common.getTimelineContent(it) })
                    else adapter.addAllContents(result.response().body().map { Common.getTimelineContent(it) })
                    nextId = result.response().headers().get("Link")?.replace(Regex("^.*<https?://.+\\?max_id=(.+?)>.*"), "$1")?.toLong()

                    if (loadStream) startUserTimelineStream()
                }, Throwable::printStackTrace)
    }

    fun startLocalTimelineStream() {
        Common.resetAuthInfo()?.let {
            MastodonClient(it).getLocalTimelineAsStream()
                    .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({
                        parseTimelineStream(it)
                    }, Throwable::printStackTrace)
        }
    }

    fun stopLocalTimelineStream() {
        if (getCategory() == Category.Local && !(localStream?.isDisposed ?: true)) localStream?.dispose()
    }

    fun onAddItemToAdapter() {
        if (onTop && adapter.itemCount > 1) {
            binding.recyclerView.scrollToPosition(1)
            binding.recyclerView.smoothScrollToPosition(0)
        }
    }

    private fun parseTimelineStream(source: String) {
        if (source.startsWith("data: ")) {
            val data = source.replace(Regex("^data:\\s(.+)"), "$1")
            if (waitingContent) {
                val status = Gson().fromJson(data, Status::class.java)
                val content = Common.getTimelineContent(status)
                Log.d("parse", "body: ${status.content}")

                adapter.addContent(content)
                onAddItemToAdapter()
            }
            if (waitingNotification) {
                val notification = Gson().fromJson(data, Notification::class.java)
                if (notification.type == Notification.Companion.NotificationType.reblog.name) {
                    val status = notification.status
                    if (status != null) {
                        val content = Common.getTimelineContent(status, notification)
                        Log.d("parse", "body: ${status.content}")

                        adapter.addContent(content)
                        onAddItemToAdapter()
                    }
                }
            }
            if (waitingDeletedId) {
                adapter.removeContentByTootId(data.toLong())
            }
        } else {
            waitingContent = source == "event: update"
            waitingNotification = source == "event: notification"
            waitingDeletedId = source == "event: delete"
        }
    }
}