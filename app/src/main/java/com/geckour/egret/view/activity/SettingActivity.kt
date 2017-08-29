package com.geckour.egret.view.activity

import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.Toolbar
import com.geckour.egret.R
import com.geckour.egret.databinding.ActivityMainBinding
import com.geckour.egret.view.fragment.MiscFragment
import com.geckour.egret.view.fragment.SettingMainFragment
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper

class SettingActivity: BaseActivity() {

    enum class Type {
        Preference,
        Misc
    }

    companion object {
        private val ARGS_KEY_TYPE = "argsKeyType"
        fun getIntent(context: Context, type: Type) = Intent(context, SettingActivity::class.java)
                .apply {
                    putExtra(ARGS_KEY_TYPE, type)
                }
    }

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(if (isModeDark()) R.style.AppTheme_Dark_NoActionBar else R.style.AppTheme_NoActionBar)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        binding.appBarMain.contentMain.fab.hide()

        if (savedInstanceState == null) {
            if (intent.hasExtra(ARGS_KEY_TYPE)) {
                when (intent.extras[ARGS_KEY_TYPE]) {
                    Type.Preference -> {
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.container, SettingMainFragment.newInstance(), SettingMainFragment.TAG)
                                .commit()
                    }

                    Type.Misc -> {
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.container, MiscFragment.newInstance(), MiscFragment.TAG)
                                .commit()
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase))
    }
}