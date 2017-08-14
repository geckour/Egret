package com.geckour.egret

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DeleteNotificationReceiver: BroadcastReceiver() {

    companion object {
        const val REQUEST_CODE_DELETE_NOTIFICATION = 0
        const val ACTION_DELETE_NOTIFICATION = "deleteNotificationEgret"
        const val ARGS_KEY_NOTIFICATION_ID = "argsKeyNotificationIdEgret"

        fun getIntent(context: Context, notificationId: Long): Intent = Intent(context, DeleteNotificationReceiver::class.java)
                .apply {
                    putExtra(ARGS_KEY_NOTIFICATION_ID, notificationId)
                }
    }

    var app: App? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        if (ACTION_DELETE_NOTIFICATION == intent?.action) {
            app?.let {
                val notificationId =
                        if (intent.hasExtra(ARGS_KEY_NOTIFICATION_ID)) intent.getLongExtra(ARGS_KEY_NOTIFICATION_ID, -1)
                        else return
                it.getSystemService(NotificationService::class.java)
                        ?.removeStandardNotification(notificationId)
            }
        }
    }
}