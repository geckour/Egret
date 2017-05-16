package com.geckour.egret.view.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
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
import com.geckour.egret.api.model.Relationship
import com.geckour.egret.model.MuteClient
import com.geckour.egret.model.MuteInstance
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.geckour.egret.view.fragment.*
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class MainActivity : BaseActivity() {

    lateinit var drawer: Drawer
    lateinit private var accountHeader: AccountHeader

    companion object {
        val NAV_ITEM_LOGIN: Long = 0
        val NAV_ITEM_TL_PUBLIC: Long = 1
        val NAV_ITEM_TL_LOCAL: Long = 2
        val NAV_ITEM_TL_USER: Long = 3
        val NAV_ITEM_SETTINGS: Long = 4

        fun getIntent(context: Context): Intent {
            val intent = Intent(context, MainActivity::class.java)
            return intent
        }
    }

    val timelineListener = object: TimelineAdapter.IListener {
        override fun showTootInBrowser(content: TimelineContent) {
            val uri = Uri.parse(content.tootUrl)
            if (Common.isModeDefaultBrowser(this@MainActivity)) {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } else {
                Common.getCustomTabsIntent(this@MainActivity).launchUrl(this@MainActivity, uri)
            }
        }

        override fun copyTootToClipboard(content: TimelineContent) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("toot", content.body.toString())
            clipboard.primaryClip = clip
        }

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
                                    Common.resetAuthInfo()?.let { domain ->
                                        MastodonClient(domain).getSelfAccount()
                                                .flatMap { if (it.id == content.accountId) Single.never() else MastodonClient(domain).muteAccount(content.accountId) }
                                                .subscribeOn(Schedulers.newThread())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe({
                                                    Snackbar.make(findViewById(R.id.container), "Muted account: ${content.nameWeak}", Snackbar.LENGTH_SHORT).show()
                                                }, Throwable::printStackTrace)
                                    }
                                }

                                R.string.array_item_mute_keyword -> {
                                    val fragment = KeywordMuteFragment.newInstance(content.body.toString())
                                    supportFragmentManager.beginTransaction()
                                            .replace(R.id.container, fragment, KeywordMuteFragment.TAG)
                                            .addToBackStack(KeywordMuteFragment.TAG)
                                            .commit()
                                }

                                R.string.array_item_mute_hash_tag -> {
                                    val fragment = HashTagMuteFragment.newInstance(content.tags)
                                    supportFragmentManager.beginTransaction()
                                            .replace(R.id.container, fragment, HashTagMuteFragment.TAG)
                                            .addToBackStack(HashTagMuteFragment.TAG)
                                            .commit()
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
                    MastodonClient(Common.setAuthInfo(pair.second) ?: throw IllegalArgumentException()).getAccount(pair.second.accountId)
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
                    Common.getCurrentAccessToken()?.id?.let { accountHeader.setActiveProfile(it) }
                    supportActionBar?.setDisplayShowHomeEnabled(true)

                    if (savedInstanceState == null) showDefaultTimeline()
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

        when (item.itemId) {}

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
                                        showDefaultTimeline()
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
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_tl_public).withIdentifier(NAV_ITEM_TL_PUBLIC).withIcon(R.drawable.ic_public_black_24px).withIconTintingEnabled(true).withIconColorRes(R.color.icon_tint_dark),
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_tl_local).withIdentifier(NAV_ITEM_TL_LOCAL).withIcon(R.drawable.ic_place_black_24px).withIconTintingEnabled(true).withIconColorRes(R.color.icon_tint_dark),
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_tl_user).withIdentifier(NAV_ITEM_TL_USER).withIcon(R.drawable.ic_mood_black_24px).withIconTintingEnabled(true).withIconColorRes(R.color.icon_tint_dark),
                        DividerDrawerItem(),
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_login).withIdentifier(NAV_ITEM_LOGIN).withIcon(R.drawable.ic_person_add_black_24px).withIconTintingEnabled(true).withIconColorRes(R.color.icon_tint_dark),
                        DividerDrawerItem(),
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_settings).withIdentifier(NAV_ITEM_SETTINGS).withIcon(R.drawable.ic_settings_black_24px).withIconTintingEnabled(true).withIconColorRes(R.color.icon_tint_dark)
                )
                .withOnDrawerItemClickListener { v, position, item ->
                    return@withOnDrawerItemClickListener when (item.identifier) {
                        NAV_ITEM_LOGIN -> {
                            startActivity(LoginActivity.getIntent(this))
                            false
                        }

                        NAV_ITEM_TL_PUBLIC -> {
                            showTimelineFragment(TimelineFragment.Category.Public)
                            false
                        }

                        NAV_ITEM_TL_LOCAL -> {
                            showTimelineFragment(TimelineFragment.Category.Local)
                            false
                        }

                        NAV_ITEM_TL_USER -> {
                            showTimelineFragment(TimelineFragment.Category.User)
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

    fun showTimelineFragment(category: TimelineFragment.Category, setNavSelection: Boolean = false, force: Boolean = false) {
        val currentFragment = supportFragmentManager.findFragmentByTag(TimelineFragment.TAG)
        if (!force
                && currentFragment != null
                && currentFragment.isVisible
                && (currentFragment as TimelineFragment).getCategory() == category) return

        val fragment = TimelineFragment.newInstance(category)
        supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, TimelineFragment.TAG)
                .addToBackStack(TimelineFragment.TAG)
                .commit()
        if (setNavSelection) when (category) {
            TimelineFragment.Category.Public -> drawer.setSelection(NAV_ITEM_TL_PUBLIC)
            TimelineFragment.Category.Local -> drawer.setSelection(NAV_ITEM_TL_LOCAL)
            TimelineFragment.Category.User -> drawer.setSelection(NAV_ITEM_TL_USER)
        }
    }

    fun resetSelectionNavItem(identifier: Long) {
        if (identifier > -1) drawer.setSelection(identifier)
    }

    fun showDefaultTimeline(force: Boolean = false) {
        showTimelineFragment(TimelineFragment.Category.Public, true, force)
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
