package com.geckour.egret.view.fragment

import android.os.Bundle
import android.view.View
import com.bumptech.glide.Glide
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.trello.rxlifecycle2.components.support.RxFragment
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

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

        Single.just(activity)
                .map { Glide.get(it).clearDiskCache() }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    if (activity is MainActivity) {
                        (activity as MainActivity).supportActionBar?.show()
                        (activity as MainActivity).binding.appBarMain.contentMain.fab.show()
                        Common.setSimplicityPostBarVisibility((activity as MainActivity).binding.appBarMain.contentMain, false)
                    }
                }, Throwable::printStackTrace)
    }
}