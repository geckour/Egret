package com.geckour.egret.view.adapter.model

import android.text.Spanned
import paperparcel.PaperParcel
import paperparcel.PaperParcelable

@PaperParcel

data class TimelineContent(
        var id: Long,
        var accountId: Long,
        var iconUrl: String,
        var nameStrong: String,
        var nameWeak: String,
        var time: Long,
        var body: Spanned,
        var favourited: Boolean,
        var reblogged: Boolean
): PaperParcelable {
    companion object {
        @JvmField val CREATOR = PaperParcelTimelineContent.CREATOR
    }
}