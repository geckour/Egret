package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.graphics.PointF
import android.os.Bundle
import android.support.v4.view.PagerAdapter
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.databinding.FragmentImageSliderBinding
import com.geckour.egret.databinding.PageFullscreenImageBinding
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle2.components.support.RxAppCompatDialogFragment

class ShowImagesDialogFragment: RxAppCompatDialogFragment() {

    lateinit private var binding: FragmentImageSliderBinding
    private var position = 0

    companion object {
        val TAG: String = this::class.java.simpleName
        val ARGS_KEY_IMAGE_PATHS = "images"
        val ARGS_KEY_POSITION = "position"

        fun newInstance(images: List<String>, position: Int): ShowImagesDialogFragment {
            val fragment = ShowImagesDialogFragment()
            val args = Bundle()
            args.putStringArrayList(ARGS_KEY_IMAGE_PATHS, ArrayList(images))
            args.putInt(ARGS_KEY_POSITION, position)
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = arguments.getInt(ARGS_KEY_POSITION, 0)
        setStyle(RxAppCompatDialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_NoActionBar_Fullscreen)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_image_slider, container, false)
        arguments.getStringArrayList(ARGS_KEY_IMAGE_PATHS)?.let {
            val adapter = ViewPagerAdapter(it)
            binding.viewPager.adapter = adapter
            setCurrentItem(position)
        }

        return binding.root
    }

    fun setCurrentItem(position: Int) {
        binding.viewPager.currentItem = position
    }

    inner class ViewPagerAdapter(private val imagePaths: List<String>) : PagerAdapter() {

        val binding: ArrayList<PageFullscreenImageBinding> = ArrayList()
        private val lastPoints = Pair(PointF(-1F, -1F), PointF(-1F, -1F))

        override fun instantiateItem(container: ViewGroup?, position: Int): Any {
            binding.add(DataBindingUtil.inflate(LayoutInflater.from(activity), R.layout.page_fullscreen_image, container, false))
            Picasso.with(activity).load(imagePaths[position]).into(binding[position].image)
            binding[position].cover.setOnTouchListener { view, event -> onTouchImage(event, position) } // 直接ImageViewにonTouchListenerを仕込むと挙動がおかしくなる
            container?.addView(binding[position].root)

            return binding[position].root
        }

        override fun getCount(): Int = imagePaths.size

        override fun isViewFromObject(view: View?, `object`: Any?): Boolean = (`object` is View) && view == `object`

        override fun destroyItem(container: ViewGroup?, position: Int, `object`: Any?) {
            if (`object` is View) container?.removeView(`object`)
        }

        fun onTouchImage(event: MotionEvent, position: Int): Boolean {
            if (event.pointerCount == 2) {
                onScale(binding[position].image, event)
            }

            return true
        }

        fun onScale(view: View, event: MotionEvent) {
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (view.scaleX < 1f || view.scaleY < 1f) {
                        view.translationX = 0f
                        view.translationY = 0f
                        view.scaleX = 1f
                        view.scaleY = 1f
                    }
                    resetPoints(lastPoints)
                }

                else -> {
                    val currentPoints = Pair(PointF(event.getX(0), event.getY(0)), PointF(event.getX(1), event.getY(1)))
                    if (lastPoints.first.x < 0f) lastPoints.first.set(currentPoints.first)
                    if (lastPoints.second.x < 0f) lastPoints.second.set(currentPoints.second)

                    val scale = getScale(lastPoints, currentPoints)
                    val distance = getDistance(lastPoints, currentPoints)
                    view.scaleX = view.scaleX.times(scale)
                    view.scaleY = view.scaleY.times(scale)
                    view.translationX = view.translationX.plus(distance.x)
                    view.translationY = view.translationY.plus(distance.y)

                    lastPoints.first.set(currentPoints.first)
                    lastPoints.second.set(currentPoints.second)
                }
            }
        }

        fun getScale(lastPoints: Pair<PointF, PointF>, currentPoints: Pair<PointF, PointF>): Float {
            val diffs = Pair(getDiffPointOfPoints(lastPoints).length(), getDiffPointOfPoints(currentPoints).length())
            return if (diffs.first == 0f) 1f else diffs.second / diffs.first
        }

        fun getDistance(lastPoints: Pair<PointF, PointF>, currentPoints: Pair<PointF, PointF>): PointF {
            val midPoints = Pair(getMidPointOfPoints(lastPoints), getMidPointOfPoints(currentPoints))
            return getDiffPointOfPoints(midPoints)
        }

        fun getDiffPointOfPoints(points: Pair<PointF, PointF>): PointF = PointF(points.second.x - points.first.x, points.second.y - points.first.y)

        fun getMidPointOfPoints(points: Pair<PointF, PointF>): PointF = PointF((points.first.x + points.second.x) / 2, (points.first.y + points.second.y) / 2)

        fun resetPoints(pair: Pair<PointF, PointF>) {
            pair.first.set(-1F, -1F)
            pair.second.set(-1F, -1F)
        }
    }
}