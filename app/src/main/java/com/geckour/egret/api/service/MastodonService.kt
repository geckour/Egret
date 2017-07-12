package com.geckour.egret.api.service

import com.geckour.egret.api.model.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okio.BufferedSource
import retrofit2.adapter.rxjava2.Result
import retrofit2.http.*
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.SocketException

interface MastodonService {

    enum class Visibility(val rawValue: Int) {
        `public`(0),
        unlisted(1),
        `private`(2),
        direct(3)
    }

    @FormUrlEncoded
    @POST("api/v1/apps")
    fun registerApp(
            @Field("client_name")
            clientName: String = "Egret",

            @Field("redirect_uris")
            redirectUrl: String = "urn:ietf:wg:oauth:2.0:oob",

            @Field("scopes")
            authorityScope: String = "read write follow"
    ): Single<UserSpecificApp>

    @POST("oauth/token")
    fun authUser(
            @Query("client_id")
            clientId: String,

            @Query("client_secret")
            clientSecret: String,

            @Query("username")
            username: String,

            @Query("password")
            password: String,

            @Query("grant_type")
            grantType: String = "password",

            @Query("scope")
            authorityScope: String = "read write follow"
    ): Single<InstanceAccess>

    @GET("api/v1/accounts/verify_credentials")
    fun getSelfAccount(): Single<Account>

    @GET("api/v1/accounts/{id}")
    fun getAccount(
            @Path("id")
            accountId: Long
    ): Observable<Account>

    @GET("api/v1/streaming/public")
    @Streaming
    fun getPublicTimelineAsStream(): Observable<ResponseBody>

    @GET("api/v1/streaming/public/local")
    @Streaming
    fun getLocalTimelineAsStream(): Observable<ResponseBody>

    @GET("api/v1/streaming/user")
    @Streaming
    fun getUserTimelineAsStream(): Observable<ResponseBody>

    @GET("api/v1/timelines/public")
    fun getPublicTimeline(
            @Query("local")
            isLocal: Boolean = false,

            @Query("max_id")
            maxId: Long? = null,

            @Query("since_id")
            sinceId: Long? = null
    ): Single<Result<List<Status>>>

    @GET("api/v1/timelines/tag/{hashtag}")
    fun getTagTimeline(
            @Path("hashtag")
            hashTag: String,

            @Query("local")
            isLocal: Boolean = false,

            @Query("max_id")
            maxId: Long? = null,

            @Query("since_id")
            sinceId: Long? = null
    ): Single<Result<List<Status>>>

    @GET("api/v1/timelines/home")
    fun getUserTimeline(
            @Query("max_id")
            maxId: Long? = null,

            @Query("since_id")
            sinceId: Long? = null
    ): Single<Result<List<Status>>>

    @GET("api/v1/notifications")
    fun getNotificationTimeline(
            @Query("max_id")
            maxId: Long? = null,

            @Query("since_id")
            sinceId: Long? = null,

            @Query("limit")
            limit: Long? = 30
    ): Single<Result<List<Notification>>>

    @GET("api/v1/accounts/{id}/statuses")
    fun getAccountAllToots(
            @Path("id")
            accountId: Long,

            @Query("max_id")
            maxId: Long?
    ): Single<Result<List<Status>>>

    @FormUrlEncoded
    @POST("api/v1/statuses")
    fun postNewToot(
            @Field("status")
            body: String,

            @Field("in_reply_to_id")
            inReplyToId: Long?,

            @Field("media_ids[]")
            mediaIds: List<Long>?,

            @Field("sensitive")
            isSensitive: Boolean?,

            @Field("spoiler_text")
            spoilerText: String?,

            @Field("visibility")
            visibility: String?
    ): Single<Status>

    @POST("api/v1/statuses/{id}/favourite")
    fun favoriteStatusById(
            @Path("id")
            statusId: Long
    ): Single<Status>

    @POST("api/v1/statuses/{id}/unfavourite")
    fun unFavoriteStatusById(
            @Path("id")
            statusId: Long
    ): Single<Status>

    @POST("api/v1/statuses/{id}/reblog")
    fun reblogStatusById(
            @Path("id")
            statusId: Long
    ): Single<Status>

    @POST("api/v1/statuses/{id}/unreblog")
    fun unReblogStatusById(
            @Path("id")
            statusId: Long
    ): Single<Status>

    @GET("api/v1/statuses/{id}")
    fun getStatusById(
            @Path("id")
            statusId: Long
    ): Single<Status>

    @GET("api/v1/accounts/relationships")
    fun getAccountRelationships(
            @Query("id")
            vararg accountId: Long
    ): Single<List<Relationship>>

    @POST("api/v1/accounts/{id}/follow")
    fun followAccount(
            @Path("id")
            accountId: Long
    ): Single<Relationship>

    @POST("api/v1/accounts/{id}/unfollow")
    fun unFollowAccount(
            @Path("id")
            accountId: Long
    ): Single<Relationship>

    @POST("api/v1/accounts/{id}/block")
    fun blockAccount(
            @Path("id")
            accountId: Long
    ): Single<Relationship>

    @POST("api/v1/accounts/{id}/unblock")
    fun unBlockAccount(
            @Path("id")
            accountId: Long
    ): Single<Relationship>

    @POST("api/v1/accounts/{id}/mute")
    fun muteAccount(
            @Path("id")
            accountId: Long
    ): Single<Relationship>

    @POST("api/v1/accounts/{id}/unmute")
    fun unMuteAccount(
            @Path("id")
            accountId: Long
    ): Single<Relationship>

    @DELETE("api/v1/statuses/{id}")
    fun deleteToot(
            @Path("id")
            statusId: Long
    ): Completable

    @Multipart
    @POST("api/v1/media")
    fun postNewMedia(
            @Part
            file: MultipartBody.Part
    ): Single<Attachment>

    @GET("api/v1/mutes")
    fun getMutedUsers(
            @Query("max_id")
            idTo: Long?,

            @Query("since_id")
            idFrom: Long?,

            @Query("limit")
            limit: Long
    ): Single<List<Account>>

    @GET("api/v1/blocks")
    fun getBlockedUsers(
            @Query("max_id")
            idTo: Long?,

            @Query("since_id")
            idFrom: Long?,

            @Query("limit")
            limit: Long
    ): Single<List<Account>>

    companion object {
        fun events(source: BufferedSource): Observable<String> {
            return Observable.create { emitter ->
                try {
                    while (!source.exhausted()) {
                        emitter.onNext(source.readUtf8Line())
                    }
                } catch (e: SocketException) {
                    //emitter.onError(e)
                    e.printStackTrace()
                } catch (e: IOException) {
                    //emitter.onError(e)
                    e.printStackTrace()
                }
                emitter.onComplete()
            }
        }
    }
}