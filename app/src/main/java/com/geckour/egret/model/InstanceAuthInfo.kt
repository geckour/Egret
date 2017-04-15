package com.geckour.egret.model

import com.github.gfx.android.orma.annotation.Column
import com.github.gfx.android.orma.annotation.PrimaryKey
import com.github.gfx.android.orma.annotation.Setter
import com.github.gfx.android.orma.annotation.Table

@Table
class InstanceAuthInfo(
        @Setter("id") @PrimaryKey(autoincrement = true) val id: Long = -1L,
        @Setter("instance") @Column val instance: String = "",
        @Setter("user_id") @Column var userId: String = "",
        @Setter("client_id") @Column val clientId: String = "",
        @Setter("client_secret") @Column val clientSecret: String = ""
)