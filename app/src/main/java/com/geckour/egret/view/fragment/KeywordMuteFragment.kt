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
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.MuteKeywordAdapter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class KeywordMuteFragment: BaseFragment() {

    lateinit private var binding: FragmentMuteKeywordBinding
    lateinit private var adapter: MuteKeywordAdapter
    private val preItems: ArrayList<MuteKeyword> = ArrayList()

    companion object {
        val TAG = "KeywordMuteFragment"
        val ARGS_KEY_DEFAULT_KEYWORD = "defaultKeyword"

        fun newInstance(defaultKeyword: String? = null): KeywordMuteFragment {
            val fragment = KeywordMuteFragment()
            val args = Bundle()
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

        binding.editTextAddMuteKeyword.requestFocusFromTouch()
        binding.editTextAddMuteKeyword.requestFocus()
        val keyword = binding.editTextAddMuteKeyword.text.toString()
        binding.editTextAddMuteKeyword.setSelection(keyword.length)
        adapter = MuteKeywordAdapter()
        val helper = Common.getSwipeToDismissTouchHelperForMuteKeyword(adapter)
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

        manageKeywords()
    }

    fun bindSavedKeywords() {
        adapter.clearItems()
        preItems.clear()
        val items = OrmaProvider.db.selectFromMuteKeyword().toList()
        adapter.addAllItems(items)
        preItems.addAll(items)
    }

    fun addKeyword(isRegex: Boolean, keyword: String) {
        if (TextUtils.isEmpty(keyword)) return

        adapter.addItem(MuteKeyword(isRegex = isRegex, keyword = keyword))
        binding.editTextAddMuteKeyword.setText("")
    }

    fun manageKeywords() {
        val items = adapter.getItems()

        removeKeywords(items)
                .subscribeOn(Schedulers.newThread())
                .compose(bindToLifecycle())
                .subscribe({}, Throwable::printStackTrace, { registerKeywords(items) })
    }

    fun removeKeywords(items: List<MuteKeyword>): Observable<Int> {
        val shouldRemoveItems = preItems.filter { items.none { item -> it.id == item.id } }
        return Observable.fromIterable(shouldRemoveItems)
                .map { OrmaProvider.db.deleteFromMuteKeyword().idEq(it.id).execute() }
    }

    fun registerKeywords(items: List<MuteKeyword>) {
        Observable.fromIterable(items)
                .map { OrmaProvider.db.relationOfMuteKeyword().upsert(it) }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ Timber.d("updated mute keyword: ${it.keyword}") }, Throwable::printStackTrace)
    }
}