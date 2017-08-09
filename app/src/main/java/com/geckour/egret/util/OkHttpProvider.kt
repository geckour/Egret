package com.geckour.egret.util

import com.facebook.stetho.okhttp3.StethoInterceptor
import com.geckour.egret.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object OkHttpProvider {

    lateinit var client: OkHttpClient
    lateinit var streamClient: OkHttpClient
    val authInterceptor = AuthInterceptor()

    fun init() {
        val builder = OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addNetworkInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.HEADERS))
                addNetworkInterceptor(StethoInterceptor())
            }

            addNetworkInterceptor(authInterceptor)
        }

        client = builder.build()

        builder.readTimeout(0, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS)
        streamClient = builder.build()
    }
}