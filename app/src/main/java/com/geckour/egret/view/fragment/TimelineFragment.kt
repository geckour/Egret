package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.support.design.widget.FloatingActionButton
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val STATE_ARGS_KEY_CONTENTS = "contents"

        fun newInstance(): TimelineFragment {
            val fragment = TimelineFragment()
            return fragment
        }
    }

    lateinit private var binding: FragmentTimelineBinding
    lateinit private var adapter: TimelineAdapter
    private var onTop = true
    private val bundle = Bundle()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_timeline, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val instanceId = OrmaProvider.db.selectFromAccessToken().isCurrentEq(true).last().instanceId
        (activity as MainActivity).supportActionBar?.show()
        (activity as MainActivity).supportActionBar?.title = "Public TL - ${OrmaProvider.db.selectFromInstanceAuthInfo().idEq(instanceId).last().instance}"
        (activity.findViewById(R.id.fab) as FloatingActionButton).setOnClickListener { showPublicTimeline() }

        binding.recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        binding.recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val scrollY: Int = recyclerView?.computeVerticalScrollOffset() ?: -1
                onTop = (onTop && dy == 0) || scrollY == 0 || scrollY == dy
            }
        })
        adapter = TimelineAdapter(object: TimelineAdapter.IListenr {
            override fun onClickIcon(accountId: Long) {
                AccountProfileFragment.newObservableInstance(accountId)
                        .subscribe( {
                            fragment ->
                            activity.supportFragmentManager.beginTransaction().replace(R.id.container, fragment).addToBackStack(null).commit()
                        }, Throwable::printStackTrace)
            }
        })
        binding.recyclerView.adapter = adapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        showPublicTimeline()
    }

    override fun onPause() {
        super.onPause()

        bundle.putParcelableArrayList(STATE_ARGS_KEY_CONTENTS, ArrayList(adapter.getContents()))
    }

    override fun onResume() {
        super.onResume()

        restoreTimeline(bundle)
    }

    fun restoreTimeline(savedInstanceState: Bundle?) {
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_ARGS_KEY_CONTENTS)) {
            val parcelables: ArrayList<Parcelable> = savedInstanceState.getParcelableArrayList(STATE_ARGS_KEY_CONTENTS)
            adapter.addAllContents(parcelables.map { parcelable -> parcelable as TimelineContent })
        }
    }

    fun showPublicTimeline() {
        var waitingContent = false
        var waitingDeletedId = false
        MastodonClient(Common.resetAuthInfo() ?: return).getPublicTimeline()
                .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ source ->
                    Log.d("showPublicTimeline", "source: $source")

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
                }, Throwable::printStackTrace)
    }

    fun onAddItemToAdapter() {
        if (onTop && adapter.itemCount > 1) {
            binding.recyclerView.scrollToPosition(1)
            binding.recyclerView.smoothScrollToPosition(0)
        }
    }
}