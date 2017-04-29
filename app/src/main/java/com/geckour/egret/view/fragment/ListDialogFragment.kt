package com.geckour.egret.view.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import com.trello.rxlifecycle2.components.support.RxDialogFragment

class ListDialogFragment(var onClickListener: DialogInterface.OnClickListener? = null): RxDialogFragment() {

    companion object {
        val TAG = "listDialogFragment"
        val ARGS_KEY_TITLE = "title"
        val ARGS_KEY_RES_ID = "itemsResId"
        val ARGS_KEY_STRINGS = "itemStrings"

        fun newInstance(title: String, itemsResId: Int, onClickListener: DialogInterface.OnClickListener): ListDialogFragment {
            val fragment = ListDialogFragment(onClickListener)
            val args = Bundle()
            args.putString(ARGS_KEY_TITLE, title)
            args.putInt(ARGS_KEY_RES_ID, itemsResId)
            fragment.arguments = args

            return fragment
        }

        fun newInstance(title: String, itemStrings: List<String>, onClickListener: DialogInterface.OnClickListener): ListDialogFragment {
            val fragment = ListDialogFragment(onClickListener)
            val args = Bundle()
            args.putString(ARGS_KEY_TITLE, title)
            args.putStringArray(ARGS_KEY_STRINGS, itemStrings.toTypedArray())
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        if (arguments.containsKey(ARGS_KEY_RES_ID)) {
            builder.setTitle(arguments.getString(ARGS_KEY_TITLE))
                .setItems(arguments.getInt(ARGS_KEY_RES_ID), onClickListener)
        } else {
            builder.setTitle(arguments.getString(ARGS_KEY_TITLE))
                    .setItems(arguments.getStringArray(ARGS_KEY_STRINGS), onClickListener)
        }

        return builder.create()
    }
}