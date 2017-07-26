package com.geckour.egret.api.model

import com.google.gson.annotations.SerializedName
import java.util.*

data class Notification(
        val id: Long,

        var type: String,

        @SerializedName("created_at")
        var createdAt: Date,

        var account: Account,

        var status: Status?
) {
    enum class NotificationType(val rawValue: Int) {
        mention(0),
        reblog(1),
        favourite(2),
        follow(3)
    }
}