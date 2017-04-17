package com.geckour.egret.util

import android.util.Log
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
            val instanceAuthInfoList = OrmaProvider.db.selectFromInstanceAuthInfo().orderBy("createdAt DESC")
            if (!instanceAuthInfoList.isEmpty) {
                OkHttpProvider.authInterceptor.setToken(accessTokenList.first().token)

                MastodonClient(instanceAuthInfoList.first().instance).getAccount()
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ account ->
                            Log.d("hasCertified", "id: ${account.id}")
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