package com.geckour.egret.view.fragment

import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat
import com.geckour.egret.R

class SettingMainFragment: PreferenceFragmentCompat() {

    companion object {
        fun newInstance(): SettingMainFragment {
            val fragment = SettingMainFragment()

            return fragment
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey)
    }
}