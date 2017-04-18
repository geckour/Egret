package com.geckour.egret.api.model

import com.google.gson.annotations.SerializedName

class Attachment(
        val id: Long,

        var type: String,

        var url: String,

        @SerializedName("remote_url")
        var remoteImgUrl: String,

        @SerializedName("preview_url")
        var previewImgUrl: String,

        @SerializedName("text_url")
        var urlInText: String
)