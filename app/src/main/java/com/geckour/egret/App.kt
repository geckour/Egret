package com.geckour.egret

import android.app.Application
import com.facebook.stetho.Stetho
import com.geckour.egret.util.OkHttpProvider
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.model.adapter.SpannedTypeAdapter
import paperparcel.Adapter
import paperparcel.ProcessorConfig
import timber.log.Timber

@ProcessorConfig(adapters = arrayOf(Adapter(SpannedTypeAdapter::class)))
class App: Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Stetho.initializeWithDefaults(this)
        }

        OkHttpProvider.init()
        OrmaProvider.init(this)
    }
}