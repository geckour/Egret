package com.geckour.egret.view.activity

import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import com.geckour.egret.R
import com.geckour.egret.databinding.ActivityShareBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.fragment.NewTootCreateFragment

class ShareActivity: BaseActivity() {

    lateinit private var binding: ActivityShareBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_share)


        val token = Common.getCurrentAccessToken() ?: return

        intent.apply {
            if (type != "text/plain") return

            supportFragmentManager.beginTransaction()
                    .add(R.id.container, NewTootCreateFragment.newInstance(token.id, body = extras?.getString(Intent.EXTRA_TEXT, "")), NewTootCreateFragment.TAG)
                    .commit()
        }
    }
}