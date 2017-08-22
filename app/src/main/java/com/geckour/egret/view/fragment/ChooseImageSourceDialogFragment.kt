package com.geckour.egret.view.fragment

import android.app.Dialog
import android.os.Bundle
import android.support.v7.app.AlertDialog
import com.geckour.egret.R
import com.trello.rxlifecycle2.components.support.RxDialogFragment

class ChooseImageSourceDialogFragment: RxDialogFragment() {

    companion object {
        val TAG: String = this::class.java.simpleName
        private val ARGS_KEY_UPLOAD_TYPE = "argsKeyUploadType"

        fun newInstance(type: AccountProfileFragment.UploadType): ChooseImageSourceDialogFragment = ChooseImageSourceDialogFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_UPLOAD_TYPE, type)
            }
        }
    }

    private val uploadType: AccountProfileFragment.UploadType by lazy { arguments[ARGS_KEY_UPLOAD_TYPE] as AccountProfileFragment.UploadType }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_title_choose_source)
                .setMessage(R.string.dialog_message_choose_source)
                .setPositiveButton(R.string.dialog_button_pick_media, { _, _ ->
                    parentFragment?.let { (it as? AccountProfileFragment)?.pickMedia(uploadType) } ?: targetFragment?.let { (it as? AccountProfileFragment)?.pickMedia(uploadType) }
                })
                .setNegativeButton(R.string.dialog_button_capture_image, { _, _ ->
                    parentFragment?.let { (it as? AccountProfileFragment)?.captureImage(uploadType) } ?: targetFragment?.let { (it as? AccountProfileFragment)?.captureImage(uploadType) }
                })
                .setNeutralButton(R.string.dialog_button_dismiss, { _, _ -> dismiss()})
                .create()
    }
}