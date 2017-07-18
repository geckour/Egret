package com.geckour.egret.view.fragment

import android.content.SharedPreferences
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Relationship
import com.geckour.egret.databinding.FragmentAccountProfileBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class AccountProfileFragment: BaseFragment() {

    companion object {
        val TAG: String = this::class.java.simpleName
        val ARGS_KEY_ACCOUNT = "account"
        private val STATE_KEY_THEME_MODE = "theme mode"

        fun newInstance(account: Account): AccountProfileFragment {
            val fragment = AccountProfileFragment()
            val args = Bundle()
            args.putSerializable(ARGS_KEY_ACCOUNT, account)
            fragment.arguments = args

            return fragment
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

    lateinit private var account: Account
    lateinit private var relationship: Relationship
    lateinit private var binding: FragmentAccountProfileBinding
    lateinit private var adapter: TimelineAdapter
    lateinit private var sharedPref: SharedPreferences
    private var sinceId: Long = -1
    private var maxId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
        account = arguments[ARGS_KEY_ACCOUNT] as Account
        (activity as MainActivity).supportActionBar?.hide()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_account_profile, container, false)

        val content = Common.getProfileContent(account)

        binding.content = content
        binding.timeString = Common.getReadableDateString(content.createdAt, true)
        Picasso.with(binding.header.context).load(content.headerUrl).into(binding.header)

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        adapter = TimelineAdapter((activity as MainActivity).timelineListener, false)
        binding.timeline.recyclerView.adapter = adapter

        binding.timeline.recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val scrollY: Int = recyclerView?.computeVerticalScrollOffset() ?: -1
                val y = scrollY + (recyclerView?.height ?: -1)
                val h = recyclerView?.computeVerticalScrollRange() ?: -1
                if (y == h) {
                    showToots(true)
                }
            }
        })

        binding.timeline.swipeRefreshLayout.apply {
            setColorSchemeResources(R.color.colorAccent)
            setOnRefreshListener {
                showToots()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        showToots()
    }

    override fun onResume() {
        super.onResume()

        reflectSettings()
    }

    fun showToots(loadNext: Boolean = false) {
        if (loadNext && maxId == -1L) return

        toggleRefreshIndicatorState(true)
        Common.resetAuthInfo()?.let {
            MastodonClient(it).getAccountAllToots(account.id, if (loadNext) maxId else null, if (!loadNext && sinceId != -1L) sinceId else null)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({ result ->
                        if (loadNext) adapter.addAllContentsAtLast(result.response().body().map { status -> Common.getTimelineContent(status) })
                        else adapter.addAllContents(result.response().body().map { status -> Common.getTimelineContent(status) })
                        maxId = result.response().headers().get("Link")?.replace(getRegexExtractMaxId(), "$1")?.let { if (it.isEmpty()) "-1" else it }?.toLong() ?: -1L
                        sinceId = result.response().headers().get("Link")?.replace(getRegexExtractSinceId(), "$1")?.let { if (it.isEmpty()) "-1" else it }?.toLong() ?: -1L
                        toggleRefreshIndicatorState(false)
                    }, { throwable ->
                        throwable.printStackTrace()
                        toggleRefreshIndicatorState(false)
                    })
        }
    }

    fun getRegexExtractSinceId() = Regex(".*since_id=(\\d+?)>.*")
    fun getRegexExtractMaxId() = Regex(".*max_id=(\\d+?)>.*")

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
        if (state) {
            binding.block.setColorFilter(ContextCompat.getColor(activity, R.color.accent))
        } else {
            binding.block.setColorFilter(ContextCompat.getColor(activity, R.color.icon_tint_dark))
        }
    }

    fun setMuteButtonState(state: Boolean) {
        if (state) {
            binding.mute.setColorFilter(ContextCompat.getColor(activity, R.color.accent))
        } else {
            binding.mute.setColorFilter(ContextCompat.getColor(activity, R.color.icon_tint_dark))
        }
    }

    fun reflectSettings() {
        val movementMethod = Common.getMovementMethodFromPreference(binding.root.context)
        binding.url.movementMethod = movementMethod
        binding.note.movementMethod = movementMethod
    }

    fun toggleRefreshIndicatorState(show: Boolean) = Common.toggleRefreshIndicatorState(binding.timeline.swipeRefreshLayout, show)

    fun toggleRefreshIndicatorActivity(show: Boolean) = Common.toggleRefreshIndicatorActivity(binding.timeline.swipeRefreshLayout, show)
}