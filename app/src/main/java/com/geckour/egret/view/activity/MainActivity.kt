package com.geckour.egret.view.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar
import android.text.Html
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.model.MuteClient
import com.geckour.egret.model.MuteInstance
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.ListDialogAdapter
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.geckour.egret.view.fragment.AccountProfileFragment
import com.geckour.egret.view.fragment.ListDialogFragment
import com.geckour.egret.view.fragment.NewTootCreateFragment
import com.geckour.egret.view.fragment.TimelineFragment
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class MainActivity : BaseActivity() {

    lateinit var drawer: Drawer
    lateinit private var accountHeader: AccountHeader

    companion object {
        val NAV_ITEM_LOGIN: Long = 0
        val NAV_ITEM_TL_PUBLIC: Long = 1
        val NAV_ITEM_TL_USER: Long = 2
        val NAV_ITEM_SETTINGS: Long = 3

        fun getIntent(context: Context): Intent {
            val intent = Intent(context, MainActivity::class.java)
            return intent
        }
    }

    val timelineListener = object: TimelineAdapter.IListener {
        override fun showMuteDialog(content: TimelineContent) {
            val itemStrings = resources.getStringArray(R.array.mute_from_toot).toList()
            val items = itemStrings.mapIndexed { i, s ->
                when (i) {
                    0 -> Pair(R.string.array_item_mute_account, s.format(content.nameWeak))
                    1 -> Pair(
                            R.string.array_item_mute_keyword,
                            s.format(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        Html.fromHtml(content.body.toString(), Html.FROM_HTML_MODE_COMPACT).toString()
                                    } else {
                                        Html.fromHtml(content.body.toString()).toString()
                                    }
                            )
                    )
                    2 -> Pair(R.string.array_item_mute_hash_tag, if (content.tags.isEmpty()) "" else s.format(content.tags.map { s -> "#$s" }.joinToString()))
                    3 -> {
                        var instance = content.nameWeak.replace(Regex("^@.+@(.+)$"), "@$1")

                        if (content.nameWeak == instance) {
                            instance = ""
                            Common.getCurrentAccessToken()?.instanceId?.let {
                                instance = "@${OrmaProvider.db.selectFromInstanceAuthInfo().idEq(it).last().instance}"
                            }
                        }
                        Pair(R.string.array_item_mute_instance, s.format(instance))
                    }
                    4 -> Pair(R.string.array_item_mute_client, if (TextUtils.isEmpty(content.app)) "" else s.format(content.app))
                    else -> Pair(-1, s)
                }
            }.filter { !TextUtils.isEmpty(it.second) }
            items.forEach { Timber.d("items: ${it.first}, ${it.second}") }
            ListDialogFragment.newInstance(
                    getString(R.string.dialog_title_mute),
                    items,
                    object: ListDialogFragment.OnItemClickListener {
                        override fun onClick(resId: Int) {
                            when (resId) {
                                R.string.array_item_mute_account -> {
                                    Common.resetAuthInfo()?.let {
                                        MastodonClient(it).muteAccount(content.accountId) // TODO: 自分自身をミュートしないようにする
                                                .subscribeOn(Schedulers.newThread())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe({
                                                    Snackbar.make(findViewById(R.id.container), "Muted account: ${content.nameWeak}", Snackbar.LENGTH_SHORT).show()
                                                }, Throwable::printStackTrace)
                                    }
                                }

                                R.string.array_item_mute_keyword -> {
                                    // TODO: キーワードミュート用のFragmentを作って content.body を投げる
                                }

                                R.string.array_item_mute_hash_tag -> {
                                    // TODO: ハッシュタグミュートのFragmentを作って content.tags を投げる
                                }

                                R.string.array_item_mute_instance -> {
                                    var instance = content.nameWeak.replace(Regex("^@.+@(.+)$"), "@$1")

                                    if (content.nameWeak == instance) {
                                        instance = ""
                                        Common.getCurrentAccessToken()?.instanceId?.let {
                                            instance = "@${OrmaProvider.db.selectFromInstanceAuthInfo().idEq(it).last().instance}"
                                        }
                                    }

                                    if (!TextUtils.isEmpty(instance)) {
                                        OrmaProvider.db.prepareInsertIntoMuteInstanceAsSingle()
                                                .map { inserter -> inserter.execute(MuteInstance(-1L, instance)) }
                                                .subscribeOn(Schedulers.newThread())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .compose(bindToLifecycle())
                                                .subscribe({
                                                    Snackbar.make(findViewById(R.id.container), "Muted instance: $instance", Snackbar.LENGTH_SHORT).show()
                                                }, Throwable::printStackTrace)
                                    }
                                }

                                R.string.array_item_mute_client -> {
                                    content.app?.let {
                                        OrmaProvider.db.prepareInsertIntoMuteClientAsSingle()
                                                .map { inserter -> inserter.execute(MuteClient(-1L, it)) }
                                                .subscribeOn(Schedulers.newThread())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .compose(bindToLifecycle())
                                                .subscribe({
                                                    Snackbar.make(findViewById(R.id.container), "Muted client: ${content.app}", Snackbar.LENGTH_SHORT).show()
                                                }, Throwable::printStackTrace)
                                    }
                                }
                            }
                        }
                    }).show(supportFragmentManager, ListDialogFragment.TAG)
        }

        override fun showProfile(accountId: Long) {
            AccountProfileFragment.newObservableInstance(accountId)
                    .subscribe( {
                        fragment ->
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.container, fragment, AccountProfileFragment.TAG)
                                .addToBackStack(AccountProfileFragment.TAG)
                                .commit()
                    }, Throwable::printStackTrace)
        }

        override fun onReply(content: TimelineContent) {
            replyStatusById(content)
        }

        override fun onFavStatus(statusId: Long, view: ImageView) {
            favStatusById(statusId, view)
        }

        override fun onBoostStatus(statusId: Long, view: ImageView) {
            boostStatusById(statusId, view)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(if (isModeDark()) R.style.AppThemeDark_NoActionBar else R.style.AppTheme_NoActionBar)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val recentToken = Common.getCurrentAccessToken()

        // NavDrawer内のアカウント情報表示部
        accountHeader = getAccountHeader()

        setNavDrawer()

        // アカウント情報をNavDrawerに追加
        Observable.fromIterable(OrmaProvider.db.selectFromAccessToken())
                .map { token ->
                    val domain = OrmaProvider.db.selectFromInstanceAuthInfo().idEq(token.instanceId).last().instance
                    Pair(domain, token)
                }
                .flatMap { pair ->
                    OrmaProvider.db.updateAccessToken().isCurrentEq(true).isCurrent(false).execute()
                    OrmaProvider.db.updateAccessToken().idEq(pair.second.id).isCurrent(true).execute()
                    MastodonClient(Common.resetAuthInfo() ?: throw IllegalArgumentException()).getAccount(pair.second.accountId)
                            .map { account -> Pair(pair, account) }
                }
                .flatMap { pair ->
                    if (pair.second.avatarUrl.startsWith("http")) {
                        Observable.just(Picasso.with(this).load(pair.second.avatarUrl).get())
                                .map { bitmap -> Pair(pair, bitmap) }
                    } else Observable.just(Pair(pair, null))
                }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ (first, second) ->
                    val item = ProfileDrawerItem()
                            .withName(first.second.displayName)
                            .withEmail("@${first.second.username}@${first.first.first}")
                            .withIdentifier(first.first.second.id)
                    if (second != null) item.withIcon(second)
                    accountHeader.addProfiles(item)
                }, Throwable::printStackTrace, {

                    if (recentToken != null) {
                        OrmaProvider.db.updateAccessToken().isCurrentEq(true).isCurrent(false).executeAsSingle()
                                .flatMap { OrmaProvider.db.updateAccessToken().idEq(recentToken.id).isCurrent(true).executeAsSingle() }
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .compose(bindToLifecycle())
                                .subscribe({}, Throwable::printStackTrace)
                        accountHeader.setActiveProfile(recentToken.id)
                    }

                    supportActionBar?.setDisplayShowHomeEnabled(true)

                    showDefaultTimeline()
                })

        (findViewById(R.id.fab) as FloatingActionButton).setOnClickListener { showCreateNewTootFragment() }
    }

    override fun onResume() {
        super.onResume()

        if (isModeDark()) {
            setTheme(R.style.AppThemeDark_NoActionBar)
            findViewById(R.id.drawer_layout).rootView.setBackgroundResource(R.color.material_gray_dark)
        } else {
            setTheme(R.style.AppTheme_NoActionBar)
            findViewById(R.id.drawer_layout).rootView.setBackgroundResource(R.color.material_gray_light)
        }
        setNavDrawer()
    }

    override fun onBackPressed() {
        if (drawer.isDrawerOpen) {
            drawer.closeDrawer()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_search)?.icon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
        (menu?.findItem(R.id.action_search)?.actionView as SearchView?)?.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextChange(text: String?): Boolean {
                return false
            }

            override fun onQueryTextSubmit(text: String?): Boolean {
                Snackbar.make(findViewById(R.id.container), "Not implemented", Snackbar.LENGTH_SHORT).show()
                return false
            }
        })

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        when (item.itemId) {
        }

        return super.onOptionsItemSelected(item)
    }

    fun getAccountHeader(): AccountHeader =
            AccountHeaderBuilder().withActivity(this)
                    .withHeaderBackground(R.drawable.side_nav_bar)
                    .withOnAccountHeaderListener { v, profile, current ->
                        if (v.id == R.id.material_drawer_account_header_current) {
                            Common.resetAuthInfo()?.let {
                                MastodonClient(it).getSelfAccount()
                                        .subscribeOn(Schedulers.newThread())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .compose(bindToLifecycle())
                                        .subscribe({ account ->
                                            val fragment = AccountProfileFragment.newInstance(account)
                                            supportFragmentManager.beginTransaction()
                                                    .replace(R.id.container, fragment, AccountProfileFragment.TAG)
                                                    .addToBackStack(AccountProfileFragment.TAG)
                                                    .commit()
                                        }, Throwable::printStackTrace)
                            }
                            false
                        } else if (!current) {
                            OrmaProvider.db.updateAccessToken().isCurrentEq(true).isCurrent(false).executeAsSingle()
                                    .flatMap { OrmaProvider.db.updateAccessToken().idEq(profile.identifier).isCurrent(true).executeAsSingle() }
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe({ i ->
                                        Timber.d("updated row count: $i")
                                        val fragment = TimelineFragment.newInstance(TimelineFragment.ARGS_VALUE_PUBLIC)
                                        supportFragmentManager.beginTransaction().replace(R.id.container, fragment, TimelineFragment.TAG).commit()
                                    }, Throwable::printStackTrace)
                            false
                        } else true
                    }
                    .build()

    fun setNavDrawer() {
        val toolbar = findViewById(R.id.toolbar) as Toolbar

        drawer = DrawerBuilder().withActivity(this)
                .withTranslucentStatusBar(false)
                .withActionBarDrawerToggleAnimated(true)
                .withToolbar(toolbar)
                .addDrawerItems(
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_tl_public).withIdentifier(NAV_ITEM_TL_PUBLIC),
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_tl_user).withIdentifier(NAV_ITEM_TL_USER),
                        DividerDrawerItem(),
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_login).withIdentifier(NAV_ITEM_LOGIN),
                        DividerDrawerItem(),
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_settings).withIdentifier(NAV_ITEM_SETTINGS)
                )
                .withOnDrawerItemClickListener { v, position, item ->
                    return@withOnDrawerItemClickListener when (item.identifier) {
                        NAV_ITEM_LOGIN -> {
                            startActivity(LoginActivity.getIntent(this))
                            false
                        }

                        NAV_ITEM_TL_PUBLIC -> {
                            val fragment = supportFragmentManager.findFragmentByTag(TimelineFragment.TAG)
                            if (!(fragment != null
                                    && fragment.isVisible
                                    && (fragment as TimelineFragment).getCategory() == TimelineFragment.ARGS_VALUE_PUBLIC)) {
                                val fmt = TimelineFragment.newInstance(TimelineFragment.ARGS_VALUE_PUBLIC)
                                supportFragmentManager.beginTransaction()
                                        .replace(R.id.container, fmt, TimelineFragment.TAG)
                                        .addToBackStack(TimelineFragment.TAG)
                                        .commit()
                            }
                            false
                        }

                        NAV_ITEM_TL_USER -> {
                            val fragment = supportFragmentManager.findFragmentByTag(TimelineFragment.TAG)
                            if (!(fragment != null
                                    && fragment.isVisible
                                    && (fragment as TimelineFragment).getCategory() == TimelineFragment.ARGS_VALUE_USER)) {
                                val fmt = TimelineFragment.newInstance(TimelineFragment.ARGS_VALUE_USER)
                                supportFragmentManager.beginTransaction()
                                        .replace(R.id.container, fmt, TimelineFragment.TAG)
                                        .addToBackStack(TimelineFragment.TAG)
                                        .commit()
                            }
                            false
                        }

                        NAV_ITEM_SETTINGS -> {

                            val intent = SettingActivity.getIntent(this)
                            startActivity(intent)
                            false
                        }

                        else -> true
                    }
                }
                .withAccountHeader(accountHeader)
                .build()
    }

    fun resetSelectionNavItem(identifier: Long) {
        if (identifier > -1) drawer.setSelection(identifier)
    }

    fun showDefaultTimeline() {
        val fragment = TimelineFragment.newInstance(TimelineFragment.ARGS_VALUE_PUBLIC)
        supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, TimelineFragment.TAG)
                .commit()
        drawer.setSelection(NAV_ITEM_TL_PUBLIC)
    }

    fun showCreateNewTootFragment() {
        val token = Common.getCurrentAccessToken() ?: return
        val fragment = NewTootCreateFragment.newInstance(token.id)
        supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, NewTootCreateFragment.TAG)
                .addToBackStack(NewTootCreateFragment.TAG)
                .commit()
    }

    fun replyStatusById(content: TimelineContent) {
        val fragment = NewTootCreateFragment.newInstance(
                Common.getCurrentAccessToken()?.id ?: return,
                replyToStatusId = content.id,
                replyToAccountName = content.nameWeak)

        supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, NewTootCreateFragment.TAG)
                .addToBackStack(NewTootCreateFragment.TAG)
                .commit()
    }

    fun favStatusById(statusId: Long, view: ImageView) {
        val domain = Common.resetAuthInfo() ?: return
        MastodonClient(domain).getStatusByStatusId(statusId)
                .flatMap { status ->
                    if (status.favourited) MastodonClient(domain).unFavoriteByStatusId(statusId)
                    else MastodonClient(domain).favoriteByStatusId(statusId)
                }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { status ->
                    view.setColorFilter(ContextCompat.getColor(this, if (status.favourited) R.color.colorAccent else R.color.icon_tint_dark))
                }, Throwable::printStackTrace)
    }

    fun boostStatusById(statusId: Long, view: ImageView) {
        val domain = Common.resetAuthInfo() ?: return
        MastodonClient(domain).getStatusByStatusId(statusId)
                .flatMap { status ->
                    if (status.reblogged) MastodonClient(domain).unReblogByStatusId(statusId)
                    else MastodonClient(domain).reblogByStatusId(statusId)
                }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { status ->
                    view.setColorFilter(ContextCompat.getColor(this, if (status.reblogged) R.color.colorAccent else R.color.icon_tint_dark))
                }, Throwable::printStackTrace)
    }
}
