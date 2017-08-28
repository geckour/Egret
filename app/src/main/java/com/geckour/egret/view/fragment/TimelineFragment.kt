package com.geckour.egret.view.fragment

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.net.ConnectivityManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import com.geckour.egret.App.Companion.STATE_KEY_CATEGORY
import com.geckour.egret.App.Companion.gson
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Notification
import com.geckour.egret.api.model.Status
import com.geckour.egret.api.service.MastodonService
import com.geckour.egret.databinding.ContentMainBinding
import com.geckour.egret.databinding.FragmentTimelineBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.Common.Companion.getMaxIdFromLinkString
import com.geckour.egret.util.Common.Companion.getSinceIdFromLinkString
import com.geckour.egret.util.Common.Companion.getStoreContentsKey
import com.geckour.egret.util.Common.Companion.hideSoftKeyBoard
import com.geckour.egret.util.Common.Companion.setSimplicityPostBarVisibility
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.google.gson.reflect.TypeToken
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import retrofit2.adapter.rxjava2.Result

class TimelineFragment: BaseFragment() {

    enum class Category {
        Public,
        Local,
        User,
        HashTag,
        Notification,
        Fav,
        Unknown
    }

    companion object {
        val TAG: String = this::class.java.simpleName
        val ARGS_KEY_CATEGORY = "category"
        val ARGS_KEY_HASH_TAG = "hashTag"
        val STATE_ARGS_KEY_CONTENTS = "contents"
        val REQUEST_CODE_GRANT_ACCESS_WIFI = 100

        fun newInstance(category: Category, hashTag: String? = null): TimelineFragment = TimelineFragment().apply {
            arguments = Bundle().apply {
                putString(ARGS_KEY_CATEGORY, category.name)
                hashTag?.let { putString(ARGS_KEY_HASH_TAG, hashTag) }
            }
        }
    }

    lateinit private var binding: FragmentTimelineBinding
    private val adapter: TimelineAdapter by lazy {
        TimelineAdapter(
                (activity as MainActivity).timelineListener,
                object: TimelineAdapter.OnAddTootCallback {
                    override fun onAddOnTop() { onAddItemToAdapter() }
                })
    }
    private val sharedPref: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(activity) }
    private var onTop = true
    private var inTouch = false

    private var publicStream: Disposable? = null
    private var localStream: Disposable? = null
    private var userStream: Disposable? = null
    private var notificationStream: Disposable? = null
    private var hashTagStream: Disposable? = null

    private var waitingContent = false
    private var waitingNotification = false
    private var waitingDeletedId = false

    private var maxId: Long = -1
    private var sinceId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_timeline, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val category = getCategory()
        sharedPref.edit()
                .putInt(STATE_KEY_CATEGORY, category.ordinal)
                .apply()

        binding.recyclerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    inTouch = true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    inTouch = false
                }
            }
            false
        }
        binding.recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val scrollY: Int = recyclerView?.computeVerticalScrollOffset() ?: -1
                onTop = scrollY == 0 || onTop && !(inTouch && scrollY > 0)

                if (!onTop) {
                    val y = scrollY + (recyclerView?.height ?: -1)
                    val h = recyclerView?.computeVerticalScrollRange() ?: -1
                    if (y == h) {
                        when (getCategory()) {
                            Category.Public ->showPublicTimeline(true)
                            Category.Local -> showLocalTimeline(true)
                            Category.User -> showUserTimeline(loadPrev = true)
                            Category.Notification -> showNotificationTimeline(loadPrev = true)
                            Category.HashTag -> getHashTag()?.let { showHashTagTimeline(it, loadPrev = true) }
                            Category.Fav -> showFavouriteTimeline(true)
                            Category.Unknown -> {}
                        }
                    }
                }
            }
        })
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.apply {
            setColorSchemeResources(R.color.colorAccent)
            setOnRefreshListener {
                if (existsNoRunningStream()) showTimelineByCategory(getCategory())
                else toggleRefreshIndicatorState(false)
            }
        }

        (activity as MainActivity).binding.appBarMain.contentMain.apply {
            buttonSimplicityToot.setOnClickListener { postToot(this) }
        }
    }

    override fun onPause() {
        super.onPause()

        stopTimelineStreams()
        forceStopRefreshing()

        sharedPref.edit()
                .apply {
                    if (getCategory() != Category.HashTag) {
                        val json = gson.toJson(adapter.getContents())
                        val storedContentsKey = getStoreContentsKey(getCategory()).apply { Log.d("onPause", "storeContentsKey: $this") }
                        putString(storedContentsKey, json)
                    } else {
                        getHashTag()?.let { putString(ARGS_KEY_HASH_TAG, it) }
                    }
                }
                .apply()

        sinceId = -1L
        maxId = -1L
    }

    override fun onResume() {
        super.onResume()

        (activity as MainActivity).binding.appBarMain.toolbar.setOnClickListener { scrollToTop() }

        (activity as MainActivity).supportActionBar?.show()
        refreshBarTitle()

        setSimplicityPostBarVisibility((activity as MainActivity).binding.appBarMain.contentMain, Common.isModeShowTootBar(activity))

        restoreTimeline()

        reflectCategorySelection()
    }

    fun getCategory(): Category =
            if (arguments != null && arguments.containsKey(ARGS_KEY_CATEGORY)) Category.valueOf(arguments.getString(ARGS_KEY_CATEGORY, "Unknown"))
            else Category.Unknown

    fun getHashTag(): String? =
            if (arguments != null && arguments.containsKey(ARGS_KEY_HASH_TAG)) arguments.getString(ARGS_KEY_HASH_TAG)
            else if (sharedPref.contains(ARGS_KEY_HASH_TAG)) sharedPref.getString(ARGS_KEY_HASH_TAG, "")
            else null

    private fun existsNoRunningStream() = listOf(publicStream, localStream, userStream).none { !(it?.isDisposed ?: true) }

    private fun refreshBarTitle() {
        val instanceId = Common.getCurrentAccessToken()?.instanceId
        val domain = if (instanceId == null) "not logged in" else OrmaProvider.db.selectFromInstanceAuthInfo().idEq(instanceId).last().instance
        val category = getCategory()
        (activity as MainActivity).supportActionBar?.title =
                when (category) {
                    Category.HashTag -> "$category TL${getHashTag()?.let { ": #$it" } ?: ""} - $domain"

                    Category.Fav -> "Your favourited toots list - $domain"

                    else -> "$category TL - $domain"
                }
    }

    private fun restoreTimeline() {
        adapter.clearContents()

        val storeContentsKey = getStoreContentsKey(getCategory()).apply { Log.d("restoreTimeline", "storeContentsKey: $this") }
        if (sharedPref.contains(storeContentsKey)) {
            val type = object: TypeToken<List<TimelineContent>>() {}
            val contents: List<TimelineContent> = gson.fromJson(sharedPref.getString(storeContentsKey, ""), type.type)
            Log.d("restoreTimeline", "contents.size: ${contents.size}")
            adapter.addAllContents(contents)
        }

        if (getCategory() == Category.Notification) {
            adapter.getFirstContent()?.notification?.id?.let { sinceId = it }
            adapter.getLastContent()?.notification?.id?.let { maxId = it }
        } else {
            adapter.getFirstContent()?.status?.id?.let { sinceId = it }
            adapter.getLastContent()?.status?.id?.let { maxId = it }
        }

        showTimelineByCategory(getCategory())

        sharedPref.edit()
                .remove(storeContentsKey)
                .apply()
    }

    private fun reflectCategorySelection() {
        (activity as MainActivity).resetSelectionNavItem(
                when (getCategory()) {
                    Category.Public -> MainActivity.NavItem.NAV_ITEM_TL_PUBLIC.ordinal.toLong()
                    Category.Local -> MainActivity.NavItem.NAV_ITEM_TL_LOCAL.ordinal.toLong()
                    Category.User -> MainActivity.NavItem.NAV_ITEM_TL_USER.ordinal.toLong()
                    Category.Notification -> MainActivity.NavItem.NAV_ITEM_TL_NOTIFICATION.ordinal.toLong()
                    else -> -1
                })
    }

    private fun toggleRefreshIndicatorState(show: Boolean) = Common.toggleRefreshIndicatorState(binding.swipeRefreshLayout, show)

    private fun toggleRefreshIndicatorActivity(show: Boolean) = Common.toggleRefreshIndicatorActivity(binding.swipeRefreshLayout, show)

    private fun forceStopRefreshing() {
        toggleRefreshIndicatorState(false)
        binding.swipeRefreshLayout.destroyDrawingCache()
        binding.swipeRefreshLayout.clearAnimation()
    }

    private fun showTimelineByCategory(category: Category) {
        val prefStream = sharedPref.getString("manage_stream", "1")

        if (prefStream == "1") {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_NETWORK_STATE)
                    != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_WIFI_STATE)
                            != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE), REQUEST_CODE_GRANT_ACCESS_WIFI)
            }
        }
        val activeNetworkInfo = (
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_WIFI_STATE)
                        == PackageManager.PERMISSION_GRANTED) (activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                else null)?.activeNetworkInfo

        toggleRefreshIndicatorState(true)
        if (prefStream == "0" ||
                (prefStream == "1" &&
                        activeNetworkInfo != null &&
                        activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI)) {
            when (category) {
                Category.Public -> showPublicTimeline(true)
                Category.Local -> showLocalTimeline(true)
                Category.User -> showUserTimeline(true)
                Category.HashTag -> getHashTag()?.let { showHashTagTimeline(it, true) }
                Category.Notification -> showNotificationTimeline(true)
                Category.Fav -> showFavouriteTimeline()
                Category.Unknown -> {}

            }
        } else {
            when (category) {
                Category.Public -> showPublicTimeline()
                Category.Local -> showLocalTimeline()
                Category.User -> showUserTimeline()
                Category.HashTag -> getHashTag()?.let { showHashTagTimeline(it) }
                Category.Notification -> showNotificationTimeline()
                Category.Fav -> showFavouriteTimeline()
                Category.Unknown -> {}
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_GRANT_ACCESS_WIFI -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    showTimelineByCategory(getCategory())
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_wifi_grant, Snackbar.LENGTH_SHORT)
                }
            }
        }
    }

    private fun postToot(contentMainBinding: ContentMainBinding) {
        val button = contentMainBinding.buttonSimplicityToot.apply { isEnabled = false }
        val body = contentMainBinding.simplicityTootBody.text.toString()
        if (body.isBlank()) {
            Snackbar.make(binding.root, R.string.error_empty_toot, Snackbar.LENGTH_SHORT).show()
            return
        }

        MastodonClient(Common.resetAuthInfo() ?: return)
                .postNewToot(body)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Snackbar.make(
                            binding.root,
                            R.string.succeed_post_toot,
                            Snackbar.LENGTH_SHORT).show()
                    contentMainBinding.simplicityTootBody.let {
                        it.setText("")
                        hideSoftKeyBoard(it)
                    }
                    button.isEnabled = true
                }, {
                    button.isEnabled = true
                    it.printStackTrace()
                })
    }

    private fun stopTimelineStreams() {
        stopPublicTimelineStream()
        stopLocalTimelineStream()
        stopUserTimelineStream()
        stopNotificationTimelineStream()
        stopHashTagTimelineStream()
    }

    private fun startPublicTimelineStream() {
        publicStream?.dispose()
        publicStream = null
        Common.resetAuthInfo()?.let {
            publicStream =
                    MastodonClient(it).getPublicTimelineAsStream()
                            .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                toggleRefreshIndicatorState(false)
                                parseTimelineStream(it)
                            }, { throwable ->
                                throwable.printStackTrace()
                                publicStream?.dispose()
                                toggleRefreshIndicatorState(false)
                            })
        }
    }

    private fun stopPublicTimelineStream() {
        if (publicStream?.isDisposed == false) publicStream?.dispose()
    }

    private fun showPublicTimeline(loadStream: Boolean = false, loadPrev: Boolean = false) {
        if (loadPrev && maxId == -1L) return

        MastodonClient(Common.resetAuthInfo() ?: return).getPublicTimeline(maxId = if (loadPrev) maxId else null, sinceId = if (!loadPrev && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
<<<<<<< Updated upstream
                .subscribe({
                    reflectContents(it, loadPrev)
                    if (loadStream) startPublicTimelineStream()
                }, { throwable ->
                    throwable.printStackTrace()
                    toggleRefreshIndicatorState(false)
                })
    }

    private fun startUserTimelineStream() {
        userStream?.dispose()
        userStream = null

        Common.resetAuthInfo()?.let {
            userStream =
                    MastodonClient(it).getUserTimelineAsStream()
                            .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                toggleRefreshIndicatorState(false)
                                parseTimelineStream(it)
                            }, { throwable ->
                                throwable.printStackTrace()
                                userStream?.dispose()
                                toggleRefreshIndicatorState(false)
                            })
        }
    }

    private fun stopUserTimelineStream() {
        if (userStream?.isDisposed == false) userStream?.dispose()
    }

    private fun showUserTimeline(loadStream: Boolean = false, loadPrev: Boolean = false) {
        if (loadPrev && maxId == -1L) return

        MastodonClient(Common.resetAuthInfo() ?: return).getUserTimeline(maxId = if (loadPrev) maxId else null, sinceId = if (!loadPrev && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    reflectContents(it, loadPrev)
                    if (loadStream) startUserTimelineStream()
                }, { throwable ->
                    throwable.printStackTrace()
                    toggleRefreshIndicatorState(false)
                })
    }

    private fun startLocalTimelineStream() {
        localStream?.dispose()
        localStream = null

        Common.resetAuthInfo()?.let {
            localStream =
                    MastodonClient(it).getLocalTimelineAsStream()
                            .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                toggleRefreshIndicatorState(false)
                                parseTimelineStream(it)
                            }, { throwable ->
                                throwable.printStackTrace()
                                localStream?.dispose()
                                toggleRefreshIndicatorState(false)
                            })
        }
    }

    private fun stopLocalTimelineStream() {
        if (localStream?.isDisposed == false) localStream?.dispose()
    }

    private fun showLocalTimeline(loadStream: Boolean = false, loadPrev: Boolean = false) {
        if (loadPrev && maxId == -1L) return

        MastodonClient(Common.resetAuthInfo() ?: return).getPublicTimeline(true, maxId = if (loadPrev) maxId else null, sinceId = if (!loadPrev && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    reflectContents(it, loadPrev)
                    if (loadStream) startLocalTimelineStream()
                }, { throwable ->
                    throwable.printStackTrace()
                    toggleRefreshIndicatorState(false)
                })
    }

    private fun startNotificationTimelineStream() {
        notificationStream?.dispose()
        notificationStream = null

        Common.resetAuthInfo()?.let {
            notificationStream =
                    MastodonClient(it).getNotificationTimelineAsStream()
                            .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                toggleRefreshIndicatorState(false)
                                parseTimelineStream(it)
                            }, { throwable ->
                                throwable.printStackTrace()
                                notificationStream?.dispose()
                                toggleRefreshIndicatorState(false)
                            })
        }
    }

    private fun stopNotificationTimelineStream() {
        if (notificationStream?.isDisposed == false) notificationStream?.dispose()
    }

    private fun showNotificationTimeline(loadStream: Boolean = false, loadPrev: Boolean = false) {
        if (loadPrev && maxId == -1L) return

        MastodonClient(Common.resetAuthInfo() ?: return).getNotificationTimeline(maxId = if (loadPrev) maxId else null, sinceId = if (!loadPrev && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    reflectContents(it, loadPrev)
                    if (loadStream) startNotificationTimelineStream()
                }, { throwable ->
                    throwable.printStackTrace()
                    toggleRefreshIndicatorState(false)
                })
    }

    private fun startHashTagTimelineStream() {
        hashTagStream?.dispose()
        hashTagStream = null

        getHashTag()?.let { hashTag ->
            Common.resetAuthInfo()?.let {
                MastodonClient(it).getHashTagTimelineAsStream(hashTag)
                        .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({
                            toggleRefreshIndicatorState(false)
                            parseTimelineStream(it)
                        }, { throwable ->
                            throwable.printStackTrace()
                            hashTagStream?.dispose()
                            toggleRefreshIndicatorState(false)
                        })
            }
        }
    }

    private fun stopHashTagTimelineStream() {
        if (hashTagStream?.isDisposed == false) hashTagStream?.dispose()
    }

    private fun showHashTagTimeline(hashTag: String, loadStream: Boolean = false, loadPrev: Boolean = false) {
        if (loadPrev && maxId == -1L) return

        MastodonClient(Common.resetAuthInfo() ?: return).getHashTagTimeline(hashTag, maxId = if (loadPrev) maxId else null, sinceId = if (!loadPrev && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    reflectContents(it, loadPrev)
                    if (loadStream) startHashTagTimelineStream()
                }, { throwable ->
                    throwable.printStackTrace()
                    toggleRefreshIndicatorState(false)
                })
    }

    private fun showFavouriteTimeline(loadPrev: Boolean = false) {
        if (loadPrev && maxId == -1L) return

        MastodonClient(Common.resetAuthInfo() ?: return).getFavouriteTimeline(maxId = if (loadPrev) maxId else null, sinceId = if (!loadPrev && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    reflectContents(it, loadPrev)
                }, { throwable ->
                    throwable.printStackTrace()
                    toggleRefreshIndicatorState(false)
                })
    }

    private fun <V>reflectContents(result: Result<List<V>>, next: Boolean) {
        result.response()?.let {
            it.body()?.let {
                if (it.isNotEmpty()) {
                    if (it.first() is Status) {
                        if (next) adapter.addAllContentsAtLast(it.map { Common.getTimelineContent(status = it as Status) })
                        else adapter.addAllContents(it.map { Common.getTimelineContent(status = it as Status) })
=======
                .subscribe({ source ->
                    Log.d("showPublicTimeline", "source: $source")

                    if (source.startsWith("data: ")) {
                        val data = source.replace(Regex("^data:\\s(.+)"), "$1")
                        try {
                            val status = Gson().fromJson(data, Status::class.java)
                            val content = TimelineContent(status.account.avatarUrl, status.account.displayName, status.account.username, status.createdAt.time, status.content)
                            Log.d("showPublicTimeline", "body: ${status.content}")

                            adapter.addContent(content)
                            if ((binding.recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition() == 0) binding.recyclerView.smoothScrollToPosition(0)
                        } catch (e: JsonSyntaxException) {
                            Log.e("showPublicTimeline", e.message)
                        }
>>>>>>> Stashed changes
                    }
                    if (it.first() is Notification) {
                        if (next) adapter.addAllContentsAtLast(it.map { Common.getTimelineContent(notification = it as Notification) })
                        else adapter.addAllContents(it.map { Common.getTimelineContent(notification = it as Notification) })
                    }
                }
            }

            it.headers().get("Link")?.let {
                maxId = getMaxIdFromLinkString(it)
                sinceId = getSinceIdFromLinkString(it)
            }

            toggleRefreshIndicatorState(false)
        }
    }

    fun onAddItemToAdapter() {
        if (onTop && adapter.itemCount > 1) {
            binding.recyclerView.scrollToPosition(1)
            binding.recyclerView.smoothScrollToPosition(0)
        }
    }

    private fun parseTimelineStream(source: String) {
        if (source.startsWith("data: ")) {
            val data = source.replace(Regex("^data:\\s(.+)"), "$1")
            if (waitingContent) {
                val status = gson.fromJson(data, Status::class.java)
                val content = Common.getTimelineContent(status = status)

                adapter.addContent(content)
            }
            if (waitingNotification) {
                val notification = gson.fromJson(data, Notification::class.java)
                val content = Common.getTimelineContent(notification = notification)

                adapter.addContent(content)
            }
            if (waitingDeletedId) {
                adapter.removeContentByTootId(data.toLong())
            }
        } else {
            waitingContent = source == "event: update" && getCategory() != Category.Notification
            waitingNotification = source == "event: notification" && getCategory() == Category.Notification
            waitingDeletedId = source == "event: delete"
        }
    }

    private fun scrollToTop() {
        binding.recyclerView.smoothScrollToPosition(0)
    }
}