package com.geckour.egret.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.support.customtabs.CustomTabsIntent
import android.support.v4.content.ContextCompat
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.Html
import android.text.Spanned
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.widget.TextView
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Notification
import com.geckour.egret.api.model.Status
import com.geckour.egret.model.AccessToken
import com.geckour.egret.view.adapter.MuteKeywordAdapter
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
            fun onCheckCertify(hasCertified: Boolean, accountId: Long)
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

        fun setAuthInfo(accessToken: AccessToken): String? {
            val instanceInfo = OrmaProvider.db.selectFromInstanceAuthInfo().idEq(accessToken.instanceId).last() ?: return null
            OkHttpProvider.authInterceptor.setToken(accessToken.token)

            return instanceInfo.instance
        }

        fun getTimelineContent(status: Status, notification: Notification? = null): TimelineContent = TimelineContent(
                status.id,
                status.url,
                status.account.id,
                status.account.avatarUrl,
                status.account.displayName,
                "@${status.account.acct}",
                Date(status.createdAt.time),
                getSpannedWithoutExtraMarginFromHtml(status.content),
                status.tags.map { it.name },
                status.favourited,
                status.reblogged,
                status.favCount,
                status.reblogCount,
                if (status.reblog != null || notification != null)
                    getTimelineContent(notification?.status ?: status.reblog!!) else null,
                status.application?.name)

        fun getProfileContent(account: Account): ProfileContent = ProfileContent(
                account.avatarUrl,
                account.headerUrl,
                account.displayName,
                "@${account.acct}",
                getSpannedWithoutExtraMarginFromHtml("<a href=\"${account.url}\">${account.url}</a>"),
                getSpannedWithoutExtraMarginFromHtml(account.note),
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

        fun getSpannedWithoutExtraMarginFromHtml(html: String): Spanned {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            } else {
                val handler = Html.TagHandler { opening, tag, output, xmlReader ->
                    if (opening && tag == "p") {
                        output.clearSpans()
                    }
                }
                return Html.fromHtml(html, null, handler)
            }
        }

        fun getMutableLinkMovementMethodForCustomTab(context: Context) = MutableLinkMovementMethod(getOnUrlClickListenerForCustomTab(context))

        fun getOnUrlClickListenerForCustomTab(context: Context): MutableLinkMovementMethod.OnUrlClickListener {
            return object: MutableLinkMovementMethod.OnUrlClickListener {
                override fun onUrlClick(view: TextView?, uri: Uri) {
                    getCustomTabsIntent(context).launchUrl(context, uri)
                }
            }
        }

        fun getCustomTabsIntent(context: Context): CustomTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .build()

        fun isModeDefaultBrowser(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("switch_to_use_default_browser", false)

        fun getMovementMethodFromPreference(context: Context): MovementMethod {
            return if (isModeDefaultBrowser(context)) LinkMovementMethod.getInstance() else Common.getMutableLinkMovementMethodForCustomTab(context)
        }

        fun getSwipeToDismissTouchHelperForMuteKeyword(adapter: MuteKeywordAdapter): ItemTouchHelper = ItemTouchHelper(object: ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?): Int {
                return makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
            }

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?): Boolean {
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { adapter.removeItemsByIndex(it) }
            }
        })
    }
}