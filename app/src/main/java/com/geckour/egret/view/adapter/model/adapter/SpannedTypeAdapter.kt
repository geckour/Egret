package com.geckour.egret.view.adapter.model.adapter

import android.os.Build
import android.os.Parcel
import android.text.Html
import android.text.Spanned
import android.util.Log
import com.geckour.egret.util.Common
import com.google.gson.*
import paperparcel.TypeAdapter
import java.lang.reflect.Type

class SpannedTypeAdapter: TypeAdapter<Spanned>, JsonSerializer<Spanned>, JsonDeserializer<Spanned>, InstanceCreator<Spanned> {

    companion object {
        val KEY = "Spanned"
    }

    override fun writeToParcel(value: Spanned, outParcel: Parcel, flags: Int) {
        outParcel.writeString(value.toString())
    }

    override fun readFromParcel(inParcel: Parcel): Spanned {
        return Common.getSpannedWithoutExtraMarginFromHtml(inParcel.readString())
    }

    override fun createInstance(type: Type?): Spanned {
        return Common.getSpannedWithoutExtraMarginFromHtml("")
    }

    override fun serialize(src: Spanned?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonObject().apply {
            if (src == null) {
                addProperty(KEY, "")
            } else {
                addProperty(KEY,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Html.toHtml(src, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)?.toString()
                        else Html.toHtml(src).toString())
            }
        }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Spanned {
        return Common.getSpannedWithoutExtraMarginFromHtml(if (json == null) "" else json.asJsonObject.get(KEY)?.asString ?: "")
    }
}