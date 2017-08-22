package com.geckour.egret.view.fragment

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.text.method.TextKeyListener
import android.util.Base64
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.bumptech.glide.Glide
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Account
import com.geckour.egret.api.model.Relationship
import com.geckour.egret.api.model.Status
import com.geckour.egret.databinding.FragmentAccountProfileBinding
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.TimelineAdapter
import com.geckour.egret.view.adapter.model.ProfileContent
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.apache.commons.io.IOUtils
import retrofit2.adapter.rxjava2.Result
import java.io.File
import java.io.InputStream

class AccountProfileFragment: BaseFragment() {

    companion object {
        val TAG: String = this::class.java.simpleName
        private val ARGS_KEY_ACCOUNT = "profile"
        private val REQUEST_CODE_AVATAR_PICK_MEDIA = 101
        private val REQUEST_CODE_AVATAR_CAPTURE_IMAGE = 102
        private val REQUEST_CODE_HEADER_PICK_MEDIA = 201
        private val REQUEST_CODE_HEADER_CAPTURE_IMAGE = 202
        private val REQUEST_CODE_GRANT_READ_STORAGE_AVATAR = 1
        private val REQUEST_CODE_GRANT_READ_STORAGE_HEADER = 2
        private val REQUEST_CODE_GRANT_WRITE_STORAGE_AVATAR = 3
        private val REQUEST_CODE_GRANT_WRITE_STORAGE_HEADER = 4

        fun newInstance(account: Account): AccountProfileFragment = AccountProfileFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_ACCOUNT, account)
            }
        }
    }

    enum class UploadType {
        Avatar,
        Header
    }

    lateinit private var profile: ProfileContent
    private val editedProfile: ProfileContent by lazy { profile.copy() }
    lateinit private var relationship: Relationship
    lateinit private var binding: FragmentAccountProfileBinding
    private var onTop = true
    private var inTouch = false
    private val adapter: TimelineAdapter by lazy { TimelineAdapter((activity as MainActivity).timelineListener) }

    private var capturedImageUri: Uri? = null

    private var maxId: Long = -1
    private var sinceId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as MainActivity).supportActionBar?.hide()
        profile = Common.getProfileContent(arguments[ARGS_KEY_ACCOUNT] as Account)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_account_profile, container, false)

        val content = profile

        binding.content = content
        binding.timeString = Common.getReadableDateString(content.createdAt, true)
        Glide.with(binding.header.context).load(content.headerUrl).into(binding.header)

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViewsVisibility()

        binding.icon.setOnClickListener { showImageViewer(listOf(profile.iconUrl), 0) }
        binding.header.setOnClickListener { showImageViewer(listOf(profile.headerUrl), 0) }

        if (profile.id == Common.getCurrentAccessToken()?.accountId) {
            binding.buttonFavList.apply {
                setOnClickListener { showFavList() }
                visibility = View.VISIBLE
            }

            binding.buttonEdit.apply {
                setOnClickListener {
                    val enter = text == getString(R.string.button_edit)
                    toggleEditMode(enter, this)
                }
                visibility = View.VISIBLE
            }

            listOf(
                    binding.screenName,
                    binding.note
            )
                    .forEach {
                        it.tag = it.keyListener
                        it.setOnKeyListener(null)
                    }
        }

        val movementMethod = Common.getMovementMethodFromPreference(binding.root.context)
        binding.url.movementMethod = movementMethod
        binding.note.movementMethod = movementMethod

        val domain = Common.resetAuthInfo()
        if (domain != null) MastodonClient(domain).getAccountRelationships(profile.id)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { relationships ->
                    this.relationship = relationships.first()

                    val currentAccountId = Common.getCurrentAccessToken()?.accountId
                    if (currentAccountId != null && currentAccountId != profile.id) {
                        binding.follow.visibility = View.VISIBLE
                        binding.block.visibility = View.VISIBLE
                        binding.mute.visibility = View.VISIBLE
                    }

                    setFollowButtonState(this.relationship.following)
                    binding.follow.setOnClickListener {
                        if (this.relationship.following) {
                            MastodonClient(domain).unFollowAccount(profile.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setFollowButtonState(relation.following)
                                    }, Throwable::printStackTrace)
                        } else {
                            MastodonClient(domain).followAccount(profile.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setFollowButtonState(relation.following)
                                    }, Throwable::printStackTrace)
                        }
                    }

                    setBlockButtonState(this.relationship.blocking)
                    binding.block.setOnClickListener {
                        if (this.relationship.blocking) {
                            MastodonClient(domain).unBlockAccount(profile.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setBlockButtonState(relation.blocking)
                                    }, Throwable::printStackTrace)
                        } else {
                            MastodonClient(domain).blockAccount(profile.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setBlockButtonState(relation.blocking)
                                    }, Throwable::printStackTrace)
                        }
                    }

                    setMuteButtonState(this.relationship.blocking)
                    binding.mute.setOnClickListener {
                        if (this.relationship.muting) {
                            MastodonClient(domain).unMuteAccount(profile.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setMuteButtonState(relation.muting)
                                    }, Throwable::printStackTrace)
                        } else {
                            MastodonClient(domain).muteAccount(profile.id)
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .compose(bindToLifecycle())
                                    .subscribe( { relation ->
                                        this.relationship = relation
                                        setMuteButtonState(relation.muting)
                                    }, Throwable::printStackTrace)
                        }
                    }
                }, Throwable::printStackTrace)

        binding.timeline.recyclerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    inTouch = true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    inTouch = false
                }
            }
            false
        }
        binding.timeline.recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val scrollY: Int = recyclerView?.computeVerticalScrollOffset() ?: -1
                onTop = scrollY == 0 || onTop && !(inTouch && scrollY > 0)

                if (!onTop) {
                    val y = scrollY + (recyclerView?.height ?: -1)
                    val h = recyclerView?.computeVerticalScrollRange() ?: -1
                    if (y == h) {
                        showToots(true)
                    }
                }
            }
        })
        binding.timeline.recyclerView.adapter = adapter

        binding.timeline.swipeRefreshLayout.apply {
            setColorSchemeResources(R.color.colorAccent)
            setOnRefreshListener {
                showToots()
            }
        }

        showToots()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        reflectSettings()
        refreshBarTitle()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_AVATAR_PICK_MEDIA -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.let { processPostMedia(it, UploadType.Avatar) }
                }
            }

            REQUEST_CODE_AVATAR_CAPTURE_IMAGE -> {
                if (resultCode == Activity.RESULT_OK) {
                    capturedImageUri?.let { processPostImage(it, UploadType.Avatar) }
                }
            }

            REQUEST_CODE_HEADER_PICK_MEDIA -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.let { processPostMedia(it, UploadType.Header) }
                }
            }

            REQUEST_CODE_HEADER_CAPTURE_IMAGE -> {
                if (resultCode == Activity.RESULT_OK) {
                    capturedImageUri?.let { processPostImage(it, UploadType.Header) }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_GRANT_READ_STORAGE_AVATAR -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    pickMedia(UploadType.Avatar)
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_wifi_grant, Snackbar.LENGTH_SHORT)
                }
            }

            REQUEST_CODE_GRANT_READ_STORAGE_HEADER -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    pickMedia(UploadType.Header)
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_wifi_grant, Snackbar.LENGTH_SHORT)
                }
            }

            REQUEST_CODE_GRANT_WRITE_STORAGE_AVATAR -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    captureImage(UploadType.Avatar)
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_wifi_grant, Snackbar.LENGTH_SHORT)
                }
            }

            REQUEST_CODE_GRANT_WRITE_STORAGE_HEADER -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    captureImage(UploadType.Header)
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_wifi_grant, Snackbar.LENGTH_SHORT)
                }
            }
        }
    }

    private fun initViewsVisibility() {
        listOf(
                binding.buttonFavList,
                binding.buttonCancelEdit,
                binding.buttonEdit
        )
                .forEach { it.visibility = View.GONE }
    }

    private fun showFavList() {
        (activity as MainActivity).showTimelineFragment(TimelineFragment.Category.Fav)
    }

    private fun toggleEditMode(enter: Boolean, button: Button, save: Boolean = true) { // FIXME: 常に編集できるようになってる
        if (enter) {
            button.text = getString(R.string.button_edit_save)
            binding.buttonCancelEdit.apply {
                setOnClickListener { toggleEditMode(false, button, false) }
                visibility = View.VISIBLE
            }

            binding.icon.setOnClickListener { showImageUploader(UploadType.Avatar) }
            binding.header.setOnClickListener { showImageUploader(UploadType.Header) }

            listOf(
                    binding.screenName,
                    binding.note
            )
                    .forEach { editText ->
                        (editText.tag as? TextKeyListener)?.let {
                            editText.keyListener = it
                        }
                    }
        } else {
            button.text = getString(R.string.button_edit)

            binding.buttonCancelEdit.apply {
                setOnClickListener {}
                visibility = View.GONE
            }

            binding.icon.setOnClickListener { showImageViewer(listOf(profile.iconUrl), 0) }
            binding.header.setOnClickListener { showImageViewer(listOf(profile.headerUrl), 0) }

            listOf(
                    binding.screenName,
                    binding.note
            )
                    .forEach {
                        it.tag = it.keyListener
                        it.setOnKeyListener(null)
                    }

            if (save) submitEdit(button)
        }
    }

    private fun showImageUploader(type: UploadType) = ChooseImageSourceDialogFragment.newInstance(type)
            .apply {
                setTargetFragment(this@AccountProfileFragment, 0)
                show(this@AccountProfileFragment.activity.supportFragmentManager, ChooseImageSourceDialogFragment.TAG)
            }

    fun pickMedia(uploadType: UploadType) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), when (uploadType) {
                UploadType.Avatar -> REQUEST_CODE_GRANT_READ_STORAGE_AVATAR
                UploadType.Header -> REQUEST_CODE_GRANT_READ_STORAGE_HEADER
            })
        } else {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/* video/*"
            }
            startActivityForResult(intent, when (uploadType) {
                UploadType.Avatar -> REQUEST_CODE_AVATAR_PICK_MEDIA
                UploadType.Header -> REQUEST_CODE_HEADER_PICK_MEDIA
            })
        }
    }

    fun captureImage(uploadType: UploadType) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), when (uploadType) {
                UploadType.Avatar -> REQUEST_CODE_GRANT_WRITE_STORAGE_AVATAR
                UploadType.Header -> REQUEST_CODE_GRANT_WRITE_STORAGE_HEADER
            })
        } else {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.TITLE, "${System.currentTimeMillis()}.jpg")
            capturedImageUri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
            }

            startActivityForResult(intent, when (uploadType) {
                UploadType.Avatar -> REQUEST_CODE_AVATAR_CAPTURE_IMAGE
                UploadType.Header -> REQUEST_CODE_HEADER_CAPTURE_IMAGE
            })
        }
    }

    private fun processPostMedia(data: Intent, type: UploadType) {
        data.data?.let { uri ->
            bindMedia(activity.contentResolver.openInputStream(uri), type)
        }
    }

    private fun processPostImage(uri: Uri, type: UploadType) {
        getImagePathFromUriAsSingle(uri)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ path ->
                    bindImage(File(path), type)
                }, Throwable::printStackTrace)
    }

    private fun bindMedia(stream: InputStream, type: UploadType) {
        val bytes = IOUtils.toByteArray(stream)
        val base64String = "data:image/png;base64,${Base64.encodeToString(bytes, Base64.DEFAULT)}"

        when (type) {
            UploadType.Avatar -> {
                Glide.with(activity).load(bytes).into(binding.icon)
                editedProfile.iconImg = base64String
            }

            UploadType.Header -> {
                Glide.with(activity).load(bytes).into(binding.header)
                editedProfile.headerImg = base64String
            }
        }
    }

    private fun bindImage(file: File, type: UploadType) {
        val bytes = file.readBytes()
        val base64String = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.DEFAULT)}"

        when (type) {
            UploadType.Avatar -> {
                Glide.with(activity).load(bytes).into(binding.icon)
                editedProfile.iconImg = base64String
            }

            UploadType.Header -> {
                Glide.with(activity).load(bytes).into(binding.header)
                editedProfile.headerImg = base64String
            }
        }
    }

    private fun getImagePathFromUriAsSingle(uri: Uri): Single<String> {
        return Single.just(MediaStore.Images.Media.DATA)
                .map { projection ->
                    val cursor = activity.contentResolver.query(uri, arrayOf(projection), null, null, null)
                    cursor.moveToFirst()

                    cursor.getString(cursor.getColumnIndexOrThrow(projection)).apply { cursor.close() }
                }
    }

    private fun submitEdit(button: Button) {
        editedProfile.apply {
            screenName = binding.screenName.text.toString()
            note = binding.note.text
        }
        val displayName = if (profile.screenName == editedProfile.screenName) null else editedProfile.screenName
        val note = if (profile.note.toString() == editedProfile.note.toString()) null else editedProfile.note.toString()
        val avatar = if (profile.iconImg == editedProfile.iconImg) null else editedProfile.iconImg
        val header = if (profile.headerImg == editedProfile.headerImg) null else editedProfile.headerImg

        profile = editedProfile.copy()

        if (displayName == null &&
                note == null &&
                avatar == null &&
                header == null) {
            Snackbar.make(binding.root, R.string.none_update_edit_profile, Snackbar.LENGTH_SHORT).show()
            return
        }

        Common.resetAuthInfo()?.let {
            MastodonClient(it).updateOwnAccount(displayName, note, avatar, header)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({
                        if (displayName != null) refreshBarTitle(displayName)
                        Snackbar.make(binding.root, R.string.succeed_edit_profile, Snackbar.LENGTH_SHORT).show()
                        toggleEditMode(false, button, false)
                    }, Throwable::printStackTrace)
        }
    }

    private fun showImageViewer(urls: List<String>, position: Int) {
        val fragment = ShowImagesDialogFragment.newInstance(urls, position)
        activity.supportFragmentManager.beginTransaction()
                .add(fragment, ShowImagesDialogFragment.TAG)
                .addToBackStack(ShowImagesDialogFragment.TAG)
                .commit()
    }

    private fun showToots(loadNext: Boolean = false) {
        if (loadNext && maxId == -1L) return

        Common.resetAuthInfo()?.let {
            MastodonClient(it).getAccountAllToots(profile.id, if (loadNext) maxId else null, if (!loadNext && sinceId != -1L) sinceId else null)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({ result ->
                        reflectStatuses(result, loadNext)
                    }, Throwable::printStackTrace)
        }
    }

    private fun getRegexExtractSinceId() = Regex(".*since_id=(\\d+?)>.*")
    private fun getRegexExtractMaxId() = Regex(".*max_id=(\\d+?)>.*")

    private fun reflectStatuses(result: Result<List<Status>>, next: Boolean) {
        result.response()?.let {
            if (next) adapter.addAllContentsAtLast(it.body().map { Common.getTimelineContent(it) })
            else adapter.addAllContents(it.body().map { Common.getTimelineContent(it) })

            maxId = it.headers().get("Link")?.let {
                if (it.contains("max_id")) {
                    try {
                        it.replace(getRegexExtractMaxId(), "$1").toLong()
                    } catch (e: NumberFormatException) {
                        e.printStackTrace()
                        maxId
                    }
                } else maxId
            } ?: -1L
            sinceId = it.headers().get("Link")?.let {
                if (it.contains("since_id")) {
                    try {
                        it.replace(getRegexExtractSinceId(), "$1").toLong()
                    } catch (e: NumberFormatException) {
                        e.printStackTrace()
                        sinceId
                    }
                } else sinceId
            } ?: -1L

            toggleRefreshIndicatorState(false)
        }
    }

    private fun setFollowButtonState(state: Boolean) {
        if (state) {
            binding.follow.setImageResource(R.drawable.ic_people_black_24px)
            binding.follow.setColorFilter(ContextCompat.getColor(activity, R.color.accent))
        } else {
            binding.follow.setImageResource(R.drawable.ic_person_add_black_24px)
            binding.follow.setColorFilter(ContextCompat.getColor(activity, R.color.icon_tint_dark))
        }
    }

    private fun setBlockButtonState(state: Boolean) {
        binding.block.setColorFilter(
                ContextCompat.getColor(activity,
                        if (state) R.color.accent else R.color.icon_tint_dark))
    }

    private fun setMuteButtonState(state: Boolean) {
        binding.mute.setColorFilter(
                ContextCompat.getColor(activity,
                        if (state) R.color.accent else R.color.icon_tint_dark))
    }

    private fun reflectSettings() {
        val movementMethod = Common.getMovementMethodFromPreference(binding.root.context)
        binding.url.movementMethod = movementMethod
        binding.note.movementMethod = movementMethod
    }

    private fun refreshBarTitle(name: String = profile.screenName) {
        (activity as MainActivity).supportActionBar?.title = "$name's profile"
    }

    private fun toggleRefreshIndicatorState(show: Boolean) {
        binding.timeline.swipeRefreshLayout.isRefreshing = show
    }
}