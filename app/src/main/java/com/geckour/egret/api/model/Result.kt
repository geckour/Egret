package com.geckour.egret.api.model

data class Result(
        val accounts: List<Account>?,
        val statuses: List<Status>?,
        val hashtags: List<String>?
)