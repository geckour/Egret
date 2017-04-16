package com.geckour.egret.util

import com.geckour.egret.api.MastodonClient
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class Common {

    interface IListener {
        fun onCheckCertify(hasCertified: Boolean)
    }

    fun hasCertified(listener: IListener) {
        val accessTokenList = OrmaProvider.db.selectFromAccessToken()
        if (!accessTokenList.isEmpty) {
            val instanceAuthInfoList = OrmaProvider.db.selectFromInstanceAuthInfo()
            if (!instanceAuthInfoList.isEmpty) {
                OkHttpProvider.authInterceptor.setToken(accessTokenList.last().token)

                MastodonClient(instanceAuthInfoList.last().instance).getAccount()
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ account ->
                            Timber.d("id: ${account.id}")
                            listener.onCheckCertify(true)
                        }, { throwable ->
                            Timber.e(throwable)
                            listener.onCheckCertify(false)
                        })
            } else {
                listener.onCheckCertify(false)
            }
        } else listener.onCheckCertify(false)
    }
}