package com.geckour.egret.view.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceScreen
import com.geckour.egret.R
import com.geckour.egret.view.activity.SettingActivity

class MiscFragment: PreferenceFragmentCompat(), PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    companion object {
        val TAG: String = this::class.java.simpleName

        fun newInstance(): MiscFragment = MiscFragment()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_misc, rootKey)
        preferenceScreen.findPreference("show_licenses").setOnPreferenceClickListener { showLicenseFragment() }
    }

    override fun getCallbackFragment(): Fragment = this

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat?, pref: PreferenceScreen?): Boolean {
        caller?.preferenceScreen = pref
        return true
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        (activity as? SettingActivity)?.binding?.appBarMain?.toolbar?.title = getString(R.string.title_fragment_others)
    }

    private fun showLicenseFragment(): Boolean {
        val fragment = LicenseFragment.newInstance()
        (activity as SettingActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment, LicenseFragment.TAG)
                .addToBackStack(LicenseFragment.TAG)
                .commit()

        return true
    }
}