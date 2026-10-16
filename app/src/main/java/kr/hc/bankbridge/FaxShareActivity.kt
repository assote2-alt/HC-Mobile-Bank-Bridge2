package kr.hc.bankbridge

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

class FaxShareActivity : Activity() {
    private lateinit var jobId: String
    private lateinit var faxNumber: String
    private lateinit var filePath: String
    private lateinit var server: String
    private lateinit var token: String
    private lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jobId = intent.getStringExtra("job_id").orEmpty()
        faxNumber = intent.getStringExtra("fax_number").orEmpty()
        filePath = intent.getStringExtra("file_path").orEmpty()
        server = intent.getStringExtra("server").orEmpty().trimEnd('/')
        token = intent.getStringExtra("token").orEmpty()
        deviceId = intent.getStringExtra("device_id").orEmpty().ifBlank {
            AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ANDROID_ID) ?: "hc-phone"
        }
        val customer = intent.getStringExtra("customer").orEmpty()
        val saleNo = intent.getStringExtra("sale_no").orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 42, 34, 42)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        fun text(value: String, size: Float = 17f) = TextView(this).apply {
            this.text = value
            textSize = size
            setPadding(0, 10, 0, 10)
            root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        fun button(title: String, action: () -> Unit) = Button(this).apply {
            this.text = title
            setOnClickListener { action() }
            root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }

        text("HC 거래명세서 팩스", 24f)
        text("거래처: $customer\n거래번호: $saleNo\n팩스번호: $faxNumber")
        text("[모바일팩스로 열기]를 누르면 PDF를 첨부하고 팩스번호를 클립보드에 복사합니다. 모바일팩스 앱에서 번호 붙여넣기 후 전송하세요.", 15f)
        button("모바일팩스로 열기") { shareToMobileFax() }
        button("팩스번호 다시 복사") { copyFax(); toast("팩스번호를 복사했습니다.") }
        button("전송 완료 표시") {
            postStatus("SENT", "사용자가 모바일팩스 발송 완료를 확인함") { finish() }
        }
        button("실패 / 취소 표시") {
            AlertDialog.Builder(this)
                .setTitle("팩스 전송 실패/취소")
                .setMessage("ERP 전송내역에 실패로 표시할까요?")
                .setPositiveButton("표시") { _, _ -> postStatus("FAILED", "사용자가 실패/취소로 표시함") { finish() } }
                .setNegativeButton("아니오", null)
                .show()
        }
        setContentView(root)
        copyFax()
        postStatus("OPENED", "업무폰에서 팩스 요청 화면 열림") {}
    }

    private fun copyFax() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("팩스번호", faxNumber))
    }

    private fun shareToMobileFax() {
        val file = File(filePath)
        if (!file.exists()) {
            toast("거래명세서 PDF 파일을 찾을 수 없습니다.")
            return
        }
        copyFax()
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("거래명세서", uri)
        }
        val mobileFax = "com.dho.mobilefax"
        if (packageManager.getLaunchIntentForPackage(mobileFax) != null) {
            send.setPackage(mobileFax)
            try {
                startActivity(send)
                postStatus("SHARED", "모바일팩스 앱에 PDF 전달") {}
                toast("PDF 첨부 완료 · 팩스번호가 복사되었습니다.")
                return
            } catch (_: Exception) {
                send.setPackage(null)
            }
        }
        try {
            startActivity(Intent.createChooser(send, "거래명세서를 보낼 앱 선택"))
            postStatus("SHARED", "Android 공유화면에 PDF 전달") {}
            toast("팩스번호가 복사되었습니다. 모바일팩스를 선택하세요.")
        } catch (e: Exception) {
            toast("PDF 공유 앱을 열 수 없습니다: ${e.message}")
        }
    }

    private fun postStatus(status: String, note: String, done: () -> Unit) {
        if (server.isBlank() || token.isBlank() || jobId.isBlank()) {
            done(); return
        }
        Thread {
            try {
                val con = URI("$server/api/mobile/fax/jobs/$jobId/status").toURL().openConnection() as HttpURLConnection
                try {
                    con.requestMethod = "POST"
                    con.connectTimeout = 7000
                    con.readTimeout = 7000
                    con.doOutput = true
                    con.setRequestProperty("Authorization", "Bearer $token")
                    con.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val body = JSONObject().put("status", status).put("device_id", deviceId).put("note", note).toString()
                    con.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    con.responseCode
                } finally { con.disconnect() }
            } catch (_: Exception) {}
            runOnUiThread(done)
        }.start()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
