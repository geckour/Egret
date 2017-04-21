package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.databinding.FragmentAccountProfileBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.squareup.picasso.Picasso
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class AccountProfileFragment: BaseFragment() {

    companion object {
        val ARGS_KEY_ACCOUNT = "account"

        fun newInstance(account: Account): AccountProfileFragment {
            val fragment = AccountProfileFragment()
            val args = Bundle()
            args.putSerializable(ARGS_KEY_ACCOUNT, account)
            fragment.arguments = args

            return fragment
        }

        fun newObservableInstance(accountId: Long): Single<AccountProfileFragment> {
            return MastodonClient(Common.resetAuthInfo() ?: throw IllegalArgumentException()).getAccount(accountId)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap { account ->
                        val fragment = newInstance(account)
                        Single.just(fragment)
                    }
        }
    }

    lateinit var account: Account
    lateinit var binding: FragmentAccountProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = arguments[ARGS_KEY_ACCOUNT] as Account
        (activity as MainActivity).supportActionBar?.hide()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_account_profile, container, false)
        val content = Common.getProfileContent(account)
        binding.content = content
        binding.timeString = account.createdAt.toString()
        Picasso.with(binding.icon.context).load(content.iconUrl).into(binding.icon)
        Picasso.with(binding.header.context).load(content.headerUrl).into(binding.header)

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}