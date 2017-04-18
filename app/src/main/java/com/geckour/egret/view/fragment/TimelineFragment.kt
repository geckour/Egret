package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.databinding.FragmentTimelineBinding
import com.geckour.egret.model.AccessToken
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.TimelineFragmentAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.trello.rxlifecycle2.components.support.RxFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class TimelineFragment: RxFragment() { // TODO: Timelineを取得、RecyclerViewを使って表示

    companion object {
        val TAG = "timelineFragment"

        fun newInstance(): TimelineFragment {
            val fragment = TimelineFragment()
            return fragment
        }
    }

    lateinit var binding: FragmentTimelineBinding
    lateinit var adapter: TimelineFragmentAdapter

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_timeline, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TimelineFragmentAdapter()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        binding.recyclerView.adapter = adapter
    }

    fun getCurrentAccessToken(): AccessToken = OrmaProvider.db.selectFromAccessToken().orderBy("createdAt DESC").first()

    fun showPublicTimeline() {
        MastodonClient(getCurrentAccessToken().token).getPublicTimeline()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ statuses ->
                    val contents: ArrayList<TimelineContent> = ArrayList()
                    statuses.map {
                        val content = TimelineContent(it.account.avatarUrl, it.account.displayName, it.account.username, it.createdAt.time, it.content)
                        contents.add(content)
                    }
                    adapter.addAllContents(contents)
                })
    }
}