package com.geckour.egret.view.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceScreen
import com.geckour.egret.R
import com.geckour.egret.view.activity.SettingActivity

class SettingMainFragment: PreferenceFragmentCompat(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    companion object {
        val TAG = "settingMainFragment"

        fun newInstance(): SettingMainFragment {
            val fragment = SettingMainFragment()

            return fragment
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey)
        preferenceScreen.findPreference("manage_mute_keywords").setOnPreferenceClickListener { showMuteKeywordList() }
        preferenceScreen.findPreference("manage_mute_hash_tags").setOnPreferenceClickListener { showMuteHashTagList() }
    }

    override fun getCallbackFragment(): Fragment {
        return this
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat?, pref: PreferenceScreen?): Boolean {
        caller?.preferenceScreen = pref
        return true
    }

    fun showMuteKeywordList(): Boolean {
        val fragment = KeywordMuteFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, KeywordMuteFragment.TAG)
                .commit()

        return true
    }

    fun showMuteHashTagList(): Boolean {
        val fragment = HashTagMuteFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, HashTagMuteFragment.TAG)
                .commit()

        return true
    }
}