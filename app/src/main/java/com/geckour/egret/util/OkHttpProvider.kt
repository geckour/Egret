package com.geckour.egret.util

import com.facebook.stetho.okhttp3.StethoInterceptor
import com.geckour.egret.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object OkHttpProvider {

    lateinit var client: OkHttpClient
    val authInterceptor = AuthInterceptor()

    fun init() {
        val builder : OkHttpClient.Builder = OkHttpClient.Builder();
        if (BuildConfig.DEBUG) {
            builder.addNetworkInterceptor(StethoInterceptor())
            builder.addNetworkInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        }

        client = builder.addInterceptor(authInterceptor).build()
    }
}