package com.geckour.egret.view.adapter.model

import android.text.Spanned

data class ProfileContent(
        var iconUrl: String,
        var headerUrl: String,
        var screenName: String,
        var username: String,
        var url: Spanned,
        var note: Spanned,
        var followingCount: Long,
        var followerCount: Long,
        var tootCount: Long,
        var createdAt: Long
)