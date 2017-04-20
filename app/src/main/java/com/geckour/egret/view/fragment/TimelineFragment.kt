package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
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
import com.geckour.egret.view.adapter.TimelineFragmentAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class TimelineFragment: BaseFragment() {

    companion object {
        val TAG = "timelineFragment"

        fun newInstance(): TimelineFragment {
            val fragment = TimelineFragment()
            return fragment
        }
    }

    lateinit private var binding: FragmentTimelineBinding
    lateinit private var adapter: TimelineFragmentAdapter

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
        adapter = TimelineFragmentAdapter(object: TimelineFragmentAdapter.IListenr {
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

    fun showPublicTimeline() {
        MastodonClient(Common().resetAuthInfo() ?: return).getPublicTimeline()
                .flatMap { responceBody -> MastodonService.events(responceBody.source()) }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ source ->
                    Log.d("showPublicTimeline", "source: $source")

                    if (source.startsWith("data: ")) {
                        val data = source.replace(Regex("^data:\\s(.+)"), "$1")
                        try {
                            val status = Gson().fromJson(data, Status::class.java)
                            val content = TimelineContent(status.account.id, status.account.avatarUrl, status.account.displayName, status.account.acct, status.createdAt.time, status.content)
                            Log.d("showPublicTimeline", "body: ${status.content}")

                            adapter.addContent(content)
                            if ((binding.recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition() == 0) binding.recyclerView.smoothScrollToPosition(0)
                        } catch (e: JsonSyntaxException) {
                            Log.e("showPublicTimeline", e.message)
                        }
                    }
                }, Throwable::printStackTrace)
    }
}