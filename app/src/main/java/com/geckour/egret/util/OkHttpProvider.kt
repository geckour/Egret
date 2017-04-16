package com.geckour.egret.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object OkHttpProvider {

    lateinit var client: OkHttpClient
    val authInterceptor = AuthInterceptor()

    fun init() {
        val logging = HttpLoggingInterceptor( { message -> Log.d("OkHttp", message) } )
        logging.level = HttpLoggingInterceptor.Level.BODY
        client = OkHttpClient.Builder().addInterceptor(logging).addInterceptor(authInterceptor).build()
    }
}