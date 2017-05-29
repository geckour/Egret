package com.geckour.egret.view.adapter.model

import android.text.Spanned
import paperparcel.PaperParcel
import paperparcel.PaperParcelable
import java.util.*

@PaperParcel
data class TimelineContent(
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
        var tags: List<String>,
        var favourited: Boolean,
        var reblogged: Boolean,
        var favCount: Long,
        var reblogCount: Long,
        var rebloggedStatusContent: TimelineContent?,
        var app: String?
): PaperParcelable {
    companion object {
        @JvmField val CREATOR = PaperParcelTimelineContent.CREATOR
    }
}