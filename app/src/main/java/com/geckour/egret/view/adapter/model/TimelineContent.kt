package com.geckour.egret.view.adapter.model

import android.text.Spanned
import com.geckour.egret.api.model.Notification
import java.io.Serializable
import java.util.*

data class TimelineContent(
        val status: TimelineStatus? = null,
        val notification: TimelineNotification? = null
) {
    data class TimelineStatus(
            val id: Long,
            val tootUrl: String,
            val accountId: Long,
            var iconUrl: String,
            var accountLocked: Boolean,
            var nameStrong: String,
            var nameWeak: String,
            val time: Date,
            var body: Spanned,
            var mediaPreviewUrls: List<String>,
            var mediaUrls: List<String>,
            var isSensitive: Boolean?,
            var spoilerText: Spanned,
            var tags: List<String>,
            var favourited: Boolean,
            var reblogged: Boolean,
            var favCount: Long,
            var reblogCount: Long,
            var rebloggedStatusContent: TimelineStatus?,
            var app: String?,
            var treeStatus: TreeStatus
    ): Serializable {
        enum class TreeStatus {
            None,
            Top,
            Filling,
            Bottom
        }
    }

    data class TimelineNotification(
            val id: Long,
            val type: String,
            val accountId: Long,
            var accountLocked: Boolean,
            var iconUrl: String,
            var nameStrong: String,
            var nameWeak: String,
            val time: Date,
            val status: TimelineStatus?
    )
}