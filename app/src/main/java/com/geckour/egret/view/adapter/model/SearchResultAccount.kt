package com.geckour.egret.view.adapter.model

import java.util.*

data class SearchResultAccount(
        val id: Long,
        val iconUrl: String,
        val nameStrong: String,
        val nameWeak: String,
        var isLocked: Boolean,
        val createdAt: Date
)