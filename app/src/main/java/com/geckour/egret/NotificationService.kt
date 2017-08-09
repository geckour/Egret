package com.geckour.egret

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.geckour.egret.api.MastodonClient
import com.geckour.egret.api.model.Notification
import com.geckour.egret.api.service.MastodonService
import com.geckour.egret.util.Common
import com.geckour.egret.view.activity.MainActivity
import com.geckour.egret.view.fragment.TimelineFragment
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class NotificationService: Service() {

    private var notificationStream: Disposable? = null
    private var waitingNotification: Boolean = false
    private val notifications: ArrayList<Notification> = ArrayList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        startNotificationStream()
    }

    private fun startNotificationStream() {
        Common.resetAuthInfo()?.let {
            notificationStream = MastodonClient(it).getNotificationTimelineAsStream()
                    .subscribeOn(Schedulers.newThread())
                    .flatMap { MastodonService.events(it.source()) }
                    .subscribe({
                        parseNotificationStream(it)
                    }, Throwable::printStackTrace)
        }
    }

    private fun parseNotificationStream(source: String) {
        if (source.startsWith("data: ")) {
            val data = source.replace(Regex("^data:\\s(.+)"), "$1")
            if (waitingNotification) {
                val notificationItem = App.gson.fromJson(data, Notification::class.java)
                notifications.add(notificationItem)

                val drawable = Glide.with(applicationContext).load(notificationItem.account.avatarUrl).submit().get()
                val bitmap = if (drawable is GifDrawable) drawable.firstFrame else (drawable as BitmapDrawable).bitmap
                val notification = NotificationCompat.Builder(this)
                        .apply {
                            setSmallIcon(R.mipmap.ic_launcher)
                            setLargeIcon(bitmap)
                            setContentTitle("${notificationItem.type} by ${notificationItem.account.displayName}")
                            notificationItem.status?.let { setContentText(Common.getSpannedWithoutExtraMarginFromHtml(it.content)) }

                            val intent = MainActivity.getIntent(applicationContext, TimelineFragment.Category.Notification)
                            setContentIntent(PendingIntent.getActivity(applicationContext, MainActivity.REQUEST_CODE_NOTIFICATION, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                        }
                        .build()
                        .apply {
                            flags = android.app.Notification.FLAG_AUTO_CANCEL
                        }
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notificationItem.id.toInt(), notification)
            }
        } else {
            waitingNotification = source == "event: notification"
        }
    }
}