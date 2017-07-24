package com.geckour.egret.view.fragment

import android.os.Bundle
import android.view.View
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.trello.rxlifecycle2.components.support.RxFragment

open class BaseFragment: RxFragment() {

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (activity is MainActivity) {
            (activity as MainActivity).supportActionBar?.show()
            (activity as MainActivity).binding.appBarMain.contentMain.fab.show()
            Common.setSimplicityPostBarVisibility((activity as MainActivity).binding.appBarMain.contentMain, false)
        }
    }

    override fun onResume() {
        super.onResume()

        if (activity is MainActivity) {
            (activity as MainActivity).supportActionBar?.show()
            (activity as MainActivity).binding.appBarMain.contentMain.fab.show()
            Common.setSimplicityPostBarVisibility((activity as MainActivity).binding.appBarMain.contentMain, false)
        }
    }
}