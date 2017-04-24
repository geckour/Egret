package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.support.design.widget.FloatingActionButton
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Status
import com.geckour.egret.api.service.MastodonService
import com.geckour.egret.databinding.FragmentTimelineBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.google.gson.Gson
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class TimelineFragment: BaseFragment() {

    companion object {
        val TAG = "timelineFragment"
        val ARGS_KEY_CATEGORY = "category"
        val ARGS_VALUE_PUBLIC = "Public"
        val ARGS_VALUE_USER = "User"
        val ARGS_VALUE_HASH_TAG = "Hash tag"
        val STATE_ARGS_KEY_CONTENTS = "contents"

        fun newInstance(category: String): TimelineFragment {
            val fragment = TimelineFragment()
            val args = Bundle()
            args.putString(ARGS_KEY_CATEGORY, category)
            fragment.arguments = args

            return fragment
        }
    }

    lateinit private var binding: FragmentTimelineBinding
    lateinit private var adapter: TimelineAdapter
    private var onTop = true
    private var inTouch = false
    private val bundle = Bundle()

    private var waitingContent = false
    private var waitingDeletedId = false

    private var nextId: Long? = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

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

        binding.recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
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
                            ARGS_VALUE_PUBLIC -> {}
                            ARGS_VALUE_USER -> showUserTimeline(loadNext = true)
                            ARGS_VALUE_HASH_TAG -> {}
                        }
                    }
                }
            }
        })
        adapter = TimelineAdapter(object: TimelineAdapter.IListenr {
            override fun onClickIcon(accountId: Long) {
                AccountProfileFragment.newObservableInstance(accountId)
                        .subscribe( {
                            fragment ->
                            activity.supportFragmentManager.beginTransaction()
                                    .replace(R.id.container, fragment, AccountProfileFragment.TAG)
                                    .addToBackStack(AccountProfileFragment.TAG)
                                    .commit()
                        }, Throwable::printStackTrace)
            }

            override fun onFavStatus(statusId: Long, view: ImageView) {
                (activity as MainActivity).favStatusById(statusId, view)
            }

            override fun onBoostStatus(statusId: Long, view: ImageView) {
                (activity as MainActivity).boostStatusById(statusId, view)
            }
        })
        binding.recyclerView.adapter = adapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState == null) {
            when (arguments.getString(ARGS_KEY_CATEGORY)) {
                ARGS_VALUE_PUBLIC -> showPublicTimeline()
                ARGS_VALUE_USER -> showUserTimeline(true)
                ARGS_VALUE_HASH_TAG -> {}
            }
        } else {
            when (arguments.getString(ARGS_KEY_CATEGORY)) {
                ARGS_VALUE_PUBLIC -> showPublicTimeline()
                ARGS_VALUE_USER -> showUserTimelineAsStream()
                ARGS_VALUE_HASH_TAG -> {}
            }
        }
    }

    override fun onPause() {
        super.onPause()

        bundle.putParcelableArrayList(STATE_ARGS_KEY_CONTENTS, ArrayList(adapter.getContents()))
    }

    override fun onResume() {
        super.onResume()

        restoreTimeline(bundle)
        (activity as MainActivity).resetSelectionNavItem(when (getCategory()) {
            ARGS_VALUE_PUBLIC -> MainActivity.NAV_ITEM_TL_PUBLIC
            ARGS_VALUE_USER -> MainActivity.NAV_ITEM_TL_USER
            else -> -1
        })
    }

    fun getCategory(): String = arguments.getString(ARGS_KEY_CATEGORY) ?: "Unknown"

    fun restoreTimeline(savedInstanceState: Bundle?) {
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_ARGS_KEY_CONTENTS)) {
            adapter.clearContents()
            val parcelables: ArrayList<Parcelable> = savedInstanceState.getParcelableArrayList(STATE_ARGS_KEY_CONTENTS)
            adapter.addAllContents(parcelables.map { it as TimelineContent })
        }
    }

    fun showPublicTimeline() {
        MastodonClient(Common.resetAuthInfo() ?: return).getPublicTimelineAsStream()
                .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ source ->
                    Log.d("showPublicTimeline", "source: $source")

                    parseTimelineStream(source)
                }, Throwable::printStackTrace)
    }

    fun showUserTimelineAsStream() {
        MastodonClient(Common.resetAuthInfo() ?: return).getUserTimelineAsStream()
                .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ source ->
                    Log.d("showUserTimeline", "source: $source")

                    parseTimelineStream(source)
                }, Throwable::printStackTrace)
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

                    if (loadStream) showUserTimelineAsStream()
                }, Throwable::printStackTrace)
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
                Log.d("showPublicTimeline", "body: ${status.content}")

                adapter.addContent(content)
                onAddItemToAdapter()
            }
            if (waitingDeletedId) {
                adapter.removeContentByTootId(data.toLong())
            }
        } else {
            waitingContent = source == "event: update"
            waitingDeletedId = source == "event: delete"
        }
    }
}