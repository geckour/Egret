package com.geckour.egret.view.adapter.model

data class SearchResultContent(
        val account: SearchResultAccount? = null,
        val status: TimelineContent.TimelineStatus? = null,
        val hashTag: String? = null
)