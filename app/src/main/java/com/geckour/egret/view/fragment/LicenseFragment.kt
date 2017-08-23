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
        PaperParcel,
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
        mapOf(
                Pair(License.Stetho, binding.licenseStetho),
                Pair(License.RxJava, binding.licenseReactiveXJava),
                Pair(License.RxAndroid, binding.licenseReactiveXAndroid),
                Pair(License.RxKotlin, binding.licenseReactiveXKotlin),
                Pair(License.RxLifecycle, binding.licenseReactiveXLifecycle),
                Pair(License.Orma, binding.licenseOrma),
                Pair(License.Retrofit, binding.licenseRetrofit),
                Pair(License.Glide, binding.licenseGlide),
                Pair(License.PaperParcel, binding.licensePaperParcel),
                Pair(License.Timber, binding.licenseTimber),
                Pair(License.MaterialDrawer, binding.licenseMaterialDrawer),
                Pair(License.CircularImageView, binding.licenseCircularImageView),
                Pair(License.Calligraphy, binding.licenseCalligraphy),
                Pair(License.Emoji, binding.licenseEmoji),
                Pair(License.CommonsIO, binding.licenseCommonsIo)
        )
                .apply {
                    forEach {
                        it.value.apply {
                            name.apply {
                                text = getLicenseName(it.key)
                                setOnClickListener { body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
                            }
                            body.text = getLicenseBody(it.key)
                        }
                    }
                }
    }
    
    private fun getLicenseName(license: License): String = when (license) {
        License.Stetho -> getString(R.string.license_name_stetho)
        License.RxJava -> getString(R.string.license_name_reactive_x_java)
        License.RxAndroid -> getString(R.string.license_name_reactive_x_android)
        License.RxKotlin -> getString(R.string.license_name_reactive_x_kotlin)
        License.RxLifecycle -> getString(R.string.license_name_reactive_x_lifecycle)
        License.Orma -> getString(R.string.license_name_orma)
        License.Retrofit -> getString(R.string.license_name_retrofit)
        License.Glide -> getString(R.string.license_name_glide)
        License.Timber -> getString(R.string.license_name_timber)
        License.PaperParcel -> getString(R.string.license_name_paper_parcel)
        License.MaterialDrawer -> getString(R.string.license_name_material_drawer)
        License.CircularImageView -> getString(R.string.license_name_circular_image_view)
        License.Calligraphy -> getString(R.string.license_name_calligraphy)
        License.Emoji -> getString(R.string.license_name_emoji)
        License.CommonsIO -> getString(R.string.license_name_commons_io)
    }

    private fun getLicenseBody(license: License): String = when (license) {
        License.Stetho -> getString(R.string.license_body_stetho)
        License.RxJava -> getString(R.string.license_body_reactive_x_java)
        License.RxAndroid -> getString(R.string.license_body_reactive_x_android)
        License.RxKotlin -> getString(R.string.license_body_reactive_x_kotlin)
        License.RxLifecycle -> getString(R.string.license_body_reactive_x_lifecycle)
        License.Orma -> getString(R.string.license_body_orma)
        License.Retrofit -> getString(R.string.license_body_retrofit)
        License.Glide -> getString(R.string.license_body_glide)
        License.Timber -> getString(R.string.license_body_timber)
        License.PaperParcel -> getString(R.string.license_body_paper_parcel)
        License.MaterialDrawer -> getString(R.string.license_body_material_drawer)
        License.CircularImageView -> getString(R.string.license_body_circular_image_view)
        License.Calligraphy -> getString(R.string.license_body_calligraphy)
        License.Emoji -> getString(R.string.license_body_emoji)
        License.CommonsIO -> getString(R.string.license_body_commons_io)
    }
}