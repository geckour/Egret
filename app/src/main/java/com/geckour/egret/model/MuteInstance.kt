package com.geckour.egret.model

import com.github.gfx.android.orma.annotation.Column
import com.github.gfx.android.orma.annotation.PrimaryKey
import com.github.gfx.android.orma.annotation.Setter
import com.github.gfx.android.orma.annotation.Table

@Table
class MuteInstance(
        @Setter("id") @PrimaryKey(autoincrement = true) val id: Long = -1L,
        @Setter("instance") @Column var instance: String = ""
)