package kr.hc.bankbridge

import android.content.Context
import androidx.work.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object Bridge {
    private val lock = Any()
    fun capture(context: Context, source: String, sender: String, text: String, receivedAt: Long, dedup: String, forcedType: String? = null) {
        val settings = Settings(context)
        val id = UUID.randomUUID().toString()
        val payload = JSONObject().apply {
            put("eventId", id)
            put("type", forcedType ?: Parser.classify(text, settings.list("deposit"), settings.list("withdrawal"), settings.list("alert")))
            put("source", source); put("sender", sender); put("message", text)
            put("amount", Parser.amount(text) ?: JSONObject.NULL)
            put("receivedAt", java.time.Instant.ofEpochMilli(receivedAt).toString())
            if (source == "NOTIFICATION") put("packageName", sender)
        }.toString()
        synchronized(lock) {
            Store(context).use { it.enqueue(id, payload, dedup) }
        }
        schedule(context)
    }
    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniqueWork("send-events", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SendWorker>().setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        manager.enqueueUniquePeriodicWork("retry-events", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SendWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
    }
}
