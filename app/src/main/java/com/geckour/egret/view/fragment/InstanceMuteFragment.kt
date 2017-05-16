package com.geckour.egret.view.fragment

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.egret.R
import com.geckour.egret.databinding.FragmentMuteInstanceBinding
import com.geckour.egret.model.MuteInstance
import com.geckour.egret.util.Common
import com.geckour.egret.util.OrmaProvider
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.adapter.MuteInstanceAdapter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class InstanceMuteFragment: BaseFragment() {

    lateinit private var binding: FragmentMuteInstanceBinding
    lateinit private var adapter: MuteInstanceAdapter
    private val preItems: ArrayList<MuteInstance> = ArrayList()

    companion object {
        val TAG = "KeywordMuteFragment"
        val ARGS_KEY_DEFAULT_INSTANCE = "defaultInstance"

        fun newInstance(defaultInstance: MuteInstance? = null): InstanceMuteFragment {
            val fragment = InstanceMuteFragment()
            val args = Bundle()
            if (defaultInstance != null) args.putString(ARGS_KEY_DEFAULT_INSTANCE, defaultInstance.instance)
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_mute_instance, container, false)
        binding.defaultInstance =
                if (arguments.containsKey(ARGS_KEY_DEFAULT_INSTANCE)) {
                    arguments.getString(ARGS_KEY_DEFAULT_INSTANCE, "")
                } else ""
        binding.buttonAdd.setOnClickListener {
            val instance = binding.editTextAddMuteInstance.text.toString()
            addInstance(instance)
        }

        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editTextAddMuteInstance.requestFocusFromTouch()
        binding.editTextAddMuteInstance.requestFocus()
        val instance = binding.editTextAddMuteInstance.text.toString()
        binding.editTextAddMuteInstance.setSelection(instance.length)
        adapter = MuteInstanceAdapter()
        val helper = Common.getSwipeToDismissTouchHelperForMuteInstance(adapter)
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

        manageInstances()
    }

    fun bindSavedKeywords() {
        adapter.clearItems()
        preItems.clear()
        val items = OrmaProvider.db.selectFromMuteInstance().toList()
        adapter.addAllItems(items)
        preItems.addAll(items)
    }

    fun addInstance(instance: String) {
        if (TextUtils.isEmpty(instance)) return

        adapter.addItem(MuteInstance(instance = instance))
        binding.editTextAddMuteInstance.setText("")
    }

    fun manageInstances() {
        val items = adapter.getItems()

        removeInstances(items)
                .subscribeOn(Schedulers.newThread())
                .compose(bindToLifecycle())
                .subscribe({}, Throwable::printStackTrace, { registerInstances(items) })
    }

    fun removeInstances(items: List<MuteInstance>): Observable<Int> {
        val shouldRemoveItems = preItems.filter { items.none { item -> it.id == item.id } }
        return Observable.fromIterable(shouldRemoveItems)
                .map { OrmaProvider.db.deleteFromMuteInstance().idEq(it.id).execute() }
    }

    fun registerInstances(items: List<MuteInstance>) {
        Observable.fromIterable(items)
                .map { OrmaProvider.db.relationOfMuteInstance().upsert(it) }
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ Timber.d("updated mute instance: ${it.instance}") }, Throwable::printStackTrace)
    }
}