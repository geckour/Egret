package com.geckour.egret.api.model

import com.google.gson.annotations.SerializedName

class Mention(
        var url: String,

        var username: String,

        var acct: String,

        @SerializedName("id")
        val accontId: Long
)