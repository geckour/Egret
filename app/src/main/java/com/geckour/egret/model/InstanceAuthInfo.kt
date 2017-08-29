package com.geckour.egret.model

import com.github.gfx.android.orma.annotation.Column
import com.github.gfx.android.orma.annotation.PrimaryKey
import com.github.gfx.android.orma.annotation.Setter
import com.github.gfx.android.orma.annotation.Table
import java.util.*

@Table
data class InstanceAuthInfo(
        @Setter("id") @PrimaryKey(autoincrement = true) val id: Long = -1L,
        @Setter("instance") @Column val instance: String = "",
        @Setter("auth_id") @Column var authId: Long = -1L,
        @Setter("client_id") @Column val clientId: String = "",
        @Setter("client_secret") @Column val clientSecret: String = "",
        @Setter("created_at") @Column val createdAt: Date = Date(System.currentTimeMillis())
)