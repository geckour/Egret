package com.geckour.egret.view.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import com.geckour.egret.App
import com.geckour.egret.R
import com.geckour.egret.model.Draft
import com.geckour.egret.view.activity.MainActivity
import com.trello.rxlifecycle2.components.support.RxDialogFragment

class SelectDraftDialogFragment: RxDialogFragment() {

    companion object {
        val TAG: String = this::class.java.simpleName
        private val ARGS_KEY_DRAFTS = "argsKeyDrafts"

        fun newInstance(drafts: List<Draft>): SelectDraftDialogFragment = SelectDraftDialogFragment().apply {
            arguments = Bundle().apply {
                putStringArray(ARGS_KEY_DRAFTS, drafts.map { App.gson.toJson(it) }.toTypedArray())
            }
        }
    }

    interface OnSelectDraftItemListener {
        fun onSelect(draft: Draft)
    }

    private var listener: OnSelectDraftItemListener? = null
    private val drafts: List<Draft> by lazy { arguments.getStringArray(ARGS_KEY_DRAFTS).map { App.gson.fromJson(it, Draft::class.java) } }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        targetFragment?.let { listener = it as OnSelectDraftItemListener}
        parentFragment?.let { listener = it as OnSelectDraftItemListener}
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_message_select_draft)
                .setItems(drafts.map { it.body }.toTypedArray(), { dialog, which ->
                    drafts.getOrNull(which)?.let {
                        listener?.onSelect(it)
                    } ?: Snackbar.make((activity as MainActivity).binding.root, R.string.error_unable_select_draft, Snackbar.LENGTH_SHORT).show()

                    dialog.dismiss()
                })
                .setNegativeButton(R.string.dialog_button_dismiss, null)
                .create()
    }
}