package com.geckour.egret.view.adapter.model

import android.text.Spanned
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Status
import paperparcel.PaperParcel
import paperparcel.PaperParcelable

@PaperParcel

data class TimelineContent(
        var id: Long,
        var type: TimelineContentType,
        var accountId: Long,
        var iconUrl: String,
        var nameStrong: String,
        var nameWeak: String,
        var time: Long,
        var body: Spanned,
        var favourited: Boolean,
        var reblogged: Boolean,
        var rebloggedBy: Account?,
        var rebloggedStatus: Status?
): PaperParcelable {
    companion object {
        @JvmField val CREATOR = PaperParcelTimelineContent.CREATOR

        enum class TimelineContentType(val rawValue: Int) {
            normal(0),
            reblog(1)
        }
    }
}