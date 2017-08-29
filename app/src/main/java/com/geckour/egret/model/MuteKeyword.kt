package com.geckour.egret.model

import com.github.gfx.android.orma.annotation.Column
import com.github.gfx.android.orma.annotation.PrimaryKey
import com.github.gfx.android.orma.annotation.Setter
import com.github.gfx.android.orma.annotation.Table

@Table
data class MuteKeyword(
        @Setter("id") @PrimaryKey(autoincrement = true) val id: Long = -1L,
        @Setter("is_regex") @Column var isRegex: Boolean = false,
        @Setter("hashTag") @Column var keyword: String = ""
)