package com.geckour.egret.view.activity

import android.content.Context
import android.support.v7.preference.PreferenceManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity

open class BaseActivity: RxAppCompatActivity() {
    fun showSoftKeyBoardOnFocusEditText(et: EditText) {
        et.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            else (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromInputMethod(view.applicationWindowToken, 0)
        }
        et.requestFocusFromTouch()
        et.requestFocus()
    }

    fun isModeDark(): Boolean = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("switch_to_dark_theme", false)
}