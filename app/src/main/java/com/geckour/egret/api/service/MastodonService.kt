package com.geckour.egret.api.service

import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.InstanceAccess
import com.geckour.egret.api.model.Status
import com.geckour.egret.api.model.UserSpecificApp
import io.reactivex.Observable
import io.reactivex.Single
import okhttp3.ResponseBody
import okio.BufferedSource
import retrofit2.http.*
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
    fun getPublicTimeline(): Observable<ResponseBody>

    @GET("api/v1/accounts/{id}/statuses")
    fun getAccountAllToots(
            @Path("id")
            accountId: Long
    ): Single<List<Status>>

    @FormUrlEncoded
    @POST("api/v1/statuses")
    fun postNewToot(
            @Field("status")
            body: String,

            @Field("in_reply_to_id")
            inReplyToId: Long?,

            @Field("media_ids")
            mediaIds: List<Long>?,

            @Field("sensitive")
            isSensitive: Boolean?,

            @Field("spoiler_text")
            spoilerText: String?,

            @Field("visibility")
            visibility: String?
    ): Single<Status>

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