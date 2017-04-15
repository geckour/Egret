package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.trello.rxlifecycle2.components.support.RxFragment

class AccessInstanceFragment: RxFragment() {

    companion object {
        val TAG = "accessInstanceFragment"

        fun newInstance(): AccessInstanceFragment {
            val fragment = AccessInstanceFragment()
            return fragment
        }
    }

    private lateinit var binding: FragmentLoginInstanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_login_instance, container, false)
        return binding.root
    }
}