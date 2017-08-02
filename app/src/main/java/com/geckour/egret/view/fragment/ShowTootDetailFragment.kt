package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Context
import com.geckour.egret.api.model.Status
import com.geckour.egret.databinding.FragmentTimelineBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class ShowTootDetailFragment : BaseFragment() {

    companion object {
        val TAG = "showTootDetailFragment"
        val ARGS_KEY_STATUS_ID = "statusId"

        fun newInstance(statusId: Long): ShowTootDetailFragment = ShowTootDetailFragment().apply {
            arguments = Bundle().apply {
                putLong(ARGS_KEY_STATUS_ID, statusId)
            }
        }
    }

    private val statusId: Long by lazy { arguments.getLong(ARGS_KEY_STATUS_ID, -1L) }
    lateinit private var binding: FragmentTimelineBinding
    private val adapter: TimelineAdapter by lazy { TimelineAdapter((activity as MainActivity).timelineListener) }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_timeline, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()

        refreshBarTitle()
        showContext()
    }

    fun refreshBarTitle() {
        (activity as MainActivity).supportActionBar?.title = "Reaction of the Toot"
    }

    fun showContext() {
        Common.resetAuthInfo()?.let { domain ->
            MastodonClient(domain).getStatusByStatusId(statusId)
                    .flatMap { status ->
                        MastodonClient(domain).getContextOfStatus(statusId)
                                .map { Pair(status, it) }
                    }
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({ (status, statusContext) ->
                        processStatusContext(status, statusContext)
                    }, Throwable::printStackTrace)
        }
    }

    fun processStatusContext(rootStatus: Status, statusContext: Context) {
        if (statusContext.ancestors.isEmpty() && statusContext.descendants.isEmpty()) {
            adapter.addContent(Common.getTimelineContent(status = rootStatus))
        } else {
            val contents: ArrayList<TimelineContent> = ArrayList()

            contents.addAll(statusContext.ancestors.map { Common.getTimelineContent(status = it, treeStatus = TimelineContent.TimelineStatus.TreeStatus.Filling) })
            contents.add(Common.getTimelineContent(status = rootStatus, treeStatus = TimelineContent.TimelineStatus.TreeStatus.Filling))
            contents.addAll(statusContext.descendants.map { Common.getTimelineContent(status = it, treeStatus = TimelineContent.TimelineStatus.TreeStatus.Filling) })

            contents.first().status?.treeStatus = TimelineContent.TimelineStatus.TreeStatus.Top
            contents.last().status?.treeStatus = TimelineContent.TimelineStatus.TreeStatus.Bottom

            adapter.addAllContents(contents)
        }
    }
}