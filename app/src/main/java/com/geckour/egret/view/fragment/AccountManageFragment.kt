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
import com.geckour.egret.model.AccessToken
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.LoginActivity
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.ManageAccountAdapter
import com.geckour.egret.view.adapter.model.AccountContent
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class AccountManageFragment: BaseFragment() {

    lateinit private var binding: FragmentManageAccountBinding
    private val adapter: ManageAccountAdapter by lazy { ManageAccountAdapter() }
    private val preItems: ArrayList<AccountContent> = ArrayList()

    companion object {
        val TAG: String = this::class.java.simpleName

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
                .map { token ->
                    MastodonClient(Common.setAuthInfo(token) ?: throw IllegalArgumentException()).getAccount(token.accountId)
                            .map {
                                val domain =  OrmaProvider.db.selectFromInstanceAuthInfo().idEq(token.instanceId).last().instance
                                AccountContent(token, "@${it.acct}@$domain", it.avatarUrl)
                            }
                }
                .subscribeOn(Schedulers.newThread())
                .compose(bindToLifecycle())
                .subscribe({
                    it.observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ content ->
                                adapter.addItem(content)
                                preItems.add(content)
                            }, Throwable::printStackTrace)
                }, Throwable::printStackTrace)
    }

    fun removeAccounts(items: List<AccountContent>, activity: Activity) {
        val shouldRemoveItems = preItems.filter { items.none { item -> it.token.id == item.token.id } }

        Observable.fromIterable(shouldRemoveItems)
                .flatMap {
                    Single.merge<Int>(
                            OrmaProvider.db.deleteFromInstanceAuthInfo().idEq(it.token.instanceId).executeAsSingle(),
                            OrmaProvider.db.deleteFromAccessToken().idEq(it.token.id).executeAsSingle()
                    )
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
