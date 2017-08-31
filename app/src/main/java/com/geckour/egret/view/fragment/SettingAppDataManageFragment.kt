package com.geckour.egret.view.fragment

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceManager
import android.support.v7.preference.PreferenceScreen
import com.geckour.egret.App
import com.geckour.egret.R
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.SettingActivity
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class SettingAppDataManageFragment: PreferenceFragmentCompat(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    companion object {
        val TAG: String = this::class.java.simpleName

        fun newInstance(): SettingAppDataManageFragment  = SettingAppDataManageFragment()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_app_data, rootKey)

        preferenceScreen.findPreference("clear_tl_cache").setOnPreferenceClickListener { clearTlCache() }
        preferenceScreen.findPreference("clear_all_preference").setOnPreferenceClickListener { clearAllPreference() }
        preferenceScreen.findPreference("clear_all_draft").setOnPreferenceClickListener { clearAllDraft() }
        preferenceScreen.findPreference("clear_all_restriction").setOnPreferenceClickListener { clearAllRestriction() }
        preferenceScreen.findPreference("clear_all_db_except_login_info").setOnPreferenceClickListener { clearAllDBExceptLoginInfo() }
        preferenceScreen.findPreference("clear_all_db").setOnPreferenceClickListener { clearAllDB() }
    }

    override fun getCallbackFragment(): Fragment = this

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat?, pref: PreferenceScreen?): Boolean {
        caller?.preferenceScreen = pref
        return true
    }

    private fun clearTlCache(): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPref.edit()
                .apply {
                    if (sharedPref.contains(App.STATE_KEY_CATEGORY)) remove(App.STATE_KEY_CATEGORY)
                    if (sharedPref.contains(TimelineFragment.STATE_KEY_HASH_TAG)) remove(TimelineFragment.STATE_KEY_HASH_TAG)
                    TimelineFragment.Category.values()
                            .forEach {
                                val key = Common.getStoreContentsKey(it)
                                if (sharedPref.contains(key)) remove(key)
                            }
                }
                .apply()
        Snackbar.make((activity as SettingActivity).binding.root, R.string.message_complete_clear_tl_cache, Snackbar.LENGTH_SHORT).show()
        return true
    }

    private fun clearAllPreference(): Boolean {
        PreferenceManager.getDefaultSharedPreferences(activity).edit().clear().apply()
        Snackbar.make((activity as SettingActivity).binding.root, R.string.message_complete_clear_pref, Snackbar.LENGTH_SHORT).show()
        return true
    }

    private fun clearAllDraft(): Boolean {
        OrmaProvider.db.deleteFromDraft().executeAsSingle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Snackbar.make((activity as SettingActivity).binding.root, R.string.message_complete_clear_db, Snackbar.LENGTH_SHORT).show()
                }, Throwable::printStackTrace)
        return true
    }

    private fun clearAllRestriction(): Boolean {
        Observable.merge(
                listOf(
                        OrmaProvider.db.deleteFromMuteClient().executeAsSingle().toObservable(),
                        OrmaProvider.db.deleteFromMuteHashTag().executeAsSingle().toObservable(),
                        OrmaProvider.db.deleteFromMuteInstance().executeAsSingle().toObservable(),
                        OrmaProvider.db.deleteFromMuteKeyword().executeAsSingle().toObservable()
                )
        )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, Throwable::printStackTrace, {
                    Snackbar.make((activity as SettingActivity).binding.root, R.string.message_complete_clear_db, Snackbar.LENGTH_SHORT).show()
                })
        return true
    }

    private fun clearAllDBExceptLoginInfo(): Boolean {
        Observable.merge(
                listOf(
                        OrmaProvider.db.deleteFromDraft().executeAsSingle().toObservable(),
                        OrmaProvider.db.deleteFromMuteClient().executeAsSingle().toObservable(),
                        OrmaProvider.db.deleteFromMuteHashTag().executeAsSingle().toObservable(),
                        OrmaProvider.db.deleteFromMuteInstance().executeAsSingle().toObservable(),
                        OrmaProvider.db.deleteFromMuteKeyword().executeAsSingle().toObservable()
                )
        )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, Throwable::printStackTrace, {
                    Snackbar.make((activity as SettingActivity).binding.root, R.string.message_complete_clear_db, Snackbar.LENGTH_SHORT).show()
                })
        return true
    }

    private fun clearAllDB(): Boolean {
        Single.just(OrmaProvider.db)
                .map { it.deleteAll() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Snackbar.make((activity as SettingActivity).binding.root, R.string.message_complete_clear_db, Snackbar.LENGTH_SHORT).show()
                }, Throwable::printStackTrace)

        val intent = activity.baseContext.packageManager.getLaunchIntentForPackage(activity.baseContext.packageName)
                .apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        startActivity(intent)

        return true
    }
}