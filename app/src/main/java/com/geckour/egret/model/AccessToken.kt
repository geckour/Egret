package com.geckour.egret.model

import com.github.gfx.android.orma.annotation.Column
import com.github.gfx.android.orma.annotation.PrimaryKey
import com.github.gfx.android.orma.annotation.Setter
import com.github.gfx.android.orma.annotation.Table

@Table
class AccessToken(
        @Setter("id") @PrimaryKey(autoincrement = true) val id: Long = -1L,
        @Setter("access_token") @Column val token: String = "",
        @Setter("instance_id") @Column val instanceId: Long = -1L,
        @Setter("is_current") @Column var isCurrent: Boolean = false
)