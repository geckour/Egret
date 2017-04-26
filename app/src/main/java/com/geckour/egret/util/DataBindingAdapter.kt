package com.geckour.egret.util

import android.databinding.BindingAdapter
import android.util.Patterns
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import java.util.*

class DataBindingAdapter {
    companion object {
        @JvmStatic
        @BindingAdapter("bind:imageUrl")
        fun loadImage(view: ImageView, url: String) {
            if (Patterns.WEB_URL.matcher(url).matches()) Picasso.with(view.context).load(url).transform(RoundedCornerTransformation(8f, 0f)).into(view)
        }

        @JvmStatic
        @BindingAdapter("android:text")
        fun setText(view: TextView, time: Date) {
            view.text = Common.getReadableDateString(time.time)
        }
    }
}