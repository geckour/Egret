package com.geckour.egret.api.model

import com.google.gson.annotations.SerializedName
import java.util.*

class Status(
        val id: Long,

        var uri: String,

        var url: String,

        var account: Account,

        @SerializedName("in_reply_to_id")
        var inReplyToId: Long?,

        @SerializedName("in_reply_to_account_id")
        var inReplyToAccountId: Long?,

        var reblog: Status,

        var content: String,

        @SerializedName("created_at")
        val createdAt: Date,

        @SerializedName("reblogs_count")
        var reblogsCount: Long,

        @SerializedName("favourites_count")
        var favCount: Long,

        var reblogged: Boolean,

        var favourited: Boolean,

        var sensitive: Boolean,

        @SerializedName("spoiler_text")
        var spoilerText: String,

        var visibility: String,

        @SerializedName("media_attachments")
        var media: List<Attachment>,

        var mentions: List<Mention>,

        var tags: List<Tag>,

        var application: Application
)