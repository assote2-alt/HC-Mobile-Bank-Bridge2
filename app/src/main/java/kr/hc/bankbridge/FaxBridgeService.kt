package kr.hc.bankbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings as AndroidSettings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

class FaxBridgeService : Service() {
    private val running = AtomicBoolean(false)
    private val worker = Thread { loop() }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(FOREGROUND_ID, ongoingNotification())
        if (running.compareAndSet(false, true)) worker.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running.set(false)
        worker.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loop() {
        while (running.get()) {
            try {
                val settings = Settings(this)
                if (!settings.faxEnabled) {
                    stopSelf()
                    return
                }
                poll(settings)
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                // 다음 주기에 다시 시도한다. 브릿지는 사용자 업무를 막으면 안 된다.
            }
            try { Thread.sleep(POLL_MS) } catch (_: InterruptedException) { return }
        }
    }

    private fun poll(settings: Settings) {
        val base = settings.get("fax_server").trimEnd('/')
        val token = settings.get("fax_token").trim()
        if (base.isBlank() || token.isBlank()) return
        val deviceId = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ANDROID_ID) ?: "hc-phone"
        val url = URI("$base/api/mobile/fax/next?device_id=${java.net.URLEncoder.encode(deviceId, "UTF-8")}").toURL()
        val con = url.openConnection() as HttpURLConnection
        try {
            con.requestMethod = "GET"
            con.connectTimeout = 7000
            con.readTimeout = 7000
            con.instanceFollowRedirects = false
            con.setRequestProperty("Authorization", "Bearer $token")
            val code = con.responseCode
            if (code == 204) return
            if (code !in 200..299) return
            val body = con.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val job = root.optJSONObject("job") ?: return
            val jobId = job.optString("id")
            if (jobId.isBlank()) return
            val prefs = getSharedPreferences("fax_bridge", Context.MODE_PRIVATE)
            if (prefs.getString("last_notified", "") == jobId) return
            val pdfUrl = job.optString("pdf_url")
            val file = downloadPdf(base, token, jobId, pdfUrl)
            notifyJob(job, file, base, token, deviceId)
            prefs.edit().putString("last_notified", jobId).apply()
        } finally {
            con.disconnect()
        }
    }

    private fun downloadPdf(base: String, token: String, jobId: String, pdfUrl: String): File {
        val full = if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) pdfUrl else base + pdfUrl
        val con = URI(full).toURL().openConnection() as HttpURLConnection
        try {
            con.requestMethod = "GET"
            con.connectTimeout = 10000
            con.readTimeout = 15000
            con.instanceFollowRedirects = false
            con.setRequestProperty("Authorization", "Bearer $token")
            if (con.responseCode !in 200..299) throw IllegalStateException("PDF HTTP ${con.responseCode}")
            val dir = File(cacheDir, "fax").apply { mkdirs() }
            val file = File(dir, "$jobId.pdf")
            con.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            return file
        } finally {
            con.disconnect()
        }
    }

    private fun notifyJob(job: JSONObject, file: File, base: String, token: String, deviceId: String) {
        val jobId = job.optString("id")
        val fax = job.optString("fax_number")
        val customer = job.optString("customer")
        val saleNo = job.optString("sale_no")
        val intent = Intent(this, FaxShareActivity::class.java).apply {
            putExtra("job_id", jobId)
            putExtra("fax_number", fax)
            putExtra("customer", customer)
            putExtra("sale_no", saleNo)
            putExtra("file_path", file.absolutePath)
            putExtra("server", base)
            putExtra("token", token)
            putExtra("device_id", deviceId)
        }
        val pending = PendingIntent.getActivity(
            this, jobId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = getSystemService(NotificationManager::class.java)
        val n = android.app.Notification.Builder(this, JOB_CHANNEL)
            .setSmallIcon(R.drawable.ic_bridge)
            .setContentTitle("팩스 전송 요청 · $customer")
            .setContentText("$fax · $saleNo · 눌러서 모바일팩스로 보내기")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(jobId.hashCode(), n)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SERVICE_CHANNEL, "HC 팩스 브릿지", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(JOB_CHANNEL, "HC 팩스 요청", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun ongoingNotification(): android.app.Notification =
        android.app.Notification.Builder(this, SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_bridge)
            .setContentTitle("HC 모바일 팩스 브릿지 실행 중")
            .setContentText("ERP의 거래명세서 팩스 요청을 기다리는 중입니다.")
            .setOngoing(true)
            .build()

    companion object {
        private const val FOREGROUND_ID = 7010
        private const val SERVICE_CHANNEL = "hc_fax_service"
        private const val JOB_CHANNEL = "hc_fax_jobs"
        private const val POLL_MS = 10_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FaxBridgeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FaxBridgeService::class.java))
        }
    }
}
