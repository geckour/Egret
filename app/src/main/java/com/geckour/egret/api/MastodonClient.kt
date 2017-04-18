package com.geckour.egret.api

import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.InstanceAccess
import com.geckour.egret.api.model.Status
import com.geckour.egret.api.model.UserSpecificApp
import com.geckour.egret.api.service.MastodonService
import com.geckour.egret.util.OkHttpProvider
import com.google.gson.GsonBuilder
import io.reactivex.Observable
import io.reactivex.Single
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class MastodonClient(baseUrl: String) {

    private val service = Retrofit.Builder()
            .client(OkHttpProvider.client)
            .baseUrl("https://$baseUrl/")
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create()))
            .build()
            .create(MastodonService::class.java)

    fun registerApp(): Single<UserSpecificApp> = service.registerApp()

    fun authUser(
            clientId: String,
            clientSecret: String,
            username: String,
            password: String
    ): Single<InstanceAccess> = service.authUser(clientId, clientSecret, username, password)

    fun getAccount(): Single<Account> = service.getSelfInfo()

    fun getPublicTimeline(): Observable<List<Status>> = service.getPublicTimeline()
}