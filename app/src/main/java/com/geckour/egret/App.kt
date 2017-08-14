package com.geckour.egret

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.text.Spanned
import com.facebook.stetho.Stetho
import com.geckour.egret.util.OkHttpProvider
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.adapter.model.adapter.SpannedTypeAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import paperparcel.Adapter
import paperparcel.ProcessorConfig
import timber.log.Timber

@ProcessorConfig(adapters = arrayOf(Adapter(SpannedTypeAdapter::class)))
class App: Application() {

    companion object {
        val STATE_KEY_CATEGORY = "timelineCategory"
        val gson: Gson = GsonBuilder().apply {
            registerTypeAdapter(Spanned::class.java, SpannedTypeAdapter())
        }.create()
    }

    val intent: Intent by lazy { NotificationService.getIntent(this) }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Stetho.initializeWithDefaults(this)
        }

        OkHttpProvider.init()
        OrmaProvider.init(this)

        startService(intent)
        registerReceiver(DeleteNotificationReceiver(), IntentFilter(DeleteNotificationReceiver.ACTION_DELETE_NOTIFICATION))
    }
}