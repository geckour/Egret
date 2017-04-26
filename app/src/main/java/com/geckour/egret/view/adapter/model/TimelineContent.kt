package com.geckour.egret.view.adapter.model

import android.text.Spanned
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Status
import paperparcel.PaperParcel
import paperparcel.PaperParcelable
import java.util.*

@PaperParcel
data class TimelineContent(
        var id: Long,
        var type: TimelineContentType,
        var accountId: Long,
        var iconUrl: String,
        var nameStrong: String,
        var nameWeak: String,
        var time: Date,
        var body: Spanned,
        var favourited: Boolean,
        var reblogged: Boolean,
        var rebloggedBy: String?,
        var rebloggedStatusContent: TimelineContent?
): PaperParcelable {
    companion object {
        @JvmField val CREATOR = PaperParcelTimelineContent.CREATOR

        enum class TimelineContentType(val rawValue: Int) {
            normal(0),
            reblog(1)
        }
    }
}