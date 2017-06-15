package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.UserSpecificApp
import com.geckour.egret.databinding.FragmentChoseInstanceBinding
import com.geckour.egret.model.InstanceAuthInfo
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.LoginActivity
import com.trello.rxlifecycle2.components.support.RxFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class AccessInstanceFragment: BaseFragment() {

    companion object {
        val TAG: String = this::class.java.simpleName

        fun newInstance(): AccessInstanceFragment {
            val fragment = AccessInstanceFragment()
            return fragment
        }
    }

    private lateinit var binding: FragmentChoseInstanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_chose_instance, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.button.setOnClickListener { attemptChoseInstance() }
    }

    fun attemptChoseInstance() {
        val domain = binding.editText.text.toString().replace(Regex("^https?://(.+)"), "$1")
        if (Patterns.WEB_URL.matcher(domain).matches()) requestResister(domain)
        else binding.editText.error = getString(R.string.error_incorrect_domain)
    }

    fun requestResister(domain: String) {
        (activity as LoginActivity).showProgress(true)

        MastodonClient(domain).registerApp()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { value ->
                    (activity as LoginActivity).showProgress(false)
                    storeInstanceInfo(domain, value)
                }, { throwable ->
                    (activity as LoginActivity).showProgress(false)
                    binding.editText.error = getString(R.string.error_incorrect_domain)
                    Timber.e(throwable)
                } )
    }

    fun storeInstanceInfo(domain: String, info: UserSpecificApp) {
        OrmaProvider.db.relationOfInstanceAuthInfo().upsertAsSingle(createInstanceAuthInfo(domain, info))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { value ->
                    Timber.d("$value")
                    (activity as LoginActivity).showAuthAppFragment()
                }, Throwable::printStackTrace )
    }

    fun createInstanceAuthInfo(domain: String, info: UserSpecificApp): InstanceAuthInfo {
        return InstanceAuthInfo(-1L, domain, info.id, info.clientId, info.clientSecret)
    }
}