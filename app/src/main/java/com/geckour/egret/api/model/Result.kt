package com.geckour.egret.api.model

import java.io.Serializable

data class Result(
        val accounts: List<Account>?,
        val statuses: List<Status>?,
        val hashtags: List<String>?
): Serializable