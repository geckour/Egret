package com.geckour.egret.view.activity

import android.content.Context
import android.support.v7.preference.PreferenceManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity

open class BaseActivity: RxAppCompatActivity() {
    fun isModeDark(): Boolean = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("switch_to_dark_theme", false)
}