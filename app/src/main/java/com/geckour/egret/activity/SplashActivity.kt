package com.geckour.egret.activity

import android.content.Context
import android.os.Bundle
import com.geckour.egret.R
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper

class SplashActivity : RxAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase))
    }
}
