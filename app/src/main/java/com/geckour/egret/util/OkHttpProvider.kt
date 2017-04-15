package com.geckour.egret.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object OkHttpProvider {

    lateinit var client: OkHttpClient

    fun init() {
        val logging = HttpLoggingInterceptor( { message -> Log.d("OkHttp", message) } )
        logging.level = HttpLoggingInterceptor.Level.BASIC
        client = OkHttpClient.Builder().addInterceptor(logging).build()
    }
}