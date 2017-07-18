package com.geckour.egret.api

import com.geckour.egret.App.Companion.gson
import com.geckour.egret.api.model.*
import com.geckour.egret.api.service.MastodonService
import com.geckour.egret.util.OkHttpProvider
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.Result
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class MastodonClient(baseUrl: String) {

    private val service = Retrofit.Builder()
            .client(OkHttpProvider.client)
            .baseUrl("https://$baseUrl/")
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MastodonService::class.java)

    private val streamService = Retrofit.Builder()
            .client(OkHttpProvider.streamClient)
            .baseUrl("https://$baseUrl/")
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
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

    fun getPublicTimelineAsStream(): Observable<ResponseBody> = streamService.getPublicTimelineAsStream()

    fun getLocalTimelineAsStream(): Observable<ResponseBody> = streamService.getLocalTimelineAsStream()

    fun getUserTimelineAsStream(): Observable<ResponseBody> = streamService.getUserTimelineAsStream()

    fun getPublicTimeline(isLocal: Boolean = false, maxId: Long? = null, sinceId: Long? = null): Single<Result<List<Status>>> = service.getPublicTimeline(isLocal, maxId, sinceId)

    fun getTagTimeline(hashTag: String, isLocal: Boolean = false, maxId: Long? = null, sinceId: Long? = null): Single<Result<List<Status>>> = service.getTagTimeline(hashTag, isLocal, maxId, sinceId)

    fun getUserTimeline(maxId: Long? = null, sinceId: Long? = null): Single<Result<List<Status>>> = service.getUserTimeline(maxId, sinceId)

    fun getNotificationTimeline(maxId: Long? = null, sinceId: Long? = null): Single<Result<List<Notification>>> = service.getNotificationTimeline(maxId, sinceId)

    fun getAccountAllToots(accountId: Long, maxId: Long? = null, sinceId: Long? = null): Single<Result<List<Status>>> = service.getAccountAllToots(accountId, maxId, sinceId)

    fun favoriteByStatusId(statusId: Long): Single<Status> = service.favoriteStatusById(statusId)

    fun unFavoriteByStatusId(statusId: Long): Single<Status> = service.unFavoriteStatusById(statusId)

    fun reblogByStatusId(statusId: Long): Single<Status> = service.reblogStatusById(statusId)

    fun unReblogByStatusId(statusId: Long): Single<Status> = service.unReblogStatusById(statusId)

    fun getStatusByStatusId(statusId: Long): Single<Status> = service.getStatusById(statusId)

    fun getAccountRelationships(vararg accountId: Long): Single<List<Relationship>> = service.getAccountRelationships(*accountId)

    fun followAccount(accountId: Long): Single<Relationship> = service.followAccount(accountId)

    fun unFollowAccount(accountId: Long): Single<Relationship> = service.unFollowAccount(accountId)

    fun blockAccount(accountId: Long): Single<Relationship> = service.blockAccount(accountId)

    fun unBlockAccount(accountId: Long): Single<Relationship> = service.unBlockAccount(accountId)

    fun muteAccount(accountId: Long): Single<Relationship> = service.muteAccount(accountId)

    fun unMuteAccount(accountId: Long): Single<Relationship> = service.unMuteAccount(accountId)

    fun postNewToot(
            body: String,
            inReplyToId: Long? = null,
            mediaIds: List<Long>? = null,
            isSensitive: Boolean? = null,
            spoilerText: String? = null,
            visibility: MastodonService.Visibility? = null
    ): Single<Status> = service.postNewToot(body, inReplyToId, mediaIds, isSensitive, spoilerText, visibility?.name)

    fun deleteToot(statusId: Long): Completable = service.deleteToot(statusId)

    fun postNewMedia(body: MultipartBody.Part): Single<Attachment> = service.postNewMedia(body)

    fun getMutedUsers(
            idTo: Long? = null,
            idFrom: Long? = null,
            limit: Long = 80
    ): Single<List<Account>> = service.getMutedUsers(idTo, idFrom, limit)

    fun getBlockedUsers(
            idTo: Long? = null,
            idFrom: Long? = null,
            limit: Long = 80
    ): Single<List<Account>> = service.getBlockedUsers(idTo, idFrom, limit)
}