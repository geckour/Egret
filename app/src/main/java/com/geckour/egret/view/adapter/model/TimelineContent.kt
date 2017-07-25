package com.geckour.egret.view.adapter.model

import android.text.Spanned
import com.geckour.egret.api.model.Notification
import java.util.*

data class TimelineContent(
        val status: TimelineStatus? = null,
        val notification: TimelineNotification? = null
) {
    data class TimelineStatus(
            var id: Long,
            var tootUrl: String,
            var accountId: Long,
            var iconUrl: String,
            var nameStrong: String,
            var nameWeak: String,
            var time: Date,
            var body: Spanned,
            var mediaPreviewUrls: List<String>,
            var mediaUrls: List<String>,
            var isSensitive: Boolean,
            var spoilerText: Spanned,
            var tags: List<String>,
            var favourited: Boolean,
            var reblogged: Boolean,
            var favCount: Long,
            var reblogCount: Long,
            var rebloggedStatusContent: TimelineStatus?,
            var app: String?
    )

    data class TimelineNotification(
            val id: Long,
            var type: String,
            var accountId: Long,
            var iconUrl: String,
            var nameStrong: String,
            var nameWeak: String,
            var time: Date,
            var status: TimelineStatus?
    )
}