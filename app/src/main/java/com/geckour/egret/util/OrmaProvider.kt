package com.geckour.egret.util

import android.content.Context
import com.geckour.egret.model.OrmaDatabase

object OrmaProvider {
    lateinit var db: OrmaDatabase

    fun init(context: Context) {
        this.db = OrmaDatabase.builder(context).build()
    }
}