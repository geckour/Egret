package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.view.LayoutInflater
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

    companion object {
        val TAG: String = this::class.java.simpleName

        fun newInstance(): AccountBlockFragment {
            val fragment = AccountBlockFragment()

            return fragment
        }
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
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        bindSavedAccounts()
    }

    override fun onResume() {
        super.onResume()

        if (activity is MainActivity) ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.hide()
    }

    override fun onPause() {
        super.onPause()

        removeAccounts(adapter.getItems())
    }

    fun bindSavedAccounts() {
        adapter.clearItems()
        preItems.clear()
        Common.resetAuthInfo()?.let { domain ->
            MastodonClient(domain).getBlockedUsers()
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({
                        adapter.addAllItems(it)
                        preItems.addAll(it)
                    }, Throwable::printStackTrace)
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
}