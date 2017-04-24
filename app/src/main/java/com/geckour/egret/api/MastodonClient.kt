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
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class MastodonClient(baseUrl: String) {

    private val service = Retrofit.Builder()
            .client(OkHttpProvider.client)
            .baseUrl("https://$baseUrl/")
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(MastodonService::class.java)

    fun registerApp(): Single<UserSpecificApp> = service.registerApp()

    fun authUser(
            clientId: String,
            clientSecret: String,
            username: String,
            password: String
    ): Single<InstanceAccess> = service.authUser(clientId, clientSecret, username, password)

    fun getSelfAccount(): Single<Account> = service.getSelfAccount()

    fun getAccount(accountId: Long): Observable<Account> = service.getAccount(accountId)

    fun getPublicTimeline(): Observable<ResponseBody> = service.getPublicTimeline()

    fun getUserTimeline(): Observable<ResponseBody> = service.getUserTimeline()

    fun getAccountAllToots(accountId: Long): Single<List<Status>> = service.getAccountAllToots(accountId)

    fun favoriteByStatusId(statusId: Long): Single<Status> = service.favoriteStatusById(statusId)

    fun unFavoriteByStatusId(statusId: Long): Single<Status> = service.unFavoriteStatusById(statusId)

    fun reblogByStatusId(statusId: Long): Single<Status> = service.reblogStatusById(statusId)

    fun unReblogByStatusId(statusId: Long): Single<Status> = service.unReblogStatusById(statusId)

    fun getStatusByStatusId(statusId: Long): Single<Status> = service.getStatusById(statusId)

    fun postNewToot(
            body: String,
            inReplyToId: Long? = null,
            mediaIds: List<Long>? = null,
            isSensitive: Boolean? = null,
            spoilerText: String? = null,
            visibility: MastodonService.Visibility? = null
    ): Single<Status> = service.postNewToot(body, inReplyToId, mediaIds, isSensitive, spoilerText, visibility?.name)
}