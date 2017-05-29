package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.Parcelable
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
import com.geckour.egret.view.activity.BaseActivity
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.geckour.egret.view.fragment.TimelineFragment.Companion.STATE_ARGS_KEY_CONTENTS
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class AccountProfileFragment: BaseFragment() {

    companion object {
        val TAG = "accountProfileFragment"
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
    private val bundle = Bundle()
    private var nextId: Long? = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        binding.recyclerView.adapter = adapter

        binding.recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
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
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        showToots()
    }

    override fun onPause() {
        super.onPause()

        bundle.putParcelableArrayList(STATE_ARGS_KEY_CONTENTS, ArrayList(adapter.getContents()))
        bundle.putBoolean(STATE_KEY_THEME_MODE, (activity as BaseActivity).isModeDark())
    }

    override fun onResume() {
        super.onResume()

        if (bundle.containsKey(STATE_KEY_THEME_MODE) && (bundle.getBoolean(STATE_KEY_THEME_MODE, false) xor (activity as BaseActivity).isModeDark())) {
            bundle.clear()
            activity.recreate()
        }

        restoreTimeline(bundle)

        reflectSettings()
    }

    fun showToots(loadNext: Boolean = false) {
        val next = loadNext && nextId != null && (nextId?.compareTo(-1) ?: 0) == 1
        if (nextId != null)
            Common.resetAuthInfo()?.let {
                MastodonClient(it).getAccountAllToots(account.id, if (next) nextId else null)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({ result ->
                            if (next) adapter.addAllContentsAtLast(result.response().body().map { status -> Common.getTimelineContent(status) }, -1)
                            else adapter.addAllContents(result.response().body().map { status -> Common.getTimelineContent(status) }, -1)
                            nextId = result.response().headers().get("Link")?.replace(Regex(".*<https?://.+\\?max_id=(.+?)>.*"), "$1")?.toLong()
                        }, Throwable::printStackTrace)
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

    fun restoreTimeline(savedInstanceState: Bundle?) {
        if (savedInstanceState != null && savedInstanceState.containsKey(TimelineFragment.STATE_ARGS_KEY_CONTENTS)) {
            val parcelables: ArrayList<Parcelable> = savedInstanceState.getParcelableArrayList(TimelineFragment.STATE_ARGS_KEY_CONTENTS)
            adapter.addAllContents(parcelables.map { parcelable -> parcelable as TimelineContent })
        }
    }

    fun reflectSettings() {
        val movementMethod = Common.getMovementMethodFromPreference(binding.root.context)
        binding.url.movementMethod = movementMethod
        binding.note.movementMethod = movementMethod
    }
}