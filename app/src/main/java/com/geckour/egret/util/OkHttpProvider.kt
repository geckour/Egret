package com.geckour.egret.util

import com.facebook.stetho.okhttp3.StethoInterceptor
import com.geckour.egret.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object OkHttpProvider {

    lateinit var client: OkHttpClient
    val authInterceptor = AuthInterceptor()

    fun init() {
        val builder : OkHttpClient.Builder = OkHttpClient.Builder()
        builder.readTimeout(0, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addNetworkInterceptor(StethoInterceptor())
            builder.addNetworkInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.HEADERS))
            builder.addNetworkInterceptor(authInterceptor)
        }

        client = builder.build()
    }
}