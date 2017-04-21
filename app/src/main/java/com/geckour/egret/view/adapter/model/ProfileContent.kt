package com.geckour.egret.view.adapter.model

data class ProfileContent(
        var iconUrl: String,
        var headerUrl: String,
        var screenName: String,
        var username: String,
        var url: String,
        var note: String,
        var followingCount: Long,
        var followerCount: Long,
        var tootCount: Long,
        var createdAt: Long
)