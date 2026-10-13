package kr.hc.bankbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val pending = goAsync()
        executor.execute {
            try {
                messages.groupBy { it.originatingAddress ?: "" }.forEach { (sender, parts) ->
                    if (sender.isNotBlank() && Settings(context).acceptsSms(sender)) {
                        val body = parts.joinToString("") { it.messageBody ?: "" }
                        val time = parts.first().timestampMillis
                        Bridge.capture(context, "SMS", sender, body, time, "sms:" + sender + ":" + time + ":" + body)
                    }
                }
            } finally { pending.finish() }
        }
    }
    companion object { private val executor = Executors.newSingleThreadExecutor() }
}
