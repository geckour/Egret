package com.geckour.egret.util

import android.databinding.BindingAdapter
import android.util.Patterns
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import jp.wasabeef.glide.transformations.RoundedCornersTransformation
import java.util.*

class DataBindingAdapter {
    companion object {
        @JvmStatic
        @BindingAdapter("bind:imageUrl")
        fun loadImage(view: ImageView, url: String?) {
            if (url != null && Patterns.WEB_URL.matcher(url).matches()) {
                Glide.with(view.context).load(url).apply(RequestOptions.bitmapTransform(RoundedCornersTransformation(view.context, 8, 0))).into(view)
            }
        }

        @JvmStatic
        @BindingAdapter("android:text")
        fun setText(view: TextView, time: Date) {
            view.text = Common.getReadableDateString(time.time)
        }

        @JvmStatic
        @BindingAdapter("android:text")
        fun setText(view: EditText, value: String) {
            view.setText(value)
            view.setSelection(value.length)
        }
    }
}