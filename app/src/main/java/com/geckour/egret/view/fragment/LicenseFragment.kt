package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.databinding.FragmentLicenseBinding
import com.geckour.egret.view.activity.SettingActivity

class LicenseFragment : BaseFragment() {
    
    enum class License {
        Stetho,
        RxJava,
        RxAndroid,
        RxKotlin,
        RxLifecycle,
        Orma,
        Retrofit,
        Glide,
        Timber,
        MaterialDrawer,
        CircularImageView,
        Calligraphy,
        Emoji,
        CommonsIO
    }

    companion object {
        val TAG: String = this::class.java.simpleName

        fun newInstance(): LicenseFragment = LicenseFragment()
    }

    lateinit private var binding: FragmentLicenseBinding

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_license, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        injectLicense()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        (activity as? SettingActivity)?.binding?.appBarMain?.toolbar?.title = getString(R.string.title_fragment_license)
    }

    private fun injectLicense() {
        listOf(
                binding.licenseStetho,
                binding.licenseReactiveXJava,
                binding.licenseReactiveXAndroid,
                binding.licenseReactiveXKotlin,
                binding.licenseReactiveXLifecycle,
                binding.licenseOrma,
                binding.licenseRetrofit,
                binding.licenseGlide,
                binding.licenseTimber,
                binding.licenseMaterialDrawer,
                binding.licenseCircularImageView,
                binding.licenseCalligraphy,
                binding.licenseEmoji,
                binding.licenseCommonsIo
        )
                .forEach {
                    it.apply {
                        name.setOnClickListener {
                            body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        }
                    }
                }
    }
}