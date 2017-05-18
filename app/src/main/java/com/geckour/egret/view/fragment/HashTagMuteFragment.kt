package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
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
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class HashTagMuteFragment: BaseFragment() {

    lateinit private var binding: FragmentMuteHashTagBinding
    lateinit private var adapter: MuteHashTagAdapter
    private val preItems: ArrayList<MuteHashTag> = ArrayList()
    private val argItems: ArrayList<String> = ArrayList()

    companion object {
        val TAG = "KeywordMuteFragment"
        val ARGS_KEY_DEFAULT_HASH_TAG = "defaultHashTag"

        fun newInstance(defaultHashTags: List<String> = ArrayList()): HashTagMuteFragment {
            val fragment = HashTagMuteFragment()
            val args = Bundle()
            if (defaultHashTags.isNotEmpty()) args.putString(ARGS_KEY_DEFAULT_HASH_TAG, Gson().toJson(defaultHashTags))
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
                    argItems.addAll(tags)
                    if (tags.size == 1) tags.first() else ""
                } else ""
        binding.buttonAdd.setOnClickListener {
            val hashTag = binding.editTextAddMuteHashTag.text.toString()
            addHashTag(hashTag)
        }

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editTextAddMuteHashTag.requestFocusFromTouch()
        binding.editTextAddMuteHashTag.requestFocus()
        val hashTag = binding.editTextAddMuteHashTag.text.toString()
        binding.editTextAddMuteHashTag.setSelection(hashTag.length)
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
    }

    fun bindSavedKeywords() {
        adapter.clearItems()
        preItems.clear()
        val items = OrmaProvider.db.selectFromMuteHashTag().toList()
        adapter.addAllItems(items)
        preItems.addAll(items)
        bindArgItems()
    }

    fun bindArgItems() {
        if (argItems.size > 1) adapter.addAllItems(argItems.map { MuteHashTag(hashTag = it) })
    }

    fun addHashTag(hashTag: String) {
        if (TextUtils.isEmpty(hashTag)) return

        adapter.addItem(MuteHashTag(hashTag = hashTag))
        binding.editTextAddMuteHashTag.setText("")
    }

    fun manageHashTags() {
        val items = adapter.getItems()

        removeHashTags(items)
                .subscribeOn(Schedulers.newThread())
                .subscribe({ registerHashTags(items) }, Throwable::printStackTrace)
    }

    fun removeHashTags(items: List<MuteHashTag>): Single<Int> {
        val shouldRemoveItems = preItems.filter { items.none { item -> it.id == item.id } }
        var where = "(`id` = ?)"
        for (i in 1..shouldRemoveItems.lastIndex) where += " OR (`id` = ?)"

        return OrmaProvider.db.deleteFromMuteHashTag().where(where, *shouldRemoveItems.map { it.id }.toTypedArray()).executeAsSingle()
    }

    fun registerHashTags(items: List<MuteHashTag>) {
        Observable.fromIterable(items)
                .subscribeOn(Schedulers.newThread())
                .map { OrmaProvider.db.relationOfMuteHashTag().upsert(it) }
                .subscribe({ Timber.d("updated mute hashTag: ${it.hashTag}") }, Throwable::printStackTrace)
    }
}