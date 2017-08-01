package com.geckour.egret.view.activity

import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.Toolbar
import com.geckour.egret.R
import com.geckour.egret.databinding.ActivityMainBinding
import com.geckour.egret.view.fragment.SettingMainFragment

class SettingActivity: BaseActivity() {

    lateinit var binding: ActivityMainBinding

    companion object {
        fun getIntent(context: Context): Intent {
            val intent = Intent(context, SettingActivity::class.java)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(if (isModeDark()) R.style.AppThemeDark_NoActionBar else R.style.AppTheme_NoActionBar)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        binding.appBarMain.contentMain.fab.hide()

        supportFragmentManager.beginTransaction()
                .replace(R.id.container, SettingMainFragment.newInstance(), SettingMainFragment.TAG)
                .commit()
    }
}