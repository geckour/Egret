package com.geckour.egret

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.IBinder
import android.os.Vibrator
import android.preference.PreferenceManager
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

    companion object {
        const val PUBLISH_ID_STANDARD = 0
        const val GROUP_KEY_STANDARD = "standard"

        fun getIntent(context: Context): Intent = Intent(context, NotificationService::class.java)
    }

    private var notificationStream: Disposable? = null
    private var waitingNotification: Boolean = false
    private val sharedPref: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(applicationContext) }
    private var parentNotificationStandard: android.app.Notification? = null
    private val standardNotifications: ArrayList<Notification> = ArrayList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (sharedPref.getBoolean("manage_notify_real_time", false)) startNotificationStream()
    }

    override fun onDestroy() {
        super.onDestroy()

        notificationStream?.let {
            if (!it.isDisposed) it.dispose()
        }
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
                val notification = App.gson.fromJson(data, Notification::class.java)
                publishNotification(notification)
            }
        } else {
            waitingNotification = source == "event: notification"
        }
    }

    fun publishNotification(notificationItem: Notification) {
        val drawable = Glide.with(applicationContext).load(notificationItem.account.avatarUrl).submit().get()
        val bitmap = if (drawable is GifDrawable) drawable.firstFrame else (drawable as BitmapDrawable).bitmap

        addStandardNotification(notificationItem)

        parentNotificationStandard = getParentNotification()
                .apply {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(PUBLISH_ID_STANDARD, this)
                }
        getChildNotification(notificationItem, bitmap)
                .apply {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notificationItem.id.toInt(), this)
                }


        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(longArrayOf(0, 50, 50, 50), -1)
    }

    fun addStandardNotification(notification: Notification) = standardNotifications.add(notification)

    fun removeStandardNotification(notificationId: Long) = standardNotifications.removeAll { it.id == notificationId }

    fun getParentNotification(): android.app.Notification = NotificationCompat.Builder(this)
            .apply {
                setSmallIcon(R.mipmap.ic_launcher)
                standardNotifications.size.let {
                    if (it > 0) setNumber(it)
                    setStyle(NotificationCompat.BigTextStyle()
                            .apply {
                                if (it > 0) setSummaryText("There is ${standardNotifications.size} notifications")
                            })
                }
                setGroup(GROUP_KEY_STANDARD)
                setGroupSummary(true)
            }
            .build()

    fun getChildNotification(notificationItem: Notification, bitmap: Bitmap): android.app.Notification = NotificationCompat.Builder(this)
                .apply {
                    setGroup(GROUP_KEY_STANDARD)
                    setSmallIcon(R.mipmap.ic_launcher)
                    setLargeIcon(bitmap)
                    setContentTitle(createNotificationTitle(notificationItem))
                    notificationItem.status?.let { setContentText(Common.getSpannedWithoutExtraMarginFromHtml(it.content)) }

                    val contentIntent = MainActivity.getIntent(applicationContext, TimelineFragment.Category.Notification)
                    setContentIntent(PendingIntent.getActivity(applicationContext, MainActivity.REQUEST_CODE_NOTIFICATION, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                    val deleteIntent = DeleteNotificationReceiver.getIntent(applicationContext, notificationItem.id)
                    setDeleteIntent(PendingIntent.getBroadcast(applicationContext, DeleteNotificationReceiver.REQUEST_CODE_DELETE_NOTIFICATION, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                }
                .build()
                .apply {
                    flags = android.app.Notification.FLAG_AUTO_CANCEL
                }

    fun createNotificationTitle(notificationItem: Notification): String = "${notificationItem.type} by ${notificationItem.account.displayName}"
}