package kr.hc.bankbridge

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URI

class SendWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = synchronized(sendLock) {
        Store(applicationContext).use { store ->
            val settings = Settings(applicationContext)
            val endpoint = settings.get("url")
            if (endpoint.isBlank()) return@synchronized Result.success()
            while (!isStopped) {
                val event = store.first() ?: return@synchronized Result.success()
                var connection: HttpURLConnection? = null
                try {
                    val uri = URI(endpoint)
                    require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) { "서버 URL 형식 오류" }
                    connection = uri.toURL().openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000; connection.readTimeout = 15000
                    connection.instanceFollowRedirects = false
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Idempotency-Key", event.first)
                    val token = settings.get("token")
                    if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer " + token)
                    connection.outputStream.use { it.write(event.second.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        store.log("전송 실패 HTTP " + code + " · 자동 재시도")
                        return@synchronized Result.retry()
                    }
                    store.sent(event.first)
                } catch (e: Exception) {
                    store.log("연결 실패 (" + e.javaClass.simpleName + ") · 자동 재시도")
                    return@synchronized Result.retry()
                } finally { connection?.disconnect() }
            }
            Result.retry()
        }
    }
    companion object { private val sendLock = Any() }
}
