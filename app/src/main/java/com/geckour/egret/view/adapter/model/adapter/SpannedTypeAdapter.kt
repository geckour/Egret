package com.geckour.egret.view.adapter.model.adapter

import android.os.Build
import android.text.Html
import android.text.Spanned
import com.geckour.egret.util.Common
import com.google.gson.*
import java.lang.reflect.Type

class SpannedTypeAdapter: JsonSerializer<Spanned>, JsonDeserializer<Spanned> {

    companion object {
        const val KEY = "Spanned"
    }

    override fun serialize(src: Spanned?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement =
            JsonObject().apply {
                addProperty(KEY,
                        src?.let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Html.toHtml(src, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)?.toString()
                            else Html.toHtml(src).toString()
                        } ?: "")
            }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Spanned =
            Common.getSpannedWithoutExtraMarginFromHtml(json?.asJsonObject?.get(KEY)?.asString ?: "")
}