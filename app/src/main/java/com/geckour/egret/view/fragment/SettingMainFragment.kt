package com.geckour.egret.view.fragment

import android.os.Bundle
import android.support.v14.preference.SwitchPreference
import android.support.v4.app.Fragment
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceScreen
import com.geckour.egret.App
import com.geckour.egret.R
import com.geckour.egret.view.activity.SettingActivity

class SettingMainFragment: PreferenceFragmentCompat(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    companion object {
        val TAG: String = this::class.java.simpleName

        fun newInstance(): SettingMainFragment = SettingMainFragment()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey)
        preferenceScreen.findPreference("manage_accounts").setOnPreferenceClickListener { showAccountManageFragment() }
        preferenceScreen.findPreference("manage_restrictions").setOnPreferenceClickListener { showRestrictFragment() }
    }

    override fun getCallbackFragment(): Fragment = this

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat?, pref: PreferenceScreen?): Boolean {
        caller?.preferenceScreen = pref
        return true
    }

    override fun onPause() {
        super.onPause()

        (preferenceScreen.findPreference("manage_notify_real_time") as SwitchPreference).apply {
            if (isChecked) {
                activity.startService((activity.application as App).intent)
            } else {
                activity.stopService((activity.application as App).intent)
            }
        }
    }

    private fun showAccountManageFragment(): Boolean {
        val fragment = AccountManageFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, AccountManageFragment.TAG)
                .addToBackStack(AccountManageFragment.TAG)
                .commit()

        return true
    }

    private fun showRestrictFragment(): Boolean {
        val fragment = SettingRestrictFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, SettingRestrictFragment.TAG)
                .addToBackStack(SettingRestrictFragment.TAG)
                .commit()

        return true
    }
}