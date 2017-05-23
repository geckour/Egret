package com.geckour.egret.view.fragment

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.text.Editable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.geckour.egret.R
import com.geckour.egret.api.MastodonClient
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
    private var postMediaCount: Int = 0
    private val mediaIds: ArrayList<Long> = ArrayList()

    companion object {
        val TAG = "createNewTootFragment"
        private val ARGS_KEY_CURRENT_TOKEN_ID = "currentTokenId"
        private val ARGS_KEY_POST_TOKEN_ID = "postTokenId"
        private val ARGS_KEY_REPLY_TO_STATUS_ID = "replyToStatusId"
        private val ARGS_KEY_REPLY_TO_ACCOUNT_NAME = "replyToAccountName"
        private val REQUEST_CODE_PICK_MEDIA = 1

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
        ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.hide()
    }

    override fun onResume() {
        super.onResume()

        (activity as MainActivity).supportActionBar?.hide()
        ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.hide()
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

        (activity as MainActivity).showSoftKeyBoardOnFocusEditText(binding.tootBody)

        binding.gallery.setOnClickListener { pickMedia() }

        binding.buttonToot.setOnClickListener {
            binding.buttonToot.isEnabled = false

            postToot(binding.tootBody.text.toString())
        }

        if (arguments.containsKey(ARGS_KEY_REPLY_TO_STATUS_ID)
                && arguments.containsKey(ARGS_KEY_REPLY_TO_ACCOUNT_NAME)
                && arguments.getString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME) != null) {
            binding.replyTo.text = "reply: ${arguments.getString(ARGS_KEY_REPLY_TO_ACCOUNT_NAME)}"
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
        }
    }

    fun postToot(body: String) {
        if (TextUtils.isEmpty(body)) {
            Snackbar.make(binding.root, R.string.error_empty_toot, Snackbar.LENGTH_SHORT)
            return
        }
        MastodonClient(Common.resetAuthInfo() ?: return)
                .postNewToot(
                        body,
                        if (binding.replyTo.visibility == View.VISIBLE) arguments.getLong(ARGS_KEY_REPLY_TO_STATUS_ID) else null,
                        if (mediaIds.size > 0) mediaIds else null)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe( { onPostSuccess() }, Throwable::printStackTrace)
    }

    fun pickMedia() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/* video/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, REQUEST_CODE_PICK_MEDIA)
    }

    fun bindMedia(data: Intent) {
        Common.resetAuthInfo()?.let { domain ->
            if (data.clipData != null) {
                getMediaPathsFromClipDataAsObservable(data.clipData)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({ (path, uri) ->
                            if (++postMediaCount < 5) {
                                postMedia(domain, path, uri)
                            } else {
                                Snackbar.make(binding.root, R.string.error_too_many_media, Snackbar.LENGTH_SHORT).show()
                            }
                        }, Throwable::printStackTrace)
            }
            if (data.data != null && ++postMediaCount < 5) {
                getMediaPathFromUriAsSingle(data.data)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe({
                            if (++postMediaCount < 5) {
                                postMedia(domain, it, data.data)
                            } else {
                                Snackbar.make(binding.root, R.string.error_too_many_media, Snackbar.LENGTH_SHORT).show()
                            }
                        }, Throwable::printStackTrace)
            }
        }
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
                            postMediaCount--
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
                    val mediaViews: ArrayList<ImageView> = ArrayList()
                    mediaViews.add(binding.media1)
                    mediaViews.add(binding.media2)
                    mediaViews.add(binding.media3)
                    mediaViews.add(binding.media4)

                    mediaViews.filter { it.drawable == null }.firstOrNull()?.setImageBitmap(it)
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

                    val path = cursor.getString(cursor.getColumnIndexOrThrow(projection))
                    cursor.close()

                    path
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
                    cursor.close()

                    Pair(path, it.second)
                }
    }

    fun onPostSuccess() {
        (activity as MainActivity).supportFragmentManager.popBackStack()
    }
}