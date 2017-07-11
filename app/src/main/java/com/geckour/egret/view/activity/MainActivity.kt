package com.geckour.egret.view.activity

import android.content.*
import android.databinding.DataBindingUtil
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar
import android.text.Html
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import com.geckour.egret.App
import com.geckour.egret.App.Companion.STATE_KEY_CATEGORY
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.databinding.ActivityMainBinding
import com.geckour.egret.model.MuteClient
import com.geckour.egret.model.MuteInstance
import com.geckour.egret.util.Common
import com.geckour.egret.util.Common.Companion.getStoreContentsKey
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.TimelineContent
import com.geckour.egret.view.fragment.*
import com.geckour.egret.view.fragment.TimelineFragment.Companion.STATE_ARGS_KEY_CONTENTS
import com.geckour.egret.view.fragment.TimelineFragment.Companion.STATE_ARGS_KEY_RESUME
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

    lateinit var binding: ActivityMainBinding
    lateinit var drawer: Drawer
    lateinit private var accountHeader: AccountHeader
    lateinit private var sharedPref: SharedPreferences
    lateinit private var currentCategory: TimelineFragment.Category

    companion object {
        val STATE_KEY_THEME_MODE = "stateKeyThemeMode"
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

    val timelineListener = object: TimelineAdapter.Callbacks {
        override val showTootInBrowser = { content: TimelineContent ->
            val uri = Uri.parse(content.tootUrl)
            if (Common.isModeDefaultBrowser(this@MainActivity)) {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } else {
                Common.getCustomTabsIntent(this@MainActivity).launchUrl(this@MainActivity, uri)
            }
        }

        override val copyTootToClipboard = { content: TimelineContent ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("toot", content.body.toString())
            clipboard.primaryClip = clip
        }

        override val showMuteDialog = { content: TimelineContent ->
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
                                                    Snackbar.make(binding.root, "Muted account: ${content.nameWeak}", Snackbar.LENGTH_SHORT).show()
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
                                                    Snackbar.make(binding.root, "Muted instance: $instance", Snackbar.LENGTH_SHORT).show()
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
                                                    Snackbar.make(binding.root, "Muted client: ${content.app}", Snackbar.LENGTH_SHORT).show()
                                                }, Throwable::printStackTrace)
                                    }
                                }
                            }
                        }
                    }).show(supportFragmentManager, ListDialogFragment.TAG)
        }

        override val showProfile = { accountId: Long ->
            AccountProfileFragment.newObservableInstance(accountId)
                    .subscribe( {
                        fragment ->
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.container, fragment, AccountProfileFragment.TAG)
                                .addToBackStack(AccountProfileFragment.TAG)
                                .commit()
                    }, Throwable::printStackTrace)
        }

        override val onReply = { content: TimelineContent ->
            replyStatusById(content)
        }

        override val onFavStatus = { statusId: Long, view: ImageView ->
            favStatusById(statusId, view)
        }

        override val onBoostStatus = { statusId: Long, view: ImageView ->
            boostStatusById(statusId, view)
        }

        override val onClickMedia = { urls: List<String>, position: Int ->
            val fragment = ShowImagesDialogFragment.newInstance(urls, position)
            supportFragmentManager.beginTransaction()
                    .add(fragment, ShowImagesDialogFragment.TAG)
                    .addToBackStack(ShowImagesDialogFragment.TAG)
                    .commit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(if (isModeDark()) R.style.AppThemeDark_NoActionBar else R.style.AppTheme_NoActionBar)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        setSupportActionBar(binding.appBarMain.toolbar)

        // NavDrawer内のアカウント情報表示部
        accountHeader = getAccountHeader()

        setNavDrawer()
        commitAccountsIntoAccountHeader()

        binding.appBarMain.contentMain.fab.setOnClickListener { showCreateNewTootFragment() }
    }

    override fun onResume() {
        super.onResume()

        if (sharedPref.contains(STATE_KEY_THEME_MODE)) {
            if (sharedPref.getBoolean(STATE_KEY_THEME_MODE, false) != isModeDark()) {
                recreate().apply {
                    sharedPref.edit().remove(STATE_KEY_THEME_MODE).apply()
                }
            }

            sharedPref.edit().remove(STATE_KEY_THEME_MODE).apply()
        }

        currentCategory =
                if (sharedPref.contains(STATE_KEY_CATEGORY)) TimelineFragment.Category.values()[sharedPref.getInt(STATE_KEY_CATEGORY, TimelineFragment.Category.Public.rawValue)]
                else TimelineFragment.Category.Public
    }

    override fun onPause() {
        super.onPause()

        sharedPref.edit()
                .putBoolean(STATE_KEY_THEME_MODE, isModeDark())
                .apply()
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
                Snackbar.make(binding.root, "Not implemented", Snackbar.LENGTH_SHORT).show()
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

    fun commitAccountsIntoAccountHeader() {
        accountHeader.clear()

        Observable.fromIterable(OrmaProvider.db.selectFromAccessToken())
                .flatMap {
                    MastodonClient(Common.setAuthInfo(it) ?: throw IllegalArgumentException()).getSelfAccount()
                            .map { account -> Pair(it, account) }
                            .toObservable()
                }
                .flatMap { (token, account) ->
                    (if (account.avatarUrl.startsWith("http")) Picasso.with(this).load(account.avatarUrl).get() else null)
                            .let {
                                Observable.just(it)
                                        .map { bitmap -> Pair(Pair(token, account), bitmap) }
                            }
                }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ (pair, bitmap) ->
                    val domain = OrmaProvider.db.selectFromInstanceAuthInfo().idEq(pair.first.instanceId).last().instance
                    val item = ProfileDrawerItem()
                            .withName(pair.second.displayName)
                            .withEmail("@${pair.second.username}@$domain")
                            .withIdentifier(pair.second.id)
                    bitmap?.let { item.withIcon(it) }
                    accountHeader.addProfiles(item)
                }, Throwable::printStackTrace, {
                    val currentAccessToken = Common.getCurrentAccessToken()
                    if (currentAccessToken == null) {
                        Single.just(OrmaProvider.db.selectFromAccessToken().last())
                                .flatMap { token ->
                                    OrmaProvider.db.updateAccessToken()
                                            .idEq(token.id)
                                            .isCurrent(true)
                                            .executeAsSingle()
                                            .map { token }
                                }
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({
                                    Timber.d("updated access token: ${it.token}")
                                    accountHeader.setActiveProfile(it.accountId)
                                }, Throwable::printStackTrace)
                    } else {
                        accountHeader.setActiveProfile(currentAccessToken.accountId)
                    }
                    supportActionBar?.setDisplayShowHomeEnabled(true)

                    showTimelineFragment()
                })
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
                            return@withOnAccountHeaderListener false
                        } else if (!current) {
                            OrmaProvider.db.updateAccessToken().isCurrentEq(true).isCurrent(false).executeAsSingle()
                                    .flatMap { OrmaProvider.db.updateAccessToken().accountIdEq(profile.identifier).isCurrent(true).executeAsSingle() }
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe({ i ->
                                        Timber.d("updated row count: $i")

                                        supportFragmentManager.beginTransaction()
                                                .apply {
                                                    val editor = sharedPref.edit()
                                                    editor.putBoolean(STATE_ARGS_KEY_RESUME, false)
                                                    listOf(TimelineFragment.Category.Public, TimelineFragment.Category.Local, TimelineFragment.Category.User)
                                                            .forEach {
                                                                supportFragmentManager.findFragmentByTag(it.name)?.let { fragment ->
                                                                    detach(fragment)
                                                                    remove(fragment)
                                                                }
                                                            }
                                                    editor.apply()
                                                }
                                                .commit()

                                        showTimelineFragment(force = true)
                                    }, Throwable::printStackTrace)
                            return@withOnAccountHeaderListener false
                        }

                        return@withOnAccountHeaderListener true
                    }
                    .build()

    fun setNavDrawer() {
        drawer = DrawerBuilder().withActivity(this)
                .withTranslucentStatusBar(false)
                .withActionBarDrawerToggleAnimated(true)
                .withToolbar(binding.appBarMain.toolbar)
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

    fun showTimelineFragment(category: TimelineFragment.Category = currentCategory, force: Boolean = false) {
        val reqFragment = supportFragmentManager.findFragmentByTag(category.name)
        val currentFragment = supportFragmentManager.findFragmentByTag(currentCategory.name)

        if (!force
                && currentFragment != null
                && currentFragment.isVisible
                && currentFragment.tag == category.name) return // 要求されたカテゴリを現在表示している場合は早期return

        supportFragmentManager.beginTransaction()
                .apply {
                    if (currentFragment != null && currentFragment.tag != category.name) detach(currentFragment)
                    if (reqFragment == null) {
                        val fragment = TimelineFragment.newInstance(category)
                        replace(R.id.container, fragment, category.name)
                    } else {
                        attach(reqFragment)
                    }
                }
                .addToBackStack(category.name)
                .commit()

        currentCategory = category
    }

    fun resetSelectionNavItem(identifier: Long) {
        if (identifier > -1 && drawer.currentSelection != identifier) drawer.setSelection(identifier)
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
