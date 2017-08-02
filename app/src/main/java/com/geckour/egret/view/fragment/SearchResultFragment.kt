package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.model.Result
import com.geckour.egret.databinding.FragmentTimelineBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.SearchResultAdapter

class SearchResultFragment: BaseFragment() {

    enum class Category {
        All,
        Account,
        Status,
        HashTag
    }

    companion object {
        val TAG = "searchResultFragment"
        private val ARGS_KEY_CATEGORY = "category"
        private val ARGS_KEY_QUERY = "query"
        private val ARGS_KEY_RESULT = "result"

        fun newInstance(category: Category = Category.All, query: String, result: Result): SearchResultFragment = SearchResultFragment().apply {
            arguments = Bundle().apply {
                putInt(ARGS_KEY_CATEGORY, category.ordinal)
                putString(ARGS_KEY_QUERY, query)
                putSerializable(ARGS_KEY_RESULT, result)
            }
        }
    }

    lateinit private var binding: FragmentTimelineBinding
    private val adapter: SearchResultAdapter by lazy { SearchResultAdapter((activity as MainActivity).timelineListener) }
    private val query: String by lazy { arguments.getString(ARGS_KEY_QUERY) }
    private val result: Result by lazy { arguments.getSerializable(ARGS_KEY_RESULT) as Result }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_timeline, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.apply {
            setColorSchemeResources(R.color.colorAccent)
            setOnRefreshListener {
                toggleRefreshIndicatorState(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        refreshBarTitle()
        showResult()
    }

    fun showResult() {
        adapter.resetContentsByResult(result)
    }

    fun toggleRefreshIndicatorState(show: Boolean) = Common.toggleRefreshIndicatorState(binding.swipeRefreshLayout, show)

    fun refreshBarTitle() {
        (activity as MainActivity).supportActionBar?.title = "Search result: $query"
    }
}