package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.databinding.FragmentMuteHashTagBinding
import com.geckour.egret.model.MuteHashTag
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.MuteHashTagAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class HashTagMuteFragment: BaseFragment() {
    lateinit private var binding: FragmentMuteHashTagBinding
    lateinit private var adapter: MuteHashTagAdapter
    private val preItems: ArrayList<MuteHashTag> = ArrayList()

    companion object {
        val TAG = "KeywordMuteFragment"
        val ARGS_KEY_DEFAULT_HASH_TAG = "defaultHashTag"

        fun newInstance(defaultHashTags: List<MuteHashTag> = ArrayList()): HashTagMuteFragment {
            val fragment = HashTagMuteFragment()
            val args = Bundle()
            if (defaultHashTags.isNotEmpty()) args.putString(ARGS_KEY_DEFAULT_HASH_TAG, Gson().toJson(defaultHashTags.map { it.hashTag }))
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_mute_hash_tag, container, false)
        binding.defaultHashTag =
                if (arguments.containsKey(ARGS_KEY_DEFAULT_HASH_TAG)) {
                    val tagsJson = arguments.getString(ARGS_KEY_DEFAULT_HASH_TAG, "")
                    val type = object: TypeToken<List<String>>() {}.type
                    val tags: List<String> = Gson().fromJson(tagsJson, type)
                    if (tags.isNotEmpty()) tags.first() else ""
                } else ""
        binding.buttonAdd.setOnClickListener {
            val hashTag = binding.editTextAddMuteKeyword.text.toString()
            addHashTag(hashTag)
        }

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editTextAddMuteKeyword.requestFocusFromTouch()
        binding.editTextAddMuteKeyword.requestFocus()
        val keyword = binding.editTextAddMuteKeyword.text.toString()
        binding.editTextAddMuteKeyword.setSelection(keyword.length)
        adapter = MuteHashTagAdapter()
        val helper = Common.getSwipeToDismissTouchHelperForMuteHashTag(adapter)
        helper.attachToRecyclerView(binding.recyclerView)
        binding.recyclerView.addItemDecoration(helper)
        binding.recyclerView.adapter = adapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        bindSavedKeywords()
    }

    override fun onResume() {
        super.onResume()

        if (activity is MainActivity) ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.hide()
    }

    override fun onPause() {
        super.onPause()

        manageHashTags()
                .subscribe(
                        { Timber.d("updated mute hashTag: ${it.hashTag}") },
                        { throwable ->
                            throwable.printStackTrace()
                            Snackbar.make(binding.root, "Failed to register mute hash tags.", Snackbar.LENGTH_SHORT)
                        },
                        { Snackbar.make(binding.root, "Registered mute hash tags.", Snackbar.LENGTH_SHORT) })
    }

    fun bindSavedKeywords() {
        adapter.clearItems()
        preItems.clear()
        val items = OrmaProvider.db.selectFromMuteHashTag().toList()
        adapter.addAllItems(items)
        preItems.addAll(items)
    }

    fun addHashTag(hashTag: String) {
        if (TextUtils.isEmpty(hashTag)) return

        adapter.addItem(MuteHashTag(hashTag = hashTag))
        binding.editTextAddMuteKeyword.setText("")
    }

    fun manageHashTags(): Observable<MuteHashTag> {
        val items = adapter.getItems()
        return removeHashTags(items)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .concatMap { registerHashTags(items) }
                .compose(bindToLifecycle())
    }

    fun removeHashTags(items: List<MuteHashTag>): Observable<Int> {
        return Observable.fromIterable(preItems.filter { items.none { item -> it.id == item.id } })
                .map { OrmaProvider.db.deleteFromMuteHashTag().idEq(it.id).execute() }
    }

    fun registerHashTags(items: List<MuteHashTag>): Observable<MuteHashTag> {
        return Observable.fromIterable(items)
                .map { OrmaProvider.db.relationOfMuteHashTag().upsert(it) }
    }
}