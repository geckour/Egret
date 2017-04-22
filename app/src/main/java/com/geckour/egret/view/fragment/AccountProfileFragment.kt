package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.Parcelable
import android.support.v7.widget.DividerItemDecoration
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.databinding.FragmentAccountProfileBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class AccountProfileFragment: BaseFragment() {

    companion object {
        val TAG = "accountProfileFragment"
        val ARGS_KEY_ACCOUNT = "account"

        fun newInstance(account: Account): AccountProfileFragment {
            val fragment = AccountProfileFragment()
            val args = Bundle()
            args.putSerializable(ARGS_KEY_ACCOUNT, account)
            fragment.arguments = args

            return fragment
        }

        fun newObservableInstance(accountId: Long): Observable<AccountProfileFragment> {
            return MastodonClient(Common.resetAuthInfo() ?: throw IllegalArgumentException()).getAccount(accountId)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap { account ->
                        val fragment = newInstance(account)
                        Observable.just(fragment)
                    }
        }
    }

    lateinit var account: Account
    lateinit var binding: FragmentAccountProfileBinding
    lateinit var adapter: TimelineAdapter
    private val bundle = Bundle()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = arguments[ARGS_KEY_ACCOUNT] as Account
        (activity as MainActivity).supportActionBar?.hide()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_account_profile, container, false)

        val content = Common.getProfileContent(account)

        binding.content = content
        binding.timeString = content.createdAt.toString()
        Picasso.with(binding.icon.context).load(content.iconUrl).into(binding.icon)
        Picasso.with(binding.header.context).load(content.headerUrl).into(binding.header)

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.url.movementMethod = LinkMovementMethod.getInstance()
        binding.note.movementMethod = LinkMovementMethod.getInstance()

        binding.recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        adapter = TimelineAdapter(object: TimelineAdapter.IListenr {
            override fun onClickIcon(accountId: Long) {
                newObservableInstance(accountId)
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

        showToots()
    }

    fun showToots() {
        MastodonClient(Common.resetAuthInfo() ?: return).getAccountAllToots(account.id)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { values ->
                    adapter.addAllContents(values.map { status -> Common.getTimelineContent(status) }, -1)
                }, Throwable::printStackTrace)
    }

    override fun onPause() {
        super.onPause()

        bundle.putParcelableArrayList(TimelineFragment.STATE_ARGS_KEY_CONTENTS, ArrayList(adapter.getContents()))
    }

    override fun onResume() {
        super.onResume()

        restoreTimeline(bundle)
    }

    fun restoreTimeline(savedInstanceState: Bundle?) {
        if (savedInstanceState != null && savedInstanceState.containsKey(TimelineFragment.STATE_ARGS_KEY_CONTENTS)) {
            val parcelables: ArrayList<Parcelable> = savedInstanceState.getParcelableArrayList(TimelineFragment.STATE_ARGS_KEY_CONTENTS)
            adapter.addAllContents(parcelables.map { parcelable -> parcelable as TimelineContent })
        }
    }
}