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

    enum class Category(val rawValue: Int) {
        Public(0),
        Local(1),
        User(2),
        HashTag(3),
        Notification(4),
        Unknown(5)
    }

    companion object {
        val TAG: String = this::class.java.simpleName
        val ARGS_KEY_CATEGORY = "category"
        val STATE_ARGS_KEY_CONTENTS = "contents"
        val STATE_ARGS_KEY_RESUME = "resume"
        val REQUEST_CODE_GRANT_ACCESS_WIFI = 100

        fun newInstance(category: Category): TimelineFragment {
            val fragment = TimelineFragment()
            val args = Bundle()
            args.putString(ARGS_KEY_CATEGORY, category.name)
            fragment.arguments = args

            return fragment
        }

        fun getCategoryById(rawValue: Int): Category = Category.values()[rawValue]
    }

    lateinit private var binding: FragmentTimelineBinding
    lateinit private var adapter: TimelineAdapter
    lateinit private var sharedPref: SharedPreferences
    private var onTop = true
    private var inTouch = false
    private var isFirst = true

    private var publicStream: Disposable? = null
    private var localStream: Disposable? = null
    private var userStream: Disposable? = null
    private var notificationStream: Disposable? = null

    private var waitingContent = false
    private var waitingNotification = false
    private var waitingDeletedId = false

    private var maxId: Long = -1
    private var sinceId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_timeline, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val category = getCategory()
        sharedPref.edit()
                .putInt(STATE_KEY_CATEGORY, category.rawValue)
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
                            Category.User -> showUserTimeline(loadNext = true)
                            Category.HashTag -> showNotificationTimeline(loadNext = true)
                        }
                    }
                }
            }
        })
        adapter = TimelineAdapter((activity as MainActivity).timelineListener)
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
        val json = gson.toJson(adapter.getContents())
        sharedPref.edit()
                .putString(getStoreContentsKey(getCategory()), json)
                .apply()
    }

    override fun onResume() {
        super.onResume()

        (activity as MainActivity).supportActionBar?.show()
        refreshBarTitle()

        setSimplicityPostBarVisibility((activity as MainActivity).binding.appBarMain.contentMain, Common.isModeShowTootBar(activity))

        if (shouldResume()) restoreTimeline()
        else {
            sharedPref.edit().putBoolean(STATE_ARGS_KEY_RESUME, true).apply()
            showTimelineByCategory(getCategory())
        }

        (activity as MainActivity).resetSelectionNavItem(
                when (getCategory()) {
                    Category.Public -> MainActivity.NAV_ITEM_TL_PUBLIC
                    Category.Local -> MainActivity.NAV_ITEM_TL_LOCAL
                    Category.User -> MainActivity.NAV_ITEM_TL_USER
                    Category.Notification -> MainActivity.NAV_ITEM_TL_NOTIFICATION
                    else -> -1
                })

        isFirst = false
    }

    fun getCategory(): Category = if (arguments != null && arguments.containsKey(ARGS_KEY_CATEGORY)) Category.valueOf(arguments.getString(ARGS_KEY_CATEGORY, "Unknown")) else Category.Unknown

    fun shouldResume(): Boolean = sharedPref.contains(STATE_ARGS_KEY_RESUME) && sharedPref.getBoolean(STATE_ARGS_KEY_RESUME, true) && !isFirst

    fun existsNoRunningStream() = listOf(publicStream, localStream, userStream).none { !(it?.isDisposed ?: true) }

    fun refreshBarTitle() {
        val instanceId = Common.getCurrentAccessToken()?.instanceId
        val domain = if (instanceId == null) "not logged in" else OrmaProvider.db.selectFromInstanceAuthInfo().idEq(instanceId).last().instance
        val category = getCategory()
        (activity as MainActivity).supportActionBar?.title = "$category TL - $domain"
    }

    fun restoreTimeline() {
        val key = getStoreContentsKey(getCategory())
        if (sharedPref.contains(key)) {
            adapter.clearContents()
            val type = object: TypeToken<List<TimelineContent>>() {}
            val contents: List<TimelineContent> = gson.fromJson(sharedPref.getString(key, ""), type.type)
            adapter.addAllContents(contents)

            showTimelineByCategory(getCategory(), true)
        } else {
            showTimelineByCategory(getCategory())
        }
        sharedPref.edit().remove(STATE_ARGS_KEY_CONTENTS).apply()
    }

    fun toggleRefreshIndicatorState(show: Boolean) = Common.toggleRefreshIndicatorState(binding.swipeRefreshLayout, show)

    fun toggleRefreshIndicatorActivity(show: Boolean) = Common.toggleRefreshIndicatorActivity(binding.swipeRefreshLayout, show)

    fun showTimelineByCategory(category: Category, hasContents: Boolean = false) {
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
            if (hasContents) {
                when (category) {
                    Category.Public -> startPublicTimelineStream()
                    Category.Local -> startLocalTimelineStream()
                    Category.User -> startUserTimelineStream()
                    Category.HashTag -> {}
                    Category.Notification -> startUserTimelineStream()
                }
            } else {
                when (category) {
                    Category.Public -> startPublicTimelineStream()
                    Category.Local -> startLocalTimelineStream()
                    Category.User -> showUserTimeline(true)
                    Category.HashTag -> {}
                    Category.Notification -> showNotificationTimeline(true)
                }
            }
        } else {
            when (category) {
                Category.Public -> showPublicTimeline()
                Category.Local -> showLocalTimeline()
                Category.User -> showUserTimeline()
                Category.HashTag -> {}
                Category.Notification -> showNotificationTimeline()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_GRANT_ACCESS_WIFI -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.filter { it != PackageManager.PERMISSION_GRANTED }.isEmpty()) {
                    showTimelineByCategory(getCategory())
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_wifi_grant, Snackbar.LENGTH_SHORT)
                }
            }
        }
    }

    fun postToot(contentMainBinding: ContentMainBinding) {
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

    fun stopTimelineStreams() {
        stopPublicTimelineStream()
        stopLocalTimelineStream()
        stopUserTimelineStream()
    }

    fun startPublicTimelineStream() {
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

    fun stopPublicTimelineStream() {
        if (getCategory() == Category.Public && !(publicStream?.isDisposed ?: true)) publicStream?.dispose()
    }

    fun showPublicTimeline(loadNext: Boolean = false) {
        if (loadNext && maxId == -1L) return

        MastodonClient(Common.resetAuthInfo() ?: return).getPublicTimeline(maxId = if (loadNext) maxId else null, sinceId = if (!loadNext && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    reflectStatuses(it, loadNext)
                }, Throwable::printStackTrace)
    }

    fun startUserTimelineStream() {
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

    fun stopUserTimelineStream() {
        if (getCategory() == Category.User && !(userStream?.isDisposed ?: true)) userStream?.dispose()
    }

    fun showUserTimeline(loadStream: Boolean = false, loadNext: Boolean = false) {
        if (loadNext && maxId == -1L) return

        MastodonClient(Common.resetAuthInfo() ?: return).getUserTimeline(maxId = if (loadNext) maxId else null, sinceId = if (!loadNext && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    reflectStatuses(it, loadNext)
                    if (loadStream) startUserTimelineStream()
                }, { throwable ->
                    throwable.printStackTrace()
                    toggleRefreshIndicatorState(false)
                })
    }

    fun startLocalTimelineStream() {
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

    fun stopLocalTimelineStream() {
        if (getCategory() == Category.Local && !(localStream?.isDisposed ?: true)) localStream?.dispose()
    }

    fun showLocalTimeline(loadNext: Boolean = false) {
        if (loadNext && maxId == -1L) return

        MastodonClient(Common.resetAuthInfo() ?: return).getPublicTimeline(true, maxId = if (loadNext) maxId else null, sinceId = if (!loadNext && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    reflectStatuses(it, loadNext)
                }, Throwable::printStackTrace)
    }

    fun stopNotificationTimelineStream() {
        if (getCategory() == Category.User && !(userStream?.isDisposed ?: true)) userStream?.dispose()
    }

    fun showNotificationTimeline(loadStream: Boolean = false, loadNext: Boolean = false) {
        if (loadNext && maxId == -1L) return
        MastodonClient(Common.resetAuthInfo() ?: return).getNotificationTimeline(maxId = if (loadNext) maxId else null, sinceId = if (!loadNext && sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    toggleRefreshIndicatorState(false)
                    reflectContents(it, loadNext)
                    if (loadStream) startUserTimelineStream()
                }, { throwable ->
                    throwable.printStackTrace()
                    toggleRefreshIndicatorState(false)
                })
    }

    fun getRegexExtractSinceId() = Regex(".*since_id=(\\d+?)>.*")
    fun getRegexExtractMaxId() = Regex(".*max_id=(\\d+?)>.*")

    fun <V>reflectContents(result: Result<List<V>>, next: Boolean) {
        result.response()?.let {
            it.body()?.let {
                if (it.first() is Status) {
                    if (next) adapter.addAllContentsAtLast(it.map { Common.getTimelineContent(status = it as Status)})
                    else adapter.addAllContents(it.map { Common.getTimelineContent(status = it as Status) })
                }
                if (it.first() is Notification) {
                    if (next) adapter.addAllContentsAtLast(it.map { Common.getTimelineContent(notification = it as Notification)})
                    else adapter.addAllContents(it.map { Common.getTimelineContent(notification = it as Notification) })
                }
            }

            maxId = it.headers().get("Link")?.replace(getRegexExtractMaxId(), "$1")?.toLong() ?: -1L
            sinceId = it.headers().get("Link")?.replace(getRegexExtractSinceId(), "$1")?.toLong() ?: -1L

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
                Log.d("parse", "body: ${status.content}")

                adapter.addContent(content)
                onAddItemToAdapter()
            }
            if (waitingNotification) {
                val notification = gson.fromJson(data, Notification::class.java)
                if (notification.type == Notification.Companion.NotificationType.reblog.name) {
                    val content = Common.getTimelineContent(notification = notification)

                    adapter.addContent(content)
                    onAddItemToAdapter()
                }
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
}