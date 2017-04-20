package com.geckour.egret.util

import android.util.Log
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.model.AccessToken
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class Common {

    interface IListener {
        fun onCheckCertify(hasCertified: Boolean)
    }

    fun hasCertified(listener: IListener) {
        val domain = resetAuthInfo()
        if (domain != null) {
                MastodonClient(domain).getAccount()
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ account ->
                            Log.d("hasCertified", "id: ${account.id}")
                            listener.onCheckCertify(true)
                        }, { throwable ->
                            Timber.e(throwable)
                            listener.onCheckCertify(false)
                        })
        } else listener.onCheckCertify(false)
    }

    private fun getCurrentAccessToken(): AccessToken? {
        val accessTokens = OrmaProvider.db.selectFromAccessToken().isCurrentEq(true)
        return if (accessTokens == null || accessTokens.isEmpty) null else accessTokens.last()
    }

    fun resetAuthInfo(): String? {
        val accessToken = getCurrentAccessToken() ?: return null
        val instanceInfo = OrmaProvider.db.selectFromInstanceAuthInfo().idEq(accessToken.instanceId).last() ?: return null
        OkHttpProvider.authInterceptor.setToken(accessToken.token)

        return instanceInfo.instance
    }
}