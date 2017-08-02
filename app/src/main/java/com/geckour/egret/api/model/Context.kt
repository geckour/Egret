package com.geckour.egret.api.model

data class Context(
        var ancestors: List<Status>,
        var descendants: List<Status>
)