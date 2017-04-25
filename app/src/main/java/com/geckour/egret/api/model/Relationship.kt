package com.geckour.egret.api.model

import com.google.gson.annotations.SerializedName

class Relationship(
        @SerializedName("id")
        val accountId: Long,

        var following: Boolean,

        @SerializedName("followed_by")
        var followedBy: Boolean,

        var blocking: Boolean,

        var muting: Boolean,

        @SerializedName("requested")
        var requestedAllowToFollow: Boolean
)