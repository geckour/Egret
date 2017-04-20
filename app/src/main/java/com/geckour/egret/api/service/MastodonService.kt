package com.geckour.egret.api.service

import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.InstanceAccess
import com.geckour.egret.api.model.UserSpecificApp
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import okhttp3.ResponseBody
import okio.BufferedSource
import retrofit2.http.*
import java.io.IOException
import java.net.SocketException

interface MastodonService {
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
            grantType: String = "password"
    ): Single<InstanceAccess>

    @GET("api/v1/accounts/verify_credentials")
    fun getSelfInfo(): Single<Account>

    @GET("api/v1/streaming/public")
    @Streaming
    fun getPublicTimeline(): Observable<ResponseBody>

    companion object {
        fun events(source: BufferedSource): Observable<String> {
            return Observable.create { emitter ->
                try {
                    while (!source.exhausted()) {
                        emitter.onNext(source.readUtf8Line())
                    }
                } catch (e: IOException) {
                    emitter.onError(e)
                } catch (e: SocketException) {
                    emitter.onError(e)
                }
                emitter.onComplete()
            }
        }
    }
}