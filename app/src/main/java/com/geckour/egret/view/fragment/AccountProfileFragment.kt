package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.content.ContextCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.RecyclerView
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Relationship
import com.geckour.egret.databinding.FragmentAccountProfileBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class AccountProfileFragment: BaseFragment() {

    companion object {
        val TAG = "accountProfileFragment"
        val ARGS_KEY_ACCOUNT = "account"

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
        Picasso.with(binding.icon.context).load(content.iconUrl).into(binding.icon)
        Picasso.with(binding.header.context).load(content.headerUrl).into(binding.header)

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.url.movementMethod = LinkMovementMethod.getInstance()
        binding.note.movementMethod = LinkMovementMethod.getInstance()

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

        binding.recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        adapter = TimelineAdapter(object: TimelineAdapter.IListenr {
            override fun showProfile(accountId: Long) {
                newObservableInstance(accountId)
                        .subscribe( {
                            fragment ->
                            activity.supportFragmentManager.beginTransaction().replace(R.id.container, fragment, TAG).addToBackStack(TAG).commit()
                        }, Throwable::printStackTrace)
            }

            override fun onReply(content: TimelineContent) {
                (activity as MainActivity).replyStatusById(content)
            }

            override fun onFavStatus(statusId: Long, view: ImageView) {
                (activity as MainActivity).favStatusById(statusId, view)
            }

            override fun onBoostStatus(statusId: Long, view: ImageView) {
                (activity as MainActivity).boostStatusById(statusId, view)
            }
        })
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

        bundle.putParcelableArrayList(TimelineFragment.STATE_ARGS_KEY_CONTENTS, ArrayList(adapter.getContents()))
    }

    override fun onResume() {
        super.onResume()

        restoreTimeline(bundle)
    }

    fun showToots(loadNext: Boolean = false) {
        val next = loadNext && nextId != null && (nextId?.compareTo(-1) ?: 0) == 1
        if (nextId != null) MastodonClient(Common.resetAuthInfo() ?: return).getAccountAllToots(account.id, if (next) nextId else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ result ->
                    if (next) adapter.addAllContentsInLast(result.response().body().map { status -> Common.getTimelineContent(status) }, -1)
                    else adapter.addAllContents(result.response().body().map { status -> Common.getTimelineContent(status) }, -1)
                    nextId = result.response().headers().get("Link")?.replace(Regex(".*<https?://.+\\?max_id=(.+?)>.*"), "$1")?.toLong()
                }, Throwable::printStackTrace)
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
}