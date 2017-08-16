package com.geckour.egret.view.fragment

import android.content.SharedPreferences
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Relationship
import com.geckour.egret.api.model.Status
import com.geckour.egret.databinding.FragmentAccountProfileBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import retrofit2.adapter.rxjava2.Result

class AccountProfileFragment: BaseFragment() {

    companion object {
        val TAG: String = this::class.java.simpleName
        val ARGS_KEY_ACCOUNT = "account"

        fun newInstance(account: Account): AccountProfileFragment = AccountProfileFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_ACCOUNT, account)
            }
        }

        fun newObservableInstance(accountId: Long): Observable<AccountProfileFragment> {
            return MastodonClient(Common.resetAuthInfo() ?: throw IllegalArgumentException()).getAccount(accountId)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap { account ->
                        val fragment = newInstance(account)
                        Observable.just(fragment)
                    }
        }
    }

    private val account: Account by lazy { arguments[ARGS_KEY_ACCOUNT] as Account }
    lateinit private var relationship: Relationship
    lateinit private var binding: FragmentAccountProfileBinding
    private var onTop = true
    private var inTouch = false
    private val adapter: TimelineAdapter by lazy { TimelineAdapter((activity as MainActivity).timelineListener) }
    private val sharedPref: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(activity) }

    private var maxId: Long = -1
    private var sinceId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as MainActivity).supportActionBar?.hide()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_account_profile, container, false)

        val content = Common.getProfileContent(account)

        binding.content = content
        binding.timeString = Common.getReadableDateString(content.createdAt, true)
        Glide.with(binding.header.context).load(content.headerUrl).into(binding.header)

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.icon.setOnClickListener { showImageViewer(listOf(account.avatarUrl), 0) }
        binding.header.setOnClickListener { showImageViewer(listOf(account.headerUrl), 0) }

        val movementMethod = Common.getMovementMethodFromPreference(binding.root.context)
        binding.url.movementMethod = movementMethod
        binding.note.movementMethod = movementMethod

        val domain = Common.resetAuthInfo()
        if (domain != null) MastodonClient(domain).getAccountRelationships(account.id)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { relationships ->
                    this.relationship = relationships.first()

                    val currentAccountId = Common.getCurrentAccessToken()?.accountId
                    if (currentAccountId != null && currentAccountId != account.id) {
                        binding.follow.visibility = View.VISIBLE
                        binding.block.visibility = View.VISIBLE
                        binding.mute.visibility = View.VISIBLE
                    }

                    setFollowButtonState(this.relationship.following)
                    binding.follow.setOnClickListener {
                        if (this.relationship.following) {
                            MastodonClient(domain).unFollowAccount(account.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setFollowButtonState(relation.following)
                                    }, Throwable::printStackTrace)
                        } else {
                            MastodonClient(domain).followAccount(account.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setFollowButtonState(relation.following)
                                    }, Throwable::printStackTrace)
                        }
                    }

                    setBlockButtonState(this.relationship.blocking)
                    binding.block.setOnClickListener {
                        if (this.relationship.blocking) {
                            MastodonClient(domain).unBlockAccount(account.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setBlockButtonState(relation.blocking)
                                    }, Throwable::printStackTrace)
                        } else {
                            MastodonClient(domain).blockAccount(account.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setBlockButtonState(relation.blocking)
                                    }, Throwable::printStackTrace)
                        }
                    }

                    setMuteButtonState(this.relationship.blocking)
                    binding.mute.setOnClickListener {
                        if (this.relationship.muting) {
                            MastodonClient(domain).unMuteAccount(account.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setMuteButtonState(relation.muting)
                                    }, Throwable::printStackTrace)
                        } else {
                            MastodonClient(domain).muteAccount(account.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setMuteButtonState(relation.muting)
                                    }, Throwable::printStackTrace)
                        }
                    }
                }, Throwable::printStackTrace)

        binding.timeline.recyclerView.setOnTouchListener { _, event ->
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
        binding.timeline.recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val scrollY: Int = recyclerView?.computeVerticalScrollOffset() ?: -1
                onTop = scrollY == 0 || onTop && !(inTouch && scrollY > 0)

                if (!onTop) {
                    val y = scrollY + (recyclerView?.height ?: -1)
                    val h = recyclerView?.computeVerticalScrollRange() ?: -1
                    if (y == h) {
                        showToots(true)
                    }
                }
            }
        })
        binding.timeline.recyclerView.adapter = adapter

        binding.timeline.swipeRefreshLayout.apply {
            setColorSchemeResources(R.color.colorAccent)
            setOnRefreshListener {
                showToots()
            }
        }

        showToots()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        reflectSettings()
        refreshBarTitle()
    }

    fun showImageViewer(urls: List<String>, position: Int) {
        val fragment = ShowImagesDialogFragment.newInstance(urls, position)
        activity.supportFragmentManager.beginTransaction()
                .add(fragment, ShowImagesDialogFragment.TAG)
                .addToBackStack(ShowImagesDialogFragment.TAG)
                .commit()
    }

    fun showToots(loadNext: Boolean = false) {
        if (loadNext && maxId == -1L) return

        Common.resetAuthInfo()?.let {
            MastodonClient(it).getAccountAllToots(account.id, if (loadNext) maxId else null, if (!loadNext && sinceId != -1L) sinceId else null)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({ result ->
                        reflectStatuses(result, loadNext)
                    }, Throwable::printStackTrace)
        }
    }

    fun getRegexExtractSinceId() = Regex(".*since_id=(\\d+?)>.*")
    fun getRegexExtractMaxId() = Regex(".*max_id=(\\d+?)>.*")

    fun reflectStatuses(result: Result<List<Status>>, next: Boolean) {
        result.response()?.let {
            if (next) adapter.addAllContentsAtLast(it.body().map { Common.getTimelineContent(it) })
            else adapter.addAllContents(it.body().map { Common.getTimelineContent(it) })

            maxId = it.headers().get("Link")?.let {
                if (it.contains("max_id")) {
                    try {
                        it.replace(getRegexExtractMaxId(), "$1").toLong()
                    } catch (e: NumberFormatException) {
                        e.printStackTrace()
                        maxId
                    }
                } else maxId
            } ?: -1L
            sinceId = it.headers().get("Link")?.let {
                if (it.contains("since_id")) {
                    try {
                        it.replace(getRegexExtractSinceId(), "$1").toLong()
                    } catch (e: NumberFormatException) {
                        e.printStackTrace()
                        sinceId
                    }
                } else sinceId
            } ?: -1L

            toggleRefreshIndicatorState(false)
        }
    }

    fun setFollowButtonState(state: Boolean) {
        if (state) {
            binding.follow.setImageResource(R.drawable.ic_people_black_24px)
            binding.follow.setColorFilter(ContextCompat.getColor(activity, R.color.accent))
        } else {
            binding.follow.setImageResource(R.drawable.ic_person_add_black_24px)
            binding.follow.setColorFilter(ContextCompat.getColor(activity, R.color.icon_tint_dark))
        }
    }

    fun setBlockButtonState(state: Boolean) {
        binding.block.setColorFilter(
                ContextCompat.getColor(activity,
                        if (state) R.color.accent else R.color.icon_tint_dark))
    }

    fun setMuteButtonState(state: Boolean) {
        binding.mute.setColorFilter(
                ContextCompat.getColor(activity,
                        if (state) R.color.accent else R.color.icon_tint_dark))
    }

    fun reflectSettings() {
        val movementMethod = Common.getMovementMethodFromPreference(binding.root.context)
        binding.url.movementMethod = movementMethod
        binding.note.movementMethod = movementMethod
    }

    fun refreshBarTitle() {
        (activity as MainActivity).supportActionBar?.title = "${account.displayName}'s profile"
    }

    fun toggleRefreshIndicatorState(show: Boolean) {
        binding.timeline.swipeRefreshLayout.isRefreshing = show
    }
}