package com.geckour.egret.view.fragment

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.DialogInterface
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
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import com.bumptech.glide.Glide
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Attachment
import com.geckour.egret.api.service.MastodonService
import com.geckour.egret.databinding.FragmentCreateNewTootBinding
import com.geckour.egret.model.Draft
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.activity.ShareActivity
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File


class NewTootCreateFragment : BaseFragment(), MainActivity.OnBackPressedListener, SelectDraftDialogFragment.OnSelectDraftItemListener {

    lateinit private var binding: FragmentCreateNewTootBinding
    private val postMediaReqs: ArrayList<Disposable> = ArrayList()
    private val attachments: ArrayList<Attachment> = ArrayList()
    private var capturedImageUri: Uri? = null
    lateinit private var initialBody: String
    lateinit private var initialAlertBody: String
    private var isSuccessPost = false
    private val drafts: ArrayList<Draft> = ArrayList()
    private var draft: Draft? = null

    companion object {
        val TAG: String = this::class.java.simpleName
        private val ARGS_KEY_CURRENT_TOKEN_ID = "currentTokenId"
        private val ARGS_KEY_POST_TOKEN_ID = "postTokenId"
        private val ARGS_KEY_REPLY_TO_STATUS_ID = "replyToStatusId"
        private val ARGS_KEY_REPLY_TO_ACCOUNT_NAME = "replyToAccountName"
        private val ARGS_KEY_BODY = "argsKeyBody"
        private val REQUEST_CODE_PICK_MEDIA = 1
        private val REQUEST_CODE_CAPTURE_IMAGE = 2
        private val REQUEST_CODE_GRANT_READ_STORAGE = 3
        private val REQUEST_CODE_GRANT_WRITE_STORAGE = 4

        fun newInstance(
                currentTokenId: Long,
                postTokenId: Long = currentTokenId,
                replyToStatusId: Long? = null,
                replyToAccountName: String? = null,
                body: String? = null) = NewTootCreateFragment().apply {
            arguments = Bundle().apply {
                putLong(ARGS_KEY_CURRENT_TOKEN_ID, currentTokenId)
                putLong(ARGS_KEY_POST_TOKEN_ID, postTokenId)
                if (replyToStatusId != null) putLong(ARGS_KEY_REPLY_TO_STATUS_ID, replyToStatusId)
                if (replyToAccountName != null) putString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME, replyToAccountName)
                if (body != null) putString(ARGS_KEY_BODY, body)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (activity as? MainActivity)?.supportActionBar?.hide()
        (activity as? MainActivity)?.binding?.appBarMain?.contentMain?.fab?.hide()
    }

    override fun onResume() {
        super.onResume()

        (activity as? MainActivity)?.supportActionBar?.hide()
        (activity as? MainActivity)?.binding?.appBarMain?.contentMain?.fab?.hide()
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
                .flatMap { MastodonClient(Common.resetAuthInfo() ?: throw IllegalArgumentException()).getOwnAccount() }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe( { account ->
                    val content = Common.getNewTootIdentifyContent(domain, token, account)
                    binding.content = content
                    Glide.with(binding.icon.context).load(content.avatarUrl).into(binding.icon)
                }, Throwable::printStackTrace)

        Common.showSoftKeyBoardOnFocusEditText(binding.tootBody)

        binding.gallery.setOnClickListener { pickMedia() }
        binding.camera.setOnClickListener { captureImage() }

        if (arguments.containsKey(ARGS_KEY_BODY))
            binding.tootBody.text = Editable.Factory.getInstance().newEditable(arguments.getString(ARGS_KEY_BODY))

        if (arguments.containsKey(ARGS_KEY_REPLY_TO_STATUS_ID)
                && arguments.containsKey(ARGS_KEY_REPLY_TO_ACCOUNT_NAME)
                && arguments.getString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME) != null) {
            binding.replyTo.text = "reply to: ${arguments.getString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME)}"
            binding.replyTo.visibility = View.VISIBLE
            val accountName = "${arguments.getString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME)} "
            binding.tootBody.text = Editable.Factory.getInstance().newEditable(accountName)
            binding.tootBody.setSelection(accountName.length)
        }

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

        initialBody = binding.tootBody.text.toString()
        initialAlertBody = binding.tootAlertBody.text.toString()

        Common.getCurrentAccessToken()?.id?.let {
            drafts.addAll(
                    OrmaProvider.db.relationOfDraft()
                            .tokenIdEq(it)
                            .orderByCreatedAtAsc()
            )
        }
        binding.draft.apply {
            if (drafts.isNotEmpty()) {
                visibility = View.VISIBLE
                setOnClickListener { onLoadDraft() }
            } else {
                visibility = View.INVISIBLE
                setOnClickListener(null)
            }
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
                    data?.let { postMedia(it) }
                }
            }

            REQUEST_CODE_CAPTURE_IMAGE -> {
                if (resultCode == Activity.RESULT_OK) {
                    capturedImageUri?.let { postImage(it) }
                }
            }
        }
    }

    override fun onBackPressedInMainActivity(callback: (Boolean) -> Any) {
        if (isSuccessPost.not() &&
                (initialBody != binding.tootBody.text.toString() || initialAlertBody != binding.tootAlertBody.text.toString())) {
            AlertDialog.Builder(activity)
                    .setTitle(R.string.dialog_title_confirm_save)
                    .setMessage(R.string.dialog_message_confirm_save)
                    .setPositiveButton(R.string.dialog_button_ok_confirm_save, { dialog, _ ->
                        saveAsDraft(dialog, callback)
                    })
                    .setNegativeButton(R.string.dialog_button_dismiss_confirm_save, { dialog, _ ->
                        dialog.dismiss()
                        callback(true)
                    })
                    .setNeutralButton(R.string.dialog_button_cancel_confirm_save, { dialog, _ ->
                        dialog.dismiss()
                        callback(false)
                    })
                    .show()
        } else callback(true)
    }

    override fun onSelect(draft: Draft) {
        this.draft = draft
        OrmaProvider.db.relationOfDraft()
                .deleter()
                .idEq(draft.id)
                .executeAsSingle()
                .map {
                    Common.getCurrentAccessToken()?.id?.let {
                        OrmaProvider.db.relationOfDraft()
                                .tokenIdEq(it)
                                .orderByCreatedAtAsc()
                                .toList()
                    } ?: arrayListOf()
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ drafts ->
                    this.drafts.apply {
                        clear()
                        addAll(drafts)
                    }
                    if (this.drafts.isEmpty()) binding.draft.apply {
                        visibility = View.INVISIBLE
                        setOnClickListener(null)
                    }
                    var account = ""
                    if (draft.inReplyToId != null && draft.inReplyToName != null) {
                        binding.replyTo.text = "reply to: ${draft.inReplyToName}"
                        binding.replyTo.visibility = View.VISIBLE
                        account = "${draft.inReplyToName} "
                    }
                    val body = "$account${draft.body}"
                    binding.tootBody.setText(body)
                    binding.tootBody.setSelection(body.length)
                    binding.tootAlertBody.setText(draft.alertBody)
                    this.attachments.apply {
                        clear()
                        addAll(draft.attachments.value)
                    }
                    Observable.fromIterable(this.attachments.mapIndexed { i, attachment -> Pair(i, attachment)})
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ (i, attachment) ->
                                Glide.with(activity).load(attachment.previewImgUrl).into(
                                        when (i) {
                                            0 -> binding.media1
                                            1 -> binding.media2
                                            2 -> binding.media3
                                            3 -> binding.media4
                                            else -> throw IndexOutOfBoundsException("There are attachments over 4.")
                                        }
                                )
                            }, Throwable::printStackTrace)
                    binding.switchCw.isChecked = draft.warning
                    binding.switchNsfw.isChecked = draft.sensitive
                    binding.spinnerVisibility.setSelection(draft.visibility)
                }, Throwable::printStackTrace)
    }

    private fun postToot(body: String) {
        if (body.isBlank()) {
            Snackbar.make(binding.root, R.string.error_empty_toot, Snackbar.LENGTH_SHORT)
            return
        }

        MastodonClient(Common.resetAuthInfo() ?: return)
                .postNewToot(
                        body = body,
                        inReplyToId = if (binding.replyTo.visibility == View.VISIBLE) draft?.inReplyToId ?: arguments.getLong(ARGS_KEY_REPLY_TO_STATUS_ID) else null,
                        mediaIds = if (attachments.size > 0) attachments.map { it.id } else null,
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

    private fun saveAsDraft(dialog: DialogInterface, callback: (Boolean) -> Any, draftId: Long? = null) {
        Common.getCurrentAccessToken()?.let { (id) ->
            if (draftId == null) {
                OrmaProvider.db.relationOfDraft()
                        .insertAsSingle {
                            Draft(
                                    tokenId = id,
                                    body = binding.tootBody.text.toString(),
                                    alertBody = binding.tootAlertBody.text.toString(),
                                    inReplyToId = if (binding.replyTo.visibility == View.VISIBLE) draft?.inReplyToId ?: arguments.getLong(ARGS_KEY_REPLY_TO_STATUS_ID) else null,
                                    inReplyToName = if (binding.replyTo.visibility == View.VISIBLE) draft?.inReplyToName ?: arguments.getString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME) else null,
                                    attachments = Draft.Attachments(attachments),
                                    warning = binding.switchCw.isChecked,
                                    sensitive = binding.switchNsfw.isChecked,
                                    visibility = binding.spinnerVisibility.selectedItemPosition
                            )
                        }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            Snackbar.make(binding.root, R.string.complete_save_draft, Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                            callback(true)
                        }, { throwable ->
                            throwable.printStackTrace()
                            Snackbar.make(binding.root, R.string.failure_save_draft, Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                            callback(false)
                        })
            } else {
                OrmaProvider.db.relationOfDraft()
                        .upsertAsSingle(
                                Draft(
                                        id = draftId,
                                        tokenId = id,
                                        body = binding.tootBody.text.toString(),
                                        alertBody = binding.tootAlertBody.text.toString(),
                                        inReplyToId = if (binding.replyTo.visibility == View.VISIBLE) arguments.getLong(ARGS_KEY_REPLY_TO_STATUS_ID) else null,
                                        attachments = Draft.Attachments(attachments),
                                        sensitive = binding.switchNsfw.isChecked,
                                        visibility = when (binding.spinnerVisibility.selectedItemPosition) {
                                            0 -> MastodonService.Visibility.public.ordinal
                                            1 -> MastodonService.Visibility.unlisted.ordinal
                                            2 -> MastodonService.Visibility.private.ordinal
                                            3 -> MastodonService.Visibility.direct.ordinal
                                            else -> -1
                                        }
                                )
                        )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            Snackbar.make(binding.root, R.string.complete_save_draft, Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                            callback(true)
                        }, { throwable ->
                            throwable.printStackTrace()
                            Snackbar.make(binding.root, R.string.failure_save_draft, Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                            callback(false)
                        })
            }
        }
    }

    private fun onLoadDraft() {
        SelectDraftDialogFragment.newInstance(drafts)
                .apply {
                    setTargetFragment(this@NewTootCreateFragment, 0)
                }
                .show(activity.supportFragmentManager, SelectDraftDialogFragment.TAG)
    }

    private fun pickMedia() {
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

    private fun captureImage() {
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
                        grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    pickMedia()
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_read_storage_grant, Snackbar.LENGTH_SHORT).show()
                }
            }

            REQUEST_CODE_GRANT_WRITE_STORAGE -> {
                if (grantResults.isNotEmpty() &&
                        grantResults.none { it != PackageManager.PERMISSION_GRANTED }) {
                    captureImage()
                } else {
                    Snackbar.make(binding.root, R.string.message_necessity_write_storage_grant_capture, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun postMedia(data: Intent) {
        if (attachments.size < 4) {
            Common.resetAuthInfo()?.let { domain ->
                if (data.clipData != null) {
                    getMediaPathsFromClipDataAsObservable(data.clipData)
                            .flatMap { (path, uri) ->
                                queryPostImageToAPI(domain, path, uri).toObservable()
                            }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                attachments.add(it)
                                indicateImage(it.previewImgUrl)
                            }, { throwable ->
                                throwable.printStackTrace()
                                Snackbar.make(binding.root, R.string.error_unable_upload_media, Snackbar.LENGTH_SHORT).show()
                            })
                }

                if (data.data != null) {
                    getMediaPathFromUriAsSingle(data.data)
                            .flatMap { (path, uri) ->
                                queryPostImageToAPI(domain, path, uri)
                            }
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .compose(bindToLifecycle())
                            .subscribe({
                                attachments.add(it)
                                indicateImage(it.previewImgUrl)
                            }, { throwable ->
                                throwable.printStackTrace()
                                Snackbar.make(binding.root, R.string.error_unable_upload_media, Snackbar.LENGTH_SHORT).show()
                            })
                }
            }
        } else Snackbar.make(binding.root, R.string.error_too_many_media, Snackbar.LENGTH_SHORT).show()
    }

    private fun postImage(uri: Uri) {
        if (attachments.size < 4) {
            Common.resetAuthInfo()?.let { domain ->
                getImagePathFromUriAsSingle(uri)
                        .flatMap { path ->
                            queryPostImageToAPI(domain, path, uri)
                        }
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({
                            attachments.add(it)
                            indicateImage(it.url)
                        }, { throwable ->
                            throwable.printStackTrace()
                            Snackbar.make(binding.root, R.string.error_unable_upload_media, Snackbar.LENGTH_SHORT).show()
                        })
            }
        } else {
            Snackbar.make(binding.root, R.string.error_too_many_media, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun queryPostImageToAPI(domain: String, path: String, uri: Uri): Single<Attachment> {
        val file = File(path)
        val body = MultipartBody.Part.createFormData(
                "file",
                file.name,
                RequestBody.create(MediaType.parse(activity.contentResolver.getType(uri)), file))

        return MastodonClient(domain).postNewMedia(body)
    }

    private fun indicateImage(url: String, index: Int = attachments.size) {
        when (index) {
            0 -> Glide.with(activity).load(url).into(binding.media1)
            1 -> Glide.with(activity).load(url).into(binding.media2)
            2 -> Glide.with(activity).load(url).into(binding.media3)
            3 -> Glide.with(activity).load(url).into(binding.media4)
            else -> {}
        }
    }

    private fun getMediaPathFromUriAsSingle(uri: Uri): Single<Pair<String, Uri>> {
        val projection = MediaStore.Images.Media.DATA

        return Single.just(DocumentsContract.getDocumentId(uri).split(":").last())
                .map {
                    val cursor = activity.contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(projection),
                            "${MediaStore.Images.Media._ID} = ?",
                            arrayOf(it), null)
                    cursor.moveToFirst()

                    val path = cursor.getString(cursor.getColumnIndexOrThrow(projection)).apply { cursor.close() }
                    Pair(path, uri)
                }
    }

    private fun getMediaPathsFromClipDataAsObservable(clip: ClipData): Observable<Pair<String, Uri>> {
        val docIds: ArrayList<Pair<String, Uri>> = ArrayList()
        val projection = MediaStore.Images.Media.DATA

        (0 until clip.itemCount).mapTo(docIds) {
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

    private fun getImagePathFromUriAsSingle(uri: Uri): Single<String> {
        return Single.just(MediaStore.Images.Media.DATA)
                .map { projection ->
                    val cursor = activity.contentResolver.query(uri, arrayOf(projection), null, null, null)
                    cursor.moveToFirst()

                    cursor.getString(cursor.getColumnIndexOrThrow(projection)).apply { cursor.close() }
                }
    }

    private fun deleteTempImage() {
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

    private fun onPostSuccess() {
        isSuccessPost = true
        (activity as? MainActivity)?.supportFragmentManager?.popBackStack()
        (activity as? ShareActivity)?.apply {
            supportFragmentManager?.popBackStack()
            onBackPressed()
        }
    }
}