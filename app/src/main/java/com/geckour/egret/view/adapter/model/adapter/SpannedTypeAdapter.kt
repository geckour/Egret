package com.geckour.egret.view.adapter.model.adapter

import android.os.Build
import android.os.Parcel
import android.text.Html
import android.text.Spanned
import com.geckour.egret.util.Common
import paperparcel.TypeAdapter

class SpannedTypeAdapter: TypeAdapter<Spanned> {
    override fun writeToParcel(value: Spanned, outParcel: Parcel, flags: Int) {
        outParcel.writeString(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    Html.toHtml(value, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)
                else Html.toHtml(value))
    }

    override fun readFromParcel(inParcel: Parcel): Spanned {
        return Common.getBodyStringWithoutExtraMarginFromHtml(inParcel.readString())
    }
}