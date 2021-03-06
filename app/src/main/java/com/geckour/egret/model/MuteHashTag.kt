package com.geckour.egret.model

import com.github.gfx.android.orma.annotation.Column
import com.github.gfx.android.orma.annotation.PrimaryKey
import com.github.gfx.android.orma.annotation.Setter
import com.github.gfx.android.orma.annotation.Table

@Table
data class MuteHashTag(
    @Setter("id") @PrimaryKey(autoincrement = true) val id: Long = -1L,
    @Setter("hashTag") @Column var hashTag: String = ""
)