package com.geckour.egret.view.activity

import android.content.Context
import android.os.Bundle
import com.geckour.egret.R
import com.geckour.egret.util.Common
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper

class SplashActivity : RxAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)

        Common.hasCertified(object: Common.Companion.IListener {
            override fun onCheckCertify(hasCertified: Boolean) {
                val intent = if (hasCertified) MainActivity.getIntent(this@SplashActivity) else LoginActivity.getIntent(this@SplashActivity)
                startActivity(intent)
            }
        })
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase))
    }
}
