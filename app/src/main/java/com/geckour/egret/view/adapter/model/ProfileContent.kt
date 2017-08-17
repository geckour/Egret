package com.geckour.egret.view.adapter.model

import android.text.Spanned

data class ProfileContent(
        val id: Long,
        var iconUrl: String,
        var headerUrl: String,
        var screenName: String,
        var username: String,
        var url: Spanned,
        var note: Spanned,
        var followingCount: Long,
        var followerCount: Long,
        var tootCount: Long,
        val createdAt: Long
)