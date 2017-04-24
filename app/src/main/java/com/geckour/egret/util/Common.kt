package com.geckour.egret.util

import android.text.format.DateFormat
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Status
import com.geckour.egret.model.AccessToken
import com.geckour.egret.view.adapter.model.NewTootIndentifyContent
import com.geckour.egret.view.adapter.model.ProfileContent
import com.geckour.egret.view.adapter.model.TimelineContent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*

class Common {

    companion object {

        interface IListener {
            fun onCheckCertify(hasCertified: Boolean, userId: Long)
        }

        fun hasCertified(listener: IListener) {
            val domain = resetAuthInfo()
            if (domain != null) {
                MastodonClient(domain).getSelfAccount()
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ account ->
                            listener.onCheckCertify(true, account.id)
                        }, { throwable ->
                            Timber.e(throwable)
                            listener.onCheckCertify(false, -1)
                        })
            } else listener.onCheckCertify(false, -1)
        }

        fun getCurrentAccessToken(): AccessToken? {
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

        fun getNewTootIdentifyContent(domain: String, accessToken: AccessToken, account: Account): NewTootIndentifyContent = NewTootIndentifyContent(
                accessToken.id,
                account.id,
                account.avatarUrl,
                "@${account.username}@$domain"
        )

        fun getReadableDateString(time: Long, full: Boolean = false): String {
            val date = Date(time)
            val calThisYear = Calendar.getInstance()
            calThisYear.set(Calendar.DAY_OF_MONTH, 0)
            val calThisDay = Calendar.getInstance()
            calThisDay.set(Calendar.HOUR_OF_DAY, 0)
            val pattern = if (full) "yyyy/M/d k:mm:ss"
            else if (date.before(calThisYear.time)) "yyyy/M/d"
            else if (date.before(calThisDay.time)) "M/d kk:mm"
            else "k:mm:ss"

            return DateFormat.format(pattern, date).toString()
        }
    }
}