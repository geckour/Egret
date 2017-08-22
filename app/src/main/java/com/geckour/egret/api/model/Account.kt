package com.geckour.egret.api.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.net.URL
import java.util.*

data class Account(
        val id: Long,

        var username: String,

        var acct: String,

        @SerializedName("display_name")
        var displayName: String,

        @SerializedName("locked")
        var isLocked: Boolean,

        @SerializedName("created_at")
        val createdAt: Date,

        @SerializedName("followers_count")
        var followersCount: Long,

        @SerializedName("following_count")
        var followingCount: Long,

        @SerializedName("statuses_count")
        var statusesCount: Long,

        var note: String,

        val url: URL,

        @SerializedName("avatar")
        var avatarUrl: String,

        @SerializedName("avatar_static")
        var avatarUrlStatic: String,

        @SerializedName("header")
        var headerUrl: String,

        @SerializedName("header_static")
        var headerUrlStatic: String
): Serializable