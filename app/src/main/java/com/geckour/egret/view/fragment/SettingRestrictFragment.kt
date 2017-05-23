package com.geckour.egret.view.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceScreen
import com.geckour.egret.R
import com.geckour.egret.view.activity.SettingActivity

class SettingRestrictFragment: PreferenceFragmentCompat(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    companion object {
        val TAG = "settingRestrictFragment"

        fun newInstance(): SettingRestrictFragment {
            val fragment = SettingRestrictFragment()

            return fragment
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_restrict, rootKey)

        preferenceScreen.findPreference("manage_mute_accounts").setOnPreferenceClickListener { showMuteAccountList() }
        preferenceScreen.findPreference("manage_mute_keywords").setOnPreferenceClickListener { showMuteKeywordList() }
        preferenceScreen.findPreference("manage_mute_hash_tags").setOnPreferenceClickListener { showMuteHashTagList() }
        preferenceScreen.findPreference("manage_mute_instances").setOnPreferenceClickListener { showMuteInstanceList() }

        preferenceScreen.findPreference("manage_block_accounts").setOnPreferenceClickListener { showBlockAccountList() }
    }

    override fun getCallbackFragment(): Fragment {
        return this
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat?, pref: PreferenceScreen?): Boolean {
        caller?.preferenceScreen = pref
        return true
    }

    fun showMuteAccountList(): Boolean {
        val fragment = AccountMuteFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, AccountMuteFragment.TAG)
                .addToBackStack(AccountMuteFragment.TAG)
                .commit()

        return true
    }

    fun showMuteKeywordList(): Boolean {
        val fragment = KeywordMuteFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, KeywordMuteFragment.TAG)
                .addToBackStack(KeywordMuteFragment.TAG)
                .commit()

        return true
    }

    fun showMuteHashTagList(): Boolean {
        val fragment = HashTagMuteFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, HashTagMuteFragment.TAG)
                .addToBackStack(HashTagMuteFragment.TAG)
                .commit()

        return true
    }

    fun showMuteInstanceList(): Boolean {
        val fragment = InstanceMuteFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, InstanceMuteFragment.TAG)
                .addToBackStack(InstanceMuteFragment.TAG)
                .commit()

        return true
    }

    fun showBlockAccountList(): Boolean {
        val fragment = AccountBlockFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, AccountBlockFragment.TAG)
                .addToBackStack(AccountBlockFragment.TAG)
                .commit()

        return true
    }
}