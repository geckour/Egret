package com.geckour.egret.view.fragment

import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import com.geckour.egret.R
import com.geckour.egret.view.activity.MainActivity
import com.trello.rxlifecycle2.components.support.RxFragment

open class BaseFragment: RxFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is MainActivity) (activity as MainActivity).supportActionBar?.show()
        ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.show()
    }

    override fun onResume() {
        super.onResume()

        if (activity is MainActivity) (activity as MainActivity).supportActionBar?.show()
        ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.show()
    }
}