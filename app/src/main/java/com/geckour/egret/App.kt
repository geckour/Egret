package com.geckour.egret

import android.app.Application
import com.geckour.egret.util.OkHttpProvider
import com.geckour.egret.util.OrmaProvider
import timber.log.BuildConfig
import timber.log.Timber

class App: Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        OkHttpProvider.init()
        OrmaProvider.init(this)
    }
}