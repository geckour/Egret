package com.geckour.egret.util

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    private var token: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        if (token == null) builder.method(original.method(), original.body())
        else builder.header("Authorization", "Bearer $token")

        return chain.proceed(builder.build())
    }

    fun setToken(token: String) {
        this.token = token
    }

    fun getToken(): String? = this.token
}