package com.geckour.egret.util;

import com.geckour.egret.App;
import com.geckour.egret.model.Draft;
import com.github.gfx.android.orma.annotation.StaticTypeAdapter;

@StaticTypeAdapter(
        targetType = Draft.Attachments.class,
        serializedType = String.class
)
public class OrmaAttachmentsAdapter {
    public static String serialize(Draft.Attachments attachments) {
        return App.Companion.getGson().toJson(attachments);
    }

    public static Draft.Attachments deserialize(String string) {
        return App.Companion.getGson().fromJson(string, Draft.Attachments.class);
    }
}
