package com.geckour.egret.api.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName

data class Attachment(
        val id: Long,

        var type: Type,

        var url: String,

        @SerializedName("remote_url")
        var remoteImgUrl: String?,

        @SerializedName("preview_url")
        var previewImgUrl: String,

        @SerializedName("text_url")
        var urlInText: String?
) {

    enum class Type {
        image,
        video,
        gifv
    }
}