package com.geckour.egret.view.activity

import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import com.geckour.egret.R
import com.geckour.egret.databinding.ActivitySplashBinding
import com.geckour.egret.util.Common
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper

class SplashActivity : BaseActivity() {

    lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_splash)

        Common.hasCertified { hasCertified, accountId ->
            val intent = if (hasCertified) MainActivity.getIntent(this@SplashActivity) else LoginActivity.getIntent(this@SplashActivity)
            startActivity(intent)
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase))
    }
}
