package com.geckour.egret.api.model

import com.google.gson.annotations.SerializedName

class UserSpecificApp(
        val id: Long,

        @SerializedName("client_id")
        val clientId: String,

        @SerializedName("client_secret")
        val clientSecret: String
)