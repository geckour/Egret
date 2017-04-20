package com.geckour.egret.view.fragment

import android.os.Bundle
import com.geckour.egret.view.activity.MainActivity
import com.trello.rxlifecycle2.components.support.RxFragment

open class BaseFragment: RxFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is MainActivity) (activity as MainActivity).supportActionBar?.show()
    }
}