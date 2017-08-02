package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.databinding.FragmentBlockAccountBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.BlockAccountAdapter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class AccountBlockFragment: BaseFragment() {

    lateinit private var binding: FragmentBlockAccountBinding
    lateinit private var adapter: BlockAccountAdapter
    private val preItems: ArrayList<Account> = ArrayList()

    private var onTop = true
    private var inTouch = false
    private var maxId: Long = -1
    private var sinceId: Long = -1

    companion object {
        val TAG: String = this::class.java.simpleName

        fun newInstance(): AccountBlockFragment = AccountBlockFragment().apply {  }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_block_account, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BlockAccountAdapter()
        val helper = Common.getSwipeToDismissTouchHelperForBlockAccount(adapter)
        helper.attachToRecyclerView(binding.recyclerView)
        binding.recyclerView.addItemDecoration(helper)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setOnTouchListener { _, event ->
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
                        bindAccounts(true)
                    }
                }
            }
        })
        binding.swipeRefreshLayout.apply {
            setColorSchemeResources(R.color.colorAccent)
            setOnRefreshListener {
                bindAccounts()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        bindAccounts()
    }

    override fun onResume() {
        super.onResume()

        if (activity is MainActivity) ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.hide()
    }

    override fun onPause() {
        super.onPause()

        removeAccounts(adapter.getItems())
    }

    fun bindAccounts(loadNext: Boolean = false) {
        if (loadNext && maxId == -1L) return

        Common.resetAuthInfo()?.let { domain ->
            MastodonClient(domain).getBlockedUsersWithHeaders(maxId = if (loadNext) maxId else null, sinceId = if (!loadNext && sinceId != -1L) sinceId else null)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({
                        it.response()?.let {
                            adapter.addAllItems(it.body())
                            preItems.addAll(it.body())

                            it.headers().get("Link")?.let {
                                maxId = Common.getMaxIdFromLinkString(it)
                                sinceId = Common.getSinceIdFromLinkString(it)
                            }

                            toggleRefreshIndicatorState(false)
                        }
                    }, { throwable ->
                        throwable.printStackTrace()
                        toggleRefreshIndicatorState(false)
                    })
        }
    }

    fun removeAccounts(items: List<Account>) {
        val shouldRemoveItems = preItems.filter { items.none { item -> it.id == item.id } }

        Common.resetAuthInfo()?.let { domain ->
            shouldRemoveItems.forEach {
                MastodonClient(domain).unBlockAccount(it.id)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ Timber.d("unblocked account: ${it.accountId}") }, Throwable::printStackTrace)
            }
            // Snackbar.make(binding.root, "hoge", Snackbar.LENGTH_SHORT).show()
        }
    }

    fun toggleRefreshIndicatorState(show: Boolean) = Common.toggleRefreshIndicatorState(binding.swipeRefreshLayout, show)
}