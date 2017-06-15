package com.geckour.egret.view.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceScreen
import com.geckour.egret.R
import com.geckour.egret.view.activity.SettingActivity

class SettingMainFragment: PreferenceFragmentCompat(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    companion object {
        val TAG: String = this::class.java.simpleName

        fun newInstance(): SettingMainFragment {
            val fragment = SettingMainFragment()

            return fragment
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey)
        preferenceScreen.findPreference("manage_accounts").setOnPreferenceClickListener { showAccountManageFragment() }
        preferenceScreen.findPreference("manage_restrictions").setOnPreferenceClickListener { showRestrictFragment() }
    }

    override fun getCallbackFragment(): Fragment {
        return this
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat?, pref: PreferenceScreen?): Boolean {
        caller?.preferenceScreen = pref
        return true
    }

    fun showAccountManageFragment(): Boolean {
        val fragment = AccountManageFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, AccountManageFragment.TAG)
                .addToBackStack(AccountManageFragment.TAG)
                .commit()

        return true
    }

    fun showRestrictFragment(): Boolean {
        val fragment = SettingRestrictFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, SettingRestrictFragment.TAG)
                .addToBackStack(SettingRestrictFragment.TAG)
                .commit()

        return true
    }
}