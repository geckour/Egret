package com.geckour.egret.view.fragment

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.service.MastodonService
import com.geckour.egret.databinding.FragmentCreateNewTootBinding
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File


class NewTootCreateFragment : BaseFragment() {

    lateinit var binding: FragmentCreateNewTootBinding
    private val postMediaReqs: ArrayList<Disposable> = ArrayList()
    private var mediaCount: Int = 0
    private val mediaIds: ArrayList<Long> = ArrayList()
    private var capturedImageUri: Uri? = null

    companion object {
        val TAG: String = this::class.java.simpleName
        private val ARGS_KEY_CURRENT_TOKEN_ID = "currentTokenId"
        private val ARGS_KEY_POST_TOKEN_ID = "postTokenId"
        private val ARGS_KEY_REPLY_TO_STATUS_ID = "replyToStatusId"
        private val ARGS_KEY_REPLY_TO_ACCOUNT_NAME = "replyToAccountName"
        private val REQUEST_CODE_PICK_MEDIA = 1
        private val REQUEST_CODE_CAPTURE_IMAGE = 2
        private val REQUEST_CODE_GRANT_READ_STORAGE = 3
        private val REQUEST_CODE_GRANT_WRITE_STORAGE = 4

        fun newInstance(
                currentTokenId: Long,
                postTokenId: Long = currentTokenId,
                replyToStatusId: Long? = null,
                replyToAccountName: String? = null): NewTootCreateFragment {

            val fragment = NewTootCreateFragment()
            val args = Bundle()
            args.putLong(ARGS_KEY_CURRENT_TOKEN_ID, currentTokenId)
            args.putLong(ARGS_KEY_POST_TOKEN_ID, postTokenId)
            if (replyToStatusId != null) args.putLong(ARGS_KEY_REPLY_TO_STATUS_ID, replyToStatusId)
            if (replyToAccountName != null) args.putString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME, replyToAccountName)
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (activity as MainActivity).supportActionBar?.hide()
        (activity as MainActivity).binding.appBarMain.contentMain.fab.hide()
    }

    override fun onResume() {
        super.onResume()

        (activity as MainActivity).supportActionBar?.hide()
        (activity as MainActivity).binding.appBarMain.contentMain.fab.hide()
        (activity as MainActivity)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_create_new_toot, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val token = OrmaProvider.db.selectFromAccessToken().idEq(arguments.getLong(ARGS_KEY_POST_TOKEN_ID)).last()
        val domain = OrmaProvider.db.selectFromInstanceAuthInfo().idEq(token.instanceId).last().instance
        OrmaProvider.db.updateAccessToken().isCurrentEq(true).isCurrent(false).executeAsSingle()
                .flatMap { OrmaProvider.db.updateAccessToken().idEq(token.id).isCurrent(true).executeAsSingle() }
                .flatMap { MastodonClient(Common.resetAuthInfo() ?: throw IllegalArgumentException()).getSelfAccount() }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { account ->
                    val content = Common.getNewTootIdentifyContent(domain, token, account)
                    binding.content = content
                    Picasso.with(binding.icon.context).load(content.avatarUrl).into(binding.icon)
                }, Throwable::printStackTrace)

        Common.showSoftKeyBoardOnFocusEditText(binding.tootBody)

        binding.gallery.setOnClickListener { pickMedia() }
        binding.camera.setOnClickListener { captureImage() }

        binding.tootBody.setOnKeyListener { v, keyCode, event ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            (v as EditText).let {
                                if (it.selectionStart == 0 && it.selectionStart == it.selectionEnd) {
                                    it.requestFocusFromTouch()
                                    it.requestFocus()
                                    it.setSelection(0)
                                    true
                                } else false
                            }
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            (v as EditText).let {
                                if (it.selectionEnd == it.text.length && it.selectionStart == it.selectionEnd) {
                                    it.requestFocusFromTouch()
                                    it.requestFocus()
                                    it.setSelection(it.text.length)
                                    true
                                } else false
                            }
                        }

                        else -> false
                    }
                }

                else -> false
            }
        }

        binding.switchCw.setOnCheckedChangeListener { _, isChecked ->
            binding.tootAlertBody.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.dividerBody.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.spinnerVisibility.adapter =
                ArrayAdapter.createFromResource(activity, R.array.spinner_toot_visibility, android.R.layout.simple_spinner_item)
                        .apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }

        binding.buttonToot.setOnClickListener {
            binding.buttonToot.isEnabled = false

            postToot(binding.tootBody.text.toString())
        }

        if (arguments.containsKey(ARGS_KEY_REPLY_TO_STATUS_ID)
                && arguments.containsKey(ARGS_KEY_REPLY_TO_ACCOUNT_NAME)
                && arguments.getString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME) != null) {
            binding.replyTo.text = "reply to: ${arguments.getString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME)}"
            binding.replyTo.visibility = View.VISIBLE
            val accountName = "${arguments.getString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME)} "
            binding.tootBody.text = Editable.Factory.getInstance().newEditable(accountName)
            binding.tootBody.setSelection(accountName.length)
        }
    }

    override fun onPause() {
        super.onPause()
        postMediaReqs.forEach { it.dispose() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_PICK_MEDIA -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.let { bindMedia(it) }
                }
            }

            REQUEST_CODE_CAPTURE_IMAGE -> {
                if (resultCode == Activity.RESULT_OK) {
                    capturedImageUri?.let { bindImage(it) }
                }
            }
        }
    }

    fun postToot(body: String) {
        if (body.isBlank()) {
            Snackbar.make(binding.root, R.string.error_empty_toot, Snackbar.LENGTH_SHORT)
            return
        }
        MastodonClient(Common.resetAuthInfo() ?: return)
                .postNewToot(
                        body = body,
                        inReplyToId = if (binding.replyTo.visibility == View.VISIBLE) arguments.getLong(ARGS_KEY_REPLY_TO_STATUS_ID) else null,
                        mediaIds = if (mediaIds.size > 0) mediaIds else null,
                        isSensitive = binding.switchNsfw.isChecked,
                        spoilerText = if (binding.switchCw.isChecked) binding.tootAlertBody.text.toString() else null,
                        visibility = when (binding.spinnerVisibility.selectedItemPosition) {
                            0 -> MastodonService.Visibility.public
                            1 -> MastodonService.Visibility.unlisted
                            2 -> MastodonService.Visibility.private
                            3 -> MastodonService.Visibility.direct
                            else -> null
                        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe( { onPostSuccess() }, Throwable::printStackTrace)
    }

    fun pickMedia() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_GRANT_READ_STORAGE)
        } else {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/* video/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_MEDIA)
        }
    }

    fun captureImage() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_GRANT_WRITE_STORAGE)
        } else {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.TITLE, "${System.currentTimeMillis()}.jpg")
            capturedImageUri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
            }

            startActivityForResult(intent, REQUEST_CODE_CAPTURE_IMAGE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_GRANT_READ_STORAGE -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.filter { it != PackageManager.PERMISSION_GRANTED }.isEmpty()) {
                    pickMedia()
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_read_storage_grant, Snackbar.LENGTH_SHORT)
                }
            }


            REQUEST_CODE_GRANT_WRITE_STORAGE -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.filter { it != PackageManager.PERMISSION_GRANTED }.isEmpty()) {
                    captureImage()
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_write_storage_grant, Snackbar.LENGTH_SHORT)
                }
            }
        }
    }

    fun bindMedia(data: Intent) {
        Common.resetAuthInfo()?.let { domain ->
            if (data.clipData != null) {
                getMediaPathsFromClipDataAsObservable(data.clipData)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({ (path, uri) ->
                            if (++mediaCount < 5) {
                                postMedia(domain, path, uri)
                            } else {
                                Snackbar.make(binding.root, R.string.error_too_many_media, Snackbar.LENGTH_SHORT).show()
                            }
                        }, Throwable::printStackTrace)
            }
            if (data.data != null && ++mediaCount < 5) {
                getMediaPathFromUriAsSingle(data.data)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({
                            if (++mediaCount < 5) {
                                postMedia(domain, it, data.data)
                            } else {
                                Snackbar.make(binding.root, R.string.error_too_many_media, Snackbar.LENGTH_SHORT).show()
                            }
                        }, Throwable::printStackTrace)
            }
        }
    }

    fun bindImage(uri: Uri) {
        getImagePathFromUriAsSingle(uri)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ path ->
                    if (++mediaCount < 5) {
                        Common.resetAuthInfo()?.let {
                            postImage(it, path, uri)
                        }
                    } else {
                        Snackbar.make(binding.root, R.string.error_too_many_media, Snackbar.LENGTH_SHORT).show()
                    }
                }, Throwable::printStackTrace)
    }

    fun postMedia(domain: String, path: String, uri: Uri) {
        val file = File(path)
        val body = MultipartBody.Part.createFormData(
                "file",
                file.name,
                RequestBody.create(MediaType.parse(activity.contentResolver.getType(uri)), file))

        postMediaReqs.add(
                MastodonClient(domain).postNewMedia(body)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({
                            mediaIds.add(it.id)
                            indicateMedia(uri)
                        }, { throwable ->
                            throwable.printStackTrace()
                            mediaCount--
                            Snackbar.make(binding.root, R.string.error_unable_upload_media, Snackbar.LENGTH_SHORT).show()
                        })
        )
    }

    fun postImage(domain: String, path: String, uri: Uri) {
        val file = File(path)
        val body = MultipartBody.Part.createFormData(
                "file",
                file.name,
                RequestBody.create(MediaType.parse("jpeg"), file))

        postMediaReqs.add(
                MastodonClient(domain).postNewMedia(body)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({
                            mediaIds.add(it.id)
                            indicateImage(uri)
                        }, { throwable ->
                            throwable.printStackTrace()
                            mediaCount--
                            Snackbar.make(binding.root, R.string.error_unable_upload_media, Snackbar.LENGTH_SHORT).show()
                        })
        )
    }

    fun indicateMedia(uri: Uri) {
        Single.just(
                MediaStore.Images.Thumbnails.getThumbnail(
                        activity.contentResolver,
                        DocumentsContract.getDocumentId(uri).split(":").last().toLong(),
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    val mediaViews: List<ImageView> = listOf(
                            binding.media1,
                            binding.media2,
                            binding.media3,
                            binding.media4
                    )

                    mediaViews.filter { it.drawable == null }.firstOrNull()?.setImageBitmap(it)
                }, Throwable::printStackTrace)
    }

    fun indicateImage(uri: Uri) {
        Single.just(
                MediaStore.Images.Thumbnails.getThumbnail(
                        activity.contentResolver,
                        ContentUris.parseId(uri),
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({
                    val mediaViews: List<ImageView> = listOf(
                            binding.media1,
                            binding.media2,
                            binding.media3,
                            binding.media4
                    )

                    mediaViews.filter { it.drawable == null }.firstOrNull()?.setImageBitmap(it)

                    deleteTempImage()
                }, Throwable::printStackTrace)
    }

    fun getMediaPathFromUriAsSingle(uri: Uri): Single<String> {
        val projection = MediaStore.Images.Media.DATA

        return Single.just(DocumentsContract.getDocumentId(uri).split(":").last())
                .map {
                    val cursor = activity.contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(projection),
                            "${MediaStore.Images.Media._ID} = ?",
                            arrayOf(it), null)
                    cursor.moveToFirst()

                    cursor.getString(cursor.getColumnIndexOrThrow(projection)).apply { cursor.close() }
                }
    }

    fun getMediaPathsFromClipDataAsObservable(clip: ClipData): Observable<Pair<String, Uri>> {
        val docIds: ArrayList<Pair<String, Uri>> = ArrayList()
        val projection = MediaStore.Images.Media.DATA

        (0..clip.itemCount - 1).mapTo(docIds) {
            val uri = clip.getItemAt(it).uri
            Pair(DocumentsContract.getDocumentId(uri).split(":").last(), uri)
        }

        return Observable.fromIterable(docIds)
                .map {
                    val cursor = activity.contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(projection),
                            "${MediaStore.Images.Media._ID} = ?",
                            arrayOf(it.first), null)
                    cursor.moveToFirst()

                    val path = cursor.getString(cursor.getColumnIndexOrThrow(projection))

                    Pair(path, it.second).apply { cursor.close() }
                }
    }

    fun getImagePathFromUriAsSingle(uri: Uri): Single<String> {
        return Single.just(MediaStore.Images.Media.DATA)
                .map { projection ->
                    val cursor = activity.contentResolver.query(uri, arrayOf(projection), null, null, null)
                    cursor.moveToFirst()

                    cursor.getString(cursor.getColumnIndexOrThrow(projection)).apply { cursor.close() }
                }
    }

    fun deleteTempImage() {
        capturedImageUri?.let {
            getImagePathFromUriAsSingle(it)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({ path ->
                        File(path).apply { if (this.exists()) this.delete() }
                        activity.contentResolver.delete(it, null, null)

                        capturedImageUri = null
                    })
        }
    }

    fun onPostSuccess() {
        (activity as MainActivity).supportFragmentManager.popBackStack()
    }
}