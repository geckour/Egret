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

        fun newInstance(defaultKeyword: String): KeywordMuteFragment {
            val fragment = KeywordMuteFragment()
            val args = Bundle()
            args.putString(ARGS_KEY_DEFAULT_KEYWORD, defaultKeyword)
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_mute_keyword, container, false)
        binding.defaultKeyword = arguments.getString(ARGS_KEY_DEFAULT_KEYWORD, "")
        binding.buttonAdd.setOnClickListener {
            val isRegex = binding.checkIsRegex.isChecked
            val keyword = binding.editTextAddMuteKeyword.text.toString()
            addKeyword(isRegex, keyword)
        }

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editTextAddMuteKeyword.requestFocusFromTouch()
        binding.editTextAddMuteKeyword.requestFocus()
        val keyword = binding.editTextAddMuteKeyword.text.toString()
        binding.editTextAddMuteKeyword.setSelection(keyword.length)
        adapter = MuteKeywordAdapter()
        binding.recyclerView.adapter = adapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        if (activity is MainActivity) ((activity as MainActivity).findViewById(R.id.fab) as FloatingActionButton?)?.hide()
    }

    fun addKeyword(isRegex: Boolean, keyword: String) {
        if (TextUtils.isEmpty(keyword)) return

        adapter.addItem(MuteKeyword(isRegex = isRegex, keyword = keyword))
    }

    fun registerKeywords() {
        val items = adapter.getItems()

        Observable.fromIterable(items)
                .map { OrmaProvider.db.insertIntoMuteKeyword(it) }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .subscribe({ nRow -> Timber.d("updated mute keywords count: $nRow") }, { throwable ->
                    throwable.printStackTrace()
                    Snackbar.make(binding.root, "Failed to add keyword.", Snackbar.LENGTH_SHORT)
                }, {
                    Snackbar.make(binding.root, "Added mute keywords.", Snackbar.LENGTH_SHORT)
                })
    }
}