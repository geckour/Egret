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
import com.geckour.egret.databinding.FragmentMuteKeywordBinding
import com.geckour.egret.model.MuteKeyword
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.MuteKeywordAdapter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class KeywordMuteFragment: BaseFragment() {

    lateinit var binding: FragmentMuteKeywordBinding
    lateinit var adapter: MuteKeywordAdapter

    companion object {
        val TAG = "KeywordMuteFragment"
        val ARGS_KEY_DEFAULT_KEYWORD = "defaultKeyword"
        val ARGS_KEY_MODE = "mode"
        val ARGS_VALUE_MODE_ADD = "modeAdd"
        val ARGS_VALUE_MODE_MANAGE = "modeManage"

        fun newInstance(mode: String, defaultKeyword: String? = null): KeywordMuteFragment {
            val fragment = KeywordMuteFragment()
            val args = Bundle()
            args.putString(ARGS_KEY_MODE, mode)
            defaultKeyword?.let {
                args.putString(ARGS_KEY_DEFAULT_KEYWORD, it)
            }
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_mute_keyword, container, false)
        binding.defaultKeyword =
                if (arguments.containsKey(ARGS_KEY_DEFAULT_KEYWORD)) arguments.getString(ARGS_KEY_DEFAULT_KEYWORD, "")
                else ""
        binding.buttonAdd.setOnClickListener {
            val isRegex = binding.checkIsRegex.isChecked
            val keyword = binding.editTextAddMuteKeyword.text.toString()
            addKeyword(isRegex, keyword)
        }

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (arguments.containsKey(ARGS_KEY_MODE)) {
            val mode = arguments.getString(ARGS_KEY_MODE, "")
            if (mode == ARGS_VALUE_MODE_ADD)
                binding.desc.setText(R.string.desc_fragment_mute_keyword)
            if (mode == ARGS_VALUE_MODE_MANAGE)
                binding.desc.setText(R.string.desc_fragment_manage_mute_keyword)
        }
        binding.editTextAddMuteKeyword.requestFocusFromTouch()
        binding.editTextAddMuteKeyword.requestFocus()
        val keyword = binding.editTextAddMuteKeyword.text.toString()
        binding.editTextAddMuteKeyword.setSelection(keyword.length)
        binding.buttonRegister.setOnClickListener { registerKeywords() }
        adapter = MuteKeywordAdapter()
        binding.recyclerView.adapter = adapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (arguments.containsKey(ARGS_KEY_MODE)) {
            if (getMode() == ARGS_VALUE_MODE_MANAGE) bindSavedKeywords()
        }
    }

    override fun onResume() {
        super.onResume()

        if (activity is MainActivity) ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.hide()
    }

    fun bindSavedKeywords() {
        val items = OrmaProvider.db.selectFromMuteKeyword().toList()
        adapter.addAllItems(items)
    }

    fun addKeyword(isRegex: Boolean, keyword: String) {
        if (TextUtils.isEmpty(keyword)) return

        adapter.addItem(MuteKeyword(isRegex = isRegex, keyword = keyword))
        binding.editTextAddMuteKeyword.setText("")
    }

    fun registerKeywords() {
        val items = adapter.getItems()

        Observable.fromIterable(items)
                .map { OrmaProvider.db.relationOfMuteKeyword().upsert(it) }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe(
                        { item -> Timber.d("updated mute keyword: ${item.keyword}") },
                        { throwable ->
                            throwable.printStackTrace()
                            Snackbar.make(binding.root, "Failed to add keyword.", Snackbar.LENGTH_SHORT)
                        },
                        {
                            Snackbar.make(binding.root, "Added mute keywords.", Snackbar.LENGTH_SHORT)
                            val mode = getMode()
                            if (mode == ARGS_VALUE_MODE_ADD) adapter.clearItems()
                            if (mode == ARGS_VALUE_MODE_MANAGE) {
                                val newItems = OrmaProvider.db.selectFromMuteKeyword().toList()
                                adapter.resetItems(newItems)
                            }
                        })
    }

    fun getMode(): String {
        return if (arguments.containsKey(ARGS_KEY_MODE)) arguments.getString(ARGS_KEY_MODE, "") else ""
    }
}