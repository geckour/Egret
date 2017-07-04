package com.geckour.egret.view.adapter.model.adapter

import android.os.Parcel
import android.text.Spanned
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
        return JsonObject().apply { addProperty(KEY, src?.toString()) }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Spanned {
        return Common.getSpannedWithoutExtraMarginFromHtml(if (json == null) "" else json.asJsonObject.get(KEY)?.asString ?: "")
    }
}