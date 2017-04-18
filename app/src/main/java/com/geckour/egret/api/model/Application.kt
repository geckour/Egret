package com.geckour.egret.api.model

import com.google.gson.annotations.SerializedName

class Application(
        var name: String,

        @SerializedName("website")
        var webUrl: String
)