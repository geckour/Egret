package com.geckour.egret.view.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.design.widget.NavigationView
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.model.TimelineContent
import com.geckour.egret.view.fragment.AccountProfileFragment
import com.geckour.egret.view.fragment.NewTootCreateFragment
import com.geckour.egret.view.fragment.TimelineFragment
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class MainActivity : BaseActivity() {

    lateinit var drawer: Drawer

    companion object {
        val NAV_ITEM_LOGIN: Long = 0
        val NAV_ITEM_TL_PUBLIC: Long = 1
        val NAV_ITEM_TL_USER: Long = 2

        fun getIntent(context: Context): Intent {
            val intent = Intent(context, MainActivity::class.java)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val recentToken = Common.getCurrentAccessToken()

        // NavDrawer内のアカウント情報表示部
        val accountHeader = AccountHeaderBuilder().withActivity(this)
                .withHeaderBackground(R.drawable.side_nav_bar)
                .withOnAccountHeaderListener { v, profile, current ->
                    val token = Common.resetAuthInfo()
                    if (token != null && v.id == R.id.material_drawer_account_header_current) {
                        MastodonClient(token).getSelfAccount()
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

        drawer = DrawerBuilder().withActivity(this)
                .withSavedInstance(savedInstanceState)
                .withTranslucentStatusBar(false)
                .withActionBarDrawerToggleAnimated(true)
                .withToolbar(toolbar)
                .addDrawerItems(
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_tl_public).withIdentifier(NAV_ITEM_TL_PUBLIC),
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_tl_user).withIdentifier(NAV_ITEM_TL_USER),
                        DividerDrawerItem(),
                        PrimaryDrawerItem().withName(R.string.navigation_drawer_item_login).withIdentifier(NAV_ITEM_LOGIN)
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

                        else -> true
                    }
                }
                .withAccountHeader(accountHeader)
                .build()

        // アカウント情報をNavDrawerに追加してNavDrawerを表示
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
                .subscribe({ pair ->
                    val item = ProfileDrawerItem()
                            .withName(pair.first.second.displayName)
                            .withEmail("@${pair.first.second.username}@${pair.first.first.first}")
                            .withIdentifier(pair.first.first.second.id)
                    if (pair.second != null) item.withIcon(pair.second)
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

    override fun onBackPressed() {
        if (drawer.isDrawerOpen) {
            drawer.closeDrawer()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId


        if (id == R.id.action_settings) {
            return true
        }

        return super.onOptionsItemSelected(item)
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
