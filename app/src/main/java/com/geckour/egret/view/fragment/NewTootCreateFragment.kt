package com.geckour.egret.view.fragment

import android.app.AlertDialog
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.databinding.FragmentCreateNewTootBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.squareup.picasso.Picasso
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import android.content.Context.INPUT_METHOD_SERVICE
import android.view.inputmethod.InputMethodManager


class NewTootCreateFragment : BaseFragment() {

    lateinit var binding: FragmentCreateNewTootBinding

    companion object {
        val TAG = "createNewTootFragment"
        private val ARGS_KEY_CURRENT_TOKEN_ID = "currentTokenId"
        private val ARGS_KEY_POST_TOKEN_ID = "postTokenId"

        fun newInstance(currentTokenId: Long, postTokenId: Long = currentTokenId): NewTootCreateFragment {
            val fragment = NewTootCreateFragment()
            val args = Bundle()
            args.putLong(ARGS_KEY_CURRENT_TOKEN_ID, currentTokenId)
            args.putLong(ARGS_KEY_POST_TOKEN_ID, postTokenId)
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (activity as MainActivity).supportActionBar?.hide()
        ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.hide()
    }

    override fun onResume() {
        super.onResume()

        (activity as MainActivity).supportActionBar?.hide()
        ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.hide()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_create_new_toot, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val token = OrmaProvider.db.selectFromAccessToken().idEq(arguments.getLong(ARGS_KEY_POST_TOKEN_ID)).last()
        val domain = OrmaProvider.db.selectFromInstanceAuthInfo().idEq(token.instanceId).last().instance
        OrmaProvider.db.updateAccessToken().isCurrentEq(true).isCurrent(false).executeAsSingle()
                .flatMap { OrmaProvider.db.updateAccessToken().idEq(token.id).isCurrent(true).executeAsSingle() }
                .flatMap { MastodonClient(Common.resetAuthInfo() ?: throw IllegalArgumentException()).getSelfAccount() }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { account ->
                    val content = Common.getNewTootIdentifyContent(domain, token, account)
                    binding.content = content
                    Picasso.with(binding.icon.context).load(content.avatarUrl).into(binding.icon)
                }, Throwable::printStackTrace)

        binding.tootBody.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) showSoftKeyBoard()
        }
        binding.tootBody.requestFocusFromTouch()
    }

    fun showSoftKeyBoard() {
        ((activity as MainActivity).getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(binding.tootBody, InputMethodManager.SHOW_IMPLICIT)
    }
}