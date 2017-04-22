package com.geckour.egret.model

import com.github.gfx.android.orma.annotation.*
import java.util.*

@Table
class AccessToken(
        @Setter("id") @PrimaryKey(autoincrement = true) val id: Long = -1L,
        @Setter("access_token") @Column val token: String = "",
        @Setter("instance_id") @Column val instanceId: Long = -1L,
        @Setter("user_id") @Column(indexed = true) val userId: Long = -1L,
        @Setter("is_current") @Column(indexed = true) var isCurrent: Boolean = false
)