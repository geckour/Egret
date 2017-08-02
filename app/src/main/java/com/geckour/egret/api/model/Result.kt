package com.geckour.egret.api.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Result(
        val accounts: List<Account>?,

        val statuses: List<Status>?,

        @SerializedName("hashtags")
        val hashTags: List<String>?
): Serializable