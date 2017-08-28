package com.geckour.egret.model

import com.geckour.egret.api.model.Attachment
import com.geckour.egret.api.service.MastodonService
import com.github.gfx.android.orma.annotation.Column
import com.github.gfx.android.orma.annotation.PrimaryKey
import com.github.gfx.android.orma.annotation.Setter
import com.github.gfx.android.orma.annotation.Table

@Table
data class Draft(
        @Setter("id") @PrimaryKey(autoincrement = true) val id: Long = -1L,
        @Setter("tokenId") @Column var tokenId: Long = -1L,
        @Setter("body") @Column var body: String = "",
        @Setter("alertBody") @Column var alertBody: String = "",
        @Setter("inReplyToId") @Column var inReplyToId: Long? = null,
        @Setter("attachments") @Column var attachments: Attachments,
        @Setter("sensitive") @Column var sensitive: Boolean = false,
        @Setter("visibility") @Column var visibility: Int = MastodonService.Visibility.public.ordinal
) {
    data class Attachments(
            val value: List<Attachment> = ArrayList()
    )
}