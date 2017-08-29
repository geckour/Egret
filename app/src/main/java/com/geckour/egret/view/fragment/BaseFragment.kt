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

        (activity as? MainActivity)?.apply {
            supportActionBar?.show()
            binding.appBarMain.contentMain.fab.show()
            Common.setSimplicityPostBarVisibility(binding.appBarMain.contentMain, false)
        }
    }

    override fun onResume() {
        super.onResume()

        Single.just(activity)
                .map { Glide.get(it).clearDiskCache() }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe()

        (activity as? MainActivity)?.apply {
            supportActionBar?.show()
            binding.appBarMain.toolbar.setOnClickListener(null)
            binding.appBarMain.contentMain.fab.show()
            Common.setSimplicityPostBarVisibility(binding.appBarMain.contentMain, false)
        }
    }
}