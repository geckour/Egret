package com.geckour.egret.view.activity

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity

open class BaseActivity: RxAppCompatActivity() {
    fun showSoftKeyBoardOnFocusEditText(et: EditText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) et.showSoftInputOnFocus = true
        else if (et.onFocusChangeListener == null) {
            et.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                else (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
    }
}