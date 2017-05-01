package com.geckour.egret.util

import android.net.Uri
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.widget.TextView
import android.text.Selection
import android.text.style.URLSpan
import android.text.style.ClickableSpan



class MutableLinkMovementMethod(private val listener: OnUrlClickListener) : LinkMovementMethod() {

    interface OnUrlClickListener {
        fun onUrlClick(view: TextView?, uri: Uri)
    }

    override fun onTouchEvent(widget: TextView?, buffer: Spannable?, event: MotionEvent?): Boolean {
        val action = event?.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            var x: Int = event.x.toInt()
            var y: Int = event.y.toInt()

            widget?.let {
                x -= it.totalPaddingLeft
                y -= it.totalPaddingTop

                x += it.scrollX
                y += it.scrollY
            }

            val layout = widget?.layout
            val link = if (layout == null) null else {
                val line = layout.getLineForVertical(y)
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())
                buffer?.getSpans(offset, offset, ClickableSpan::class.java)
            }

            if (link != null && link.isNotEmpty()) {
                if (action == MotionEvent.ACTION_UP) {
                    if (link[0] is URLSpan) {
                        val uri = Uri.parse((link[0] as URLSpan).url)
                        listener.onUrlClick(widget, uri)
                    } else {
                        link[0].onClick(widget)
                    }
                } else if (action == MotionEvent.ACTION_DOWN && buffer != null) {
                    Selection.setSelection(buffer,
                            buffer.getSpanStart(link[0]),
                            buffer.getSpanEnd(link[0]))
                }

                return true
            } else {
                Selection.removeSelection(buffer)
            }
        }

        return super.onTouchEvent(widget, buffer, event)
    }
}