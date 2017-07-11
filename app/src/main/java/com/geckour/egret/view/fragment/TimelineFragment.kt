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

        binding.recyclerView.setOnTouchListener { view, event ->
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
                            Category.HashTag -> {}
                        }
                    }
                }
            }
        })
        adapter = TimelineAdapter((activity as MainActivity).timelineListener)
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.apply {
            isEnabled = false
            setColorSchemeResources(R.color.colorAccent)
            setOnRefreshListener {
                if (existsAnyRunningStream()) showTimelineByCategory(getCategory())
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
                    else -> -1
                })

        isFirst = false
    }

    fun getCategory(): Category = if (arguments != null && arguments.containsKey(ARGS_KEY_CATEGORY)) Category.valueOf(arguments.getString(ARGS_KEY_CATEGORY, "Unknown")) else Category.Unknown

    fun shouldResume(): Boolean = sharedPref.contains(STATE_ARGS_KEY_RESUME) && sharedPref.getBoolean(STATE_ARGS_KEY_RESUME, true) && !isFirst

    fun existsAnyRunningStream() = listOf(publicStream, localStream, userStream).filter { !(it?.isDisposed ?: true) }.isEmpty()

    fun hideRefreshIndicator() { binding.swipeRefreshLayout.isRefreshing = false }

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

    fun showTimelineByCategory(category: Category, hasContents: Boolean = false) {
        val prefStream = sharedPref.getString("manage_stream", "")

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

        binding.swipeRefreshLayout.isEnabled = true
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
                }
            } else {
                when (category) {
                    Category.Public -> startPublicTimelineStream()
                    Category.Local -> startLocalTimelineStream()
                    Category.User -> showUserTimeline(true)
                    Category.HashTag -> {}
                }
            }
        } else {
            when (category) {
                Category.Public -> showPublicTimeline()
                Category.Local -> showLocalTimeline()
                Category.User -> showUserTimeline()
                Category.HashTag -> {}
            }
        }
    }

    fun toggleRefreshIndicatorState(show: Boolean) {
        binding.swipeRefreshLayout.isRefreshing = show
    }

    fun toggleRefreshIndicatorActivity(activity: Boolean) {
        binding.swipeRefreshLayout.isEnabled = activity
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

    fun postToot(contantMainBinding: ContentMainBinding) {
        val button = contantMainBinding.buttonSimplicityToot.apply { isEnabled = false }
        val body = contantMainBinding.simplicityTootBody.text.toString()
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
                    contantMainBinding.simplicityTootBody.let {
                        it.setText("")
                        hideSoftKeyBoard(it)
                    }
                    button.isEnabled = true
                }, {
                    button.isEnabled = true
                    it.printStackTrace()
                })
    }

    fun setSimplicityPostBarVisibility(contentMainBinding: ContentMainBinding, isVisible: Boolean) {
        contentMainBinding.simplicityPostWrap.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    fun stopTimelineStreams() {
        stopPublicTimelineStream()
        stopLocalTimelineStream()
        stopUserTimelineStream()
    }

    fun startPublicTimelineStream() {
        Common.resetAuthInfo()?.let {
            publicStream =
                    MastodonClient(it).getPublicTimelineAsStream()
                            .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                toggleRefreshIndicatorState(false)
                                toggleRefreshIndicatorActivity(false)
                                parseTimelineStream(it)
                            }, Throwable::printStackTrace)
        }
    }

    fun stopPublicTimelineStream() {
        if (getCategory() == Category.Public && !(publicStream?.isDisposed ?: true)) publicStream?.dispose()
    }

    fun showPublicTimeline(loadNext: Boolean = false) {
        val next = loadNext && maxId != -1L
        MastodonClient(Common.resetAuthInfo() ?: return).getPublicTimeline(maxId = if (next) maxId else null, sinceId = if (sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    toggleRefreshIndicatorState(false)
                    reflectStatuses(it, next)
                }, Throwable::printStackTrace)
    }

    fun startUserTimelineStream() {
        Common.resetAuthInfo()?.let {
            userStream =
                    MastodonClient(it).getUserTimelineAsStream()
                            .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                toggleRefreshIndicatorState(false)
                                toggleRefreshIndicatorActivity(false)
                                parseTimelineStream(it)
                            }, Throwable::printStackTrace)
        }
    }

    fun stopUserTimelineStream() {
        if (getCategory() == Category.User && !(userStream?.isDisposed ?: true)) userStream?.dispose()
    }

    fun showUserTimeline(loadStream: Boolean = false, loadNext: Boolean = false) {
        val next = loadNext && maxId != -1L
        MastodonClient(Common.resetAuthInfo() ?: return).getUserTimeline(maxId = if (next) maxId else null, sinceId = if (sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    toggleRefreshIndicatorState(false)
                    reflectStatuses(it, next)
                    if (loadStream) startUserTimelineStream()
                }, Throwable::printStackTrace)
    }

    fun startLocalTimelineStream() {
        Common.resetAuthInfo()?.let {
            MastodonClient(it).getLocalTimelineAsStream()
                    .flatMap { responseBody -> MastodonService.events(responseBody.source()) }
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({
                        toggleRefreshIndicatorState(false)
                        toggleRefreshIndicatorActivity(false)
                        parseTimelineStream(it)
                    }, Throwable::printStackTrace)
        }
    }

    fun stopLocalTimelineStream() {
        if (getCategory() == Category.Local && !(localStream?.isDisposed ?: true)) localStream?.dispose()
    }

    fun showLocalTimeline(loadNext: Boolean = false) {
        val next = loadNext && maxId != -1L
        MastodonClient(Common.resetAuthInfo() ?: return).getPublicTimeline(true, maxId = if (next) maxId else null, sinceId = if (sinceId != -1L) sinceId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    toggleRefreshIndicatorState(false)
                    reflectStatuses(it, next)
                }, Throwable::printStackTrace)
    }

    fun getRegexExtractSinceId() = Regex(".*since_id=(\\d+?)>.*")
    fun getRegexExtractMaxId() = Regex(".*max_id=(\\d+?)>.*")

    fun reflectStatuses(result: Result<List<Status>>, next: Boolean) {
        if (next) adapter.addAllContentsAtLast(result.response().body().map { Common.getTimelineContent(it) })
        else adapter.addAllContents(result.response().body().map { Common.getTimelineContent(it) })

        maxId = result.response().headers().get("Link")?.replace(getRegexExtractMaxId(), "$1")?.toLong() ?: -1L
        sinceId = result.response().headers().get("Link")?.replace(getRegexExtractSinceId(), "$1")?.toLong() ?: -1L

        hideRefreshIndicator()
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
                val content = Common.getTimelineContent(status)
                Log.d("parse", "body: ${status.content}")

                adapter.addContent(content)
                onAddItemToAdapter()
            }
            if (waitingNotification) {
                val notification = gson.fromJson(data, Notification::class.java)
                if (notification.type == Notification.Companion.NotificationType.reblog.name) {
                    val status = notification.status
                    if (status != null) {
                        val content = Common.getTimelineContent(status, notification)
                        Log.d("parse", "body: ${status.content}")

                        adapter.addContent(content)
                        onAddItemToAdapter()
                    }
                }
            }
            if (waitingDeletedId) {
                adapter.removeContentByTootId(data.toLong())
            }
        } else {
            waitingContent = source == "event: update"
            waitingNotification = source == "event: notification"
            waitingDeletedId = source == "event: delete"
        }
    }
}