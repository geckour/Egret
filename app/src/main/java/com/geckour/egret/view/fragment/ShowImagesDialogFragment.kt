package com.geckour.egret.view.fragment

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.PagerAdapter
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.geckour.egret.R
import com.geckour.egret.databinding.FragmentImageSliderBinding
import com.geckour.egret.databinding.PageFullscreenImageBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle2.components.support.RxAppCompatDialogFragment
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileOutputStream

class ShowImagesDialogFragment: RxAppCompatDialogFragment() {

    lateinit private var binding: FragmentImageSliderBinding
    lateinit private var adapter: ViewPagerAdapter
    private var position = 0

    companion object {
        val TAG: String = this::class.java.simpleName
        private val ARGS_KEY_IMAGE_PATHS = "images"
        private val ARGS_KEY_POSITION = "position"
        private val REQUEST_CODE_GRANT_WRITE_STORAGE = 1
        val SAVE_IMG_DIR = "/Egret/"

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

        binding.opt.setOnClickListener { showPopup(it) }
        arguments.getStringArrayList(ARGS_KEY_IMAGE_PATHS)?.let {
            adapter = ViewPagerAdapter(it)
            binding.viewPager.adapter = adapter
            setCurrentItem(position)
        }

        return binding.root
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_GRANT_WRITE_STORAGE -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.filter { it != PackageManager.PERMISSION_GRANTED }.isEmpty()) {
                    saveImage()
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_write_storage_grant_save, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setCurrentItem(position: Int) {
        binding.viewPager.currentItem = position
    }

    fun showPopup(view: View) {
        val popup = PopupMenu(view.context, view).apply {
            setOnMenuItemClickListener { item ->
                when (item?.itemId) {
                    R.id.action_save_image -> {
                        saveImage()
                        true
                    }

                    else -> false
                }
            }
        }
        popup.inflate(R.menu.image)
        popup.show()
    }

    fun saveImage(position: Int = binding.viewPager.currentItem) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_GRANT_WRITE_STORAGE)
        } else {
            getImagePath(position)?.let { path ->
                Single.just(path)
                        .map { Picasso.with(activity).load(path).get() }
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({
                            File("${Environment.getExternalStorageDirectory().absolutePath}$SAVE_IMG_DIR").apply {
                                if (!this.exists()) this.mkdir()

                                val dir = this.absolutePath
                                val fileName = try {
                                    getFileName(dir, path)
                                } catch (e: IndexOutOfBoundsException) {
                                    e.printStackTrace()
                                    Snackbar.make(binding.root, R.string.error_saving_image, Snackbar.LENGTH_SHORT).show()
                                    return@subscribe
                                }
                                val filePath = "$dir/$fileName"
                                FileOutputStream(filePath).apply {
                                    it.compress(if (getExtensionName(path) == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 100, this)
                                    this.flush()
                                    this.close()
                                }

                                ContentValues().apply {
                                    this.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    this.put(MediaStore.Images.Media.TITLE, fileName)
                                    this.put(MediaStore.Images.Media.DATA, filePath)

                                    activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, this)
                                }

                                Snackbar.make(binding.root, R.string.message_complete_saving_image, Snackbar.LENGTH_SHORT).show()
                            }
                        }, Throwable::printStackTrace)
            }
        }
    }

    fun getImagePath(position: Int): String? = arguments.getStringArrayList(ARGS_KEY_IMAGE_PATHS)?.let { it[position] }

    fun getFileName(dir: String, imagePath: String): String {
        var i = -1
        while (true) {
            if (i > 999) throw IndexOutOfBoundsException("Numbered same named files exist over the bound.")

            val fileName = generateFileName(imagePath, i)
            File("$dir/$fileName").apply {
                if (!this.exists()) return fileName
            }
            i++
        }
    }

    fun generateFileName(path: String, i: Int = -1): String =
            "${path.split("/").last().split(".").dropLast(1).joinToString(separator = ".")}${if (i < 0) "" else String.format(".%03d", i)}.${getExtensionName(path)}"

    fun getExtensionName(path: String): String = path.split(".").last()

    inner class ViewPagerAdapter(private val imagePaths: List<String>) : PagerAdapter() {

        val binding: ArrayList<PageFullscreenImageBinding> = ArrayList()
        private val lastPoint = PointF(-1F, -1F)
        private val lastPoints = Pair(PointF(-1F, -1F), PointF(-1F, -1F))
        private var dismiss: Boolean = false

        override fun instantiateItem(container: ViewGroup?, position: Int): Any {
            if (binding.isEmpty()) {
                imagePaths.forEach { binding.add(DataBindingUtil.inflate(LayoutInflater.from(activity), R.layout.page_fullscreen_image, container, false)) }
            }
            Picasso.with(activity).load(imagePaths[position]).into(binding[position].image)
            binding[position].cover.setOnTouchListener { _, event -> onTouchImage(event, position) } // 直接ImageViewにonTouchListenerを仕込むと挙動がおかしくなるので別Viewに仕込んでいる
            container?.addView(binding[position].root)

            return binding[position].root
        }

        override fun getCount(): Int = imagePaths.size

        override fun isViewFromObject(view: View?, `object`: Any?): Boolean = (`object` is View) && view == `object`

        override fun destroyItem(container: ViewGroup?, position: Int, `object`: Any?) {
            if (`object` is View) container?.removeView(`object`)
        }

        fun onTouchImage(event: MotionEvent, position: Int): Boolean =
                when (event.pointerCount) {
                    1 -> onDrag(binding[position].image, event)

                    2 -> {
                        onScale(binding[position].image, event)
                    }

                    else -> true
                }

        fun onDrag(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    lastPoint.set(event.x, event.y)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    this@ShowImagesDialogFragment.binding.viewPager.requestDisallowInterceptTouchEvent(true)
                    if (view.scaleX > 1f || view.scaleY > 1f) {
                        view.translationX = view.translationX + (event.x - lastPoint.x)
                        view.translationY = view.translationY + (event.y - lastPoint.y)

                        return true.apply { lastPoint.set(event.x, event.y) }
                    } else if (Math.abs(event.x - lastPoint.x) < Common.dp(activity, 20f)) {
                        view.translationY = view.translationY + (event.y - lastPoint.y)
                        dismiss = true

                        return true.apply { lastPoint.set(event.x, event.y) }
                    }

                    this@ShowImagesDialogFragment.binding.viewPager.requestDisallowInterceptTouchEvent(false)
                    lastPoint.set(event.x, event.y)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    this@ShowImagesDialogFragment.binding.viewPager.requestDisallowInterceptTouchEvent(false)
                    if (view.scaleX <= 1f || view.scaleY <= 1f) {
                        if (dismiss && Math.abs(view.translationY) > Common.dp(activity, 90f)) {
                            dismiss = false
                            (activity as MainActivity).supportFragmentManager.popBackStack()
                            return true
                        }
                    }

                    dismiss = false
                }
            }

            return false
        }

        fun onScale(view: View, event: MotionEvent): Boolean {
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
                    if (event.pointerCount < 2) return false

                    val currentPoints = Pair(PointF(event.getX(0), event.getY(0)), PointF(event.getX(1), event.getY(1)))
                    if (lastPoints.first.x < 0f) lastPoints.first.set(currentPoints.first)
                    if (lastPoints.second.x < 0f) lastPoints.second.set(currentPoints.second)

                    val scale = getScale(lastPoints, currentPoints)
                    val distance = getDistance(lastPoints, currentPoints)
                    view.scaleX = view.scaleX.times(scale)
                    view.scaleY = view.scaleY.times(scale)
                    view.translationX.apply { plus(distance.x) }
                    view.translationY.apply { plus(distance.y) }

                    lastPoints.first.set(currentPoints.first)
                    lastPoints.second.set(currentPoints.second)
                }
            }

            return true
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