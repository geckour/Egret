package com.geckour.egret.util

import android.util.Log
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Status
import com.geckour.egret.model.AccessToken
import com.geckour.egret.view.adapter.model.ProfileContent
import com.geckour.egret.view.adapter.model.TimelineContent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class Common {

    companion object {

        interface IListener {
            fun onCheckCertify(hasCertified: Boolean)
        }

        fun hasCertified(listener: IListener) {
            val domain = resetAuthInfo()
            if (domain != null) {
                MastodonClient(domain).getSelfInfo()
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

        fun getTimelineContent(status: Status): TimelineContent = TimelineContent(
                status.id,
                status.account.id,
                status.account.avatarUrl,
                status.account.displayName,
                "@${status.account.acct}",
                status.createdAt.time,
                status.content)

        fun getProfileContent(account: Account): ProfileContent = ProfileContent(
                account.avatarUrl,
                account.headerUrl,
                account.displayName,
                "@${account.acct}",
                "<a href=\"${account.url}\">${account.url}</a>",
                account.note,
                account.followingCount,
                account.followersCount,
                account.statusesCount,
                account.createdAt.time)
    }
}