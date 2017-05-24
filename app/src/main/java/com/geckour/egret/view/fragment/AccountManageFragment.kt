package com.geckour.egret.view.fragment

import android.app.Activity
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.databinding.FragmentManageAccountBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.LoginActivity
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.ManageAccountAdapter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class AccountManageFragment: BaseFragment() {

    lateinit private var binding: FragmentManageAccountBinding
    lateinit private var adapter: ManageAccountAdapter
    private val preItems: ArrayList<Account> = ArrayList()

    companion object {
        val TAG = "accountManageFragment"

        fun newInstance(): AccountManageFragment {
            val fragment = AccountManageFragment()

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_manage_account, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ManageAccountAdapter()
        val helper = Common.getSwipeToDismissTouchHelperForManageAccount(adapter)
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

        removeAccounts(adapter.getItems(), activity)
    }

    fun bindSavedAccounts() {
        adapter.clearItems()
        preItems.clear()

        Observable.fromIterable(OrmaProvider.db.selectFromAccessToken())
                .flatMap { token ->
                    MastodonClient(Common.setAuthInfo(token) ?: throw IllegalArgumentException()).getAccount(token.accountId)
                            .map {
                                val domain =  OrmaProvider.db.selectFromInstanceAuthInfo().idEq(token.instanceId).last().instance
                                it.apply { it.acct = "${it.acct}@$domain" }
                            }
                }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    adapter.addItem(it)
                    preItems.add(it)
                }, Throwable::printStackTrace)
    }

    fun removeAccounts(items: List<Account>, activity: Activity) {
        val shouldRemoveItems = preItems.filter { items.none { item -> it.id == item.id } }

        Observable.fromIterable(shouldRemoveItems)
                .map { OrmaProvider.db.selectFromAccessToken().accountIdEq(it.id).last() }
                .flatMap { accessToken ->
                    OrmaProvider.db.deleteFromInstanceAuthInfo().idEq(accessToken.instanceId).executeAsSingle()
                            .flatMap { OrmaProvider.db.deleteFromAccessToken().idEq(accessToken.id).executeAsSingle() }
                            .toObservable()
                }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { Timber.d("an account deleted: $it") },
                        Throwable::printStackTrace,
                        { if (items.isEmpty()) showLoginActivity(activity) })
    }

    fun showLoginActivity(activity: Activity) {
        val intent = LoginActivity.getIntent(activity)
        activity.startActivity(intent)
    }
}
