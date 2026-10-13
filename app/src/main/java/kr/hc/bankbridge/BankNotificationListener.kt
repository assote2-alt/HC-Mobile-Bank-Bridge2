package kr.hc.bankbridge

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

class BankNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Settings(this).acceptsPackage(sbn.packageName)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        val text = listOf(title, body).filter { it.isNotEmpty() }.joinToString("\n")
        executor.execute {
            Bridge.capture(applicationContext, "NOTIFICATION", sbn.packageName, text, sbn.postTime,
                "push:" + sbn.key + ":" + sbn.postTime + ":" + text)
        }
    }
    override fun onListenerConnected() { Bridge.schedule(this) }
    companion object { private val executor = Executors.newSingleThreadExecutor() }
}
