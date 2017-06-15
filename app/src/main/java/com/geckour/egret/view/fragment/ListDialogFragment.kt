package com.geckour.egret.view.fragment

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AlertDialog
import com.geckour.egret.R
import com.geckour.egret.databinding.FragmentListDialogBinding
import com.geckour.egret.view.adapter.ListDialogAdapter
import com.trello.rxlifecycle2.components.support.RxDialogFragment

class ListDialogFragment(val listener: OnItemClickListener? = null): RxDialogFragment() {

    lateinit var binding: FragmentListDialogBinding

    companion object {
        val TAG: String = this::class.java.simpleName
        val ARGS_KEY_TITLE = "title"
        val ARGS_KEY_RES_IDS = "itemResIds"
        val ARGS_KEY_STRINGS = "itemStrings"

        fun newInstance(title: String, items: List<Pair<Int, String>>, listener: OnItemClickListener): ListDialogFragment {
            val fragment = ListDialogFragment(listener)
            val args = Bundle()
            args.putString(ARGS_KEY_TITLE, title)
            args.putIntArray(ARGS_KEY_RES_IDS, items.map { it.first }.toIntArray())
            args.putStringArray(ARGS_KEY_STRINGS, items.map { it.second }.toTypedArray())
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        binding = DataBindingUtil.inflate(activity.layoutInflater, R.layout.fragment_list_dialog, null, false)
        binding.setTitle(arguments.getString(ARGS_KEY_TITLE))
        binding.list.adapter = ListDialogAdapter(
                activity,
                R.layout.item_list_dialog,
                arguments.getIntArray(ARGS_KEY_RES_IDS).mapIndexed {
                    index, i -> Pair(i, arguments.getStringArray(ARGS_KEY_STRINGS)[index])
                },
                object: ListDialogAdapter.OnItemClickListener {
                    override fun onClick(resId: Int) {
                        listener?.onClick(resId)
                        dismiss()
                    }
                })
        builder.setView(binding.root)

        return builder.create()
    }

    interface OnItemClickListener {
        fun onClick(resId: Int)
    }
}