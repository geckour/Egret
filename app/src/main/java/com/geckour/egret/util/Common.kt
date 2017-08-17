package com.geckour.egret.util

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.support.customtabs.CustomTabsIntent
import android.support.v4.content.ContextCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.Html
import android.text.Spanned
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import com.emojione.Emojione
import com.geckour.egret.App
import com.geckour.egret.NotificationService
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Notification
import com.geckour.egret.api.model.Status
import com.geckour.egret.databinding.ContentMainBinding
import com.geckour.egret.model.AccessToken
import com.geckour.egret.view.adapter.*
import com.geckour.egret.view.adapter.model.NewTootIndentifyContent
import com.geckour.egret.view.adapter.model.ProfileContent
import com.geckour.egret.view.adapter.model.SearchResultAccount
import com.geckour.egret.view.adapter.model.TimelineContent
import com.geckour.egret.view.fragment.TimelineFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*

class Common {

    companion object {
        fun hasCertified(accessToken: AccessToken? = null, callback: (hasCertified: Boolean, accountId: Long) -> Any) {
            if (accessToken != null) {
                setAuthInfo(accessToken)?.let { requestWeatherCertified(it, callback) }
            } else {
                val domain = resetAuthInfo()
                
                if (domain == null) callback(false, -1)
                else requestWeatherCertified(domain, callback)
            }
        }

        private fun requestWeatherCertified(domain: String, callback: (hasCertified: Boolean, accountId: Long) -> Any) {
            MastodonClient(domain).getSelfAccount()
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ account ->
                        callback(true, account.id)
                    }, { throwable ->
                        Timber.e(throwable)
                        callback(false, -1)
                    })
        }

        fun getCurrentAccessToken(): AccessToken? {
            val accessTokens = OrmaProvider.db.selectFromAccessToken().isCurrentEq(true)
            return accessTokens?.lastOrNull()
        }

        fun getInstanceName(): String? {
            val accessToken = getCurrentAccessToken() ?: return null
            return OrmaProvider.db.selectFromInstanceAuthInfo().idEq(accessToken.instanceId).last()?.instance
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

        fun getTimelineContent(
                status: Status? = null,
                notification: Notification? = null,
                treeStatus: TimelineContent.TimelineStatus.TreeStatus = TimelineContent.TimelineStatus.TreeStatus.None
        ): TimelineContent =
                if (status != null) TimelineContent(
                        status = TimelineContent.TimelineStatus(
                                status.id,
                                status.url,
                                status.account.id,
                                status.account.avatarUrl,
                                Emojione.shortnameToUnicode(status.account.displayName),
                                "@${status.account.acct}",
                                Date(status.createdAt.time),
                                getSpannedWithoutExtraMarginFromHtml(Emojione.shortnameToUnicode(status.content)),
                                status.media.map { it.previewImgUrl },
                                status.media.map { it.url },
                                status.sensitive,
                                getSpannedWithoutExtraMarginFromHtml(status.spoilerText),
                                status.tags.map { it.name },
                                status.favourited,
                                status.reblogged,
                                status.favCount,
                                status.reblogCount,
                                if (status.reblog != null) getTimelineContent(status.reblog!!).status else null,
                                status.application?.name,
                                treeStatus
                        )
                )
                else if (notification != null) TimelineContent(
                        notification = notification.let {
                            TimelineContent.TimelineNotification(
                                    it.id,
                                    it.type,
                                    it.account.id,
                                    it.account.avatarUrl,
                                    it.account.displayName,
                                    "@${it.account.acct}",
                                    Date(it.createdAt.time),
                                    getTimelineContent(status = it.status).status
                            )
                        }
                )
                else TimelineContent()

        fun getProfileContent(account: Account): ProfileContent = ProfileContent(
                account.id,
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

        fun getSearchResultAccountContent(account: Account): SearchResultAccount = SearchResultAccount(
                account.id,
                account.avatarUrl,
                account.displayName,
                "@${account.acct}",
                account.isLocked,
                account.createdAt
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
            val cleanedHtml = removeWhiteSpacesAtLastFromHtmlString(html)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return Html.fromHtml(cleanedHtml, Html.FROM_HTML_MODE_COMPACT)
            } else {
                val handler = Html.TagHandler { opening, tag, output, xmlReader ->
                    if (opening && tag == "p") {
                        output.clearSpans()
                    }
                }
                return Html.fromHtml(cleanedHtml, null, handler)
            }
        }

        fun removeWhiteSpacesAtLastFromHtmlString(html: String): String {
            return html.replace(Regex("<p>(.+)</p>", RegexOption.MULTILINE), "$1\n")
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

        fun isModeShowTootBar(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("switch_to_show_extreme_toot_bar", false)

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

        fun getSwipeToDismissTouchHelperForMuteAccount(adapter: MuteAccountAdapter): ItemTouchHelper = ItemTouchHelper(object: ItemTouchHelper.Callback() {
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

        fun getSwipeToDismissTouchHelperForMuteHashTag(adapter: MuteHashTagAdapter): ItemTouchHelper = ItemTouchHelper(object: ItemTouchHelper.Callback() {
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

        fun getSwipeToDismissTouchHelperForMuteInstance(adapter: MuteInstanceAdapter): ItemTouchHelper = ItemTouchHelper(object: ItemTouchHelper.Callback() {
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

        fun getSwipeToDismissTouchHelperForBlockAccount(adapter: BlockAccountAdapter): ItemTouchHelper = ItemTouchHelper(object: ItemTouchHelper.Callback() {
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

        fun getSwipeToDismissTouchHelperForManageAccount(adapter: ManageAccountAdapter): ItemTouchHelper = ItemTouchHelper(object: ItemTouchHelper.Callback() {
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

        fun getStoreContentsKey(category: TimelineFragment.Category) = "${getInstanceName()}:${getCurrentAccessToken()?.accountId}:${TimelineFragment.STATE_ARGS_KEY_CONTENTS}:${category.name}"

        fun showSoftKeyBoardOnFocusEditText(et: EditText, hideOnUnFocus: Boolean = true) {
            et.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                else if (hideOnUnFocus) (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
            }
            et.requestFocusFromTouch()
            et.requestFocus()
        }

        fun hideSoftKeyBoard(et: EditText) = (et.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(et.windowToken, 0)

        fun setSimplicityPostBarVisibility(contentMainBinding: ContentMainBinding, isVisible: Boolean) {
            contentMainBinding.simplicityPostWrap.visibility = if (isVisible) View.VISIBLE else View.GONE
        }


        fun toggleRefreshIndicatorState(indicator: SwipeRefreshLayout, show: Boolean) {
            indicator.isRefreshing = show
        }

        fun toggleRefreshIndicatorActivity(indicator: SwipeRefreshLayout, activity: Boolean) {
            indicator.isEnabled = activity
        }

        fun getRegexExtractSinceId() = Regex(".*since_id=(\\d+?)>.*")

        fun getRegexExtractMaxId() = Regex(".*max_id=(\\d+?)>.*")

        fun getSinceIdFromLinkString(link: String?): Long = link?.let {
            if (link.contains("max_id")) {
                try {
                    link.replace(getRegexExtractSinceId(), "$1").toLong()
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                    -1L
                }
            } else -1L
        } ?: -1L

        fun getMaxIdFromLinkString(link: String?): Long = link?.let {
            if (link.contains("max_id")) {
                try {
                    link.replace(getRegexExtractMaxId(), "$1").toLong()
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                    -1L
                }
            } else -1L
        } ?: -1L

        fun dp(context: Context, pixel: Float): Float = pixel * context.resources.displayMetrics.density

        fun resetNotificationService(activity: Activity) {
            val intent = (activity.application as App).intent
            activity.apply {
                stopService(intent)
                startService(intent)
            }
        }
    }
}