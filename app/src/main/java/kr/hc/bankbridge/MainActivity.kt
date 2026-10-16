package kr.hc.bankbridge

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.widget.*
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private val inputs = linkedMapOf<String, EditText>()
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { updateStatus(); handler.postDelayed(this, 3000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 40)
        }
        scroll.addView(root); setContentView(scroll)
        @Suppress("DEPRECATION")
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        label("HC 모바일 브릿지", 25f)
        label("은행 SMS·앱 알림을 ERP로 보내고, ERP 거래명세서를 업무폰의 모바일팩스 앱으로 전달합니다.", 15f)
        status = label("", 15f)

        label("은행 알림 브릿지", 21f)
        button("자동 수집 켜기 / 끄기") {
            if (settings.collectionEnabled) {
                settings.setCollectionEnabled(false)
                toast("자동 수집을 껐습니다. 이미 저장한 대기 이벤트는 전송됩니다.")
                updateStatus()
            } else requestCollectionConsent()
        }
        button("SMS 수신 권한 허용") {
            val request = { requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1) }
            if (settings.collectionEnabled) request() else requestCollectionConsent(request)
        }
        button("알림 접근 권한 설정") {
            val request = { startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            if (settings.collectionEnabled) request() else requestCollectionConsent(request)
        }

        label("서버 연결", 21f)
        field("url", "입금알림 ERP 서버 URL", false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        field("token", "입금알림 API 토큰 (Bearer)")
        inputs["token"]?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        label("팩스 브릿지", 21f)
        label("ERP의 [설정 → 무료 모바일팩스 브릿지 → 업무폰 연결정보]에서 서버주소와 연결코드를 확인해 입력하세요.", 14f)
        field("fax_server", "팩스 브릿지 서버 주소 · 예: http://192.168.0.10:8010", false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        field("fax_token", "팩스 연결코드")
        inputs["fax_token"]?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        button("팩스 브릿지 켜기 / 끄기") {
            if (settings.faxEnabled) {
                settings.setFaxEnabled(false)
                FaxBridgeService.stop(this)
                toast("팩스 브릿지를 껐습니다.")
                updateStatus()
            } else if (save()) {
                settings.setFaxEnabled(true)
                startFaxBridgeWithPermission()
                updateStatus()
            }
        }
        button("팩스 서버 연결 테스트") { testFaxServer() }
        button("모바일팩스 앱 열기 / 설치") { openMobileFax() }

        label("수집 대상", 21f)
        field("numbers", "SMS 발신번호 · 여러 개는 줄바꿈 또는 쉼표로 구분", true)
        field("packages", "은행 앱 packageName · 줄바꿈 또는 쉼표로 구분", true)
        label("분류 키워드", 21f)
        field("deposit", "입금 키워드", true)
        field("withdrawal", "출금 키워드 · 입금보다 우선 적용", true)
        field("alert", "은행 알림 키워드", true)
        label("어떤 키워드에도 해당하지 않으면 GENERAL로 전송됩니다.", 14f)
        button("설정 저장") { save() }
        button("테스트 입금 이벤트 전송") {
            if (save()) {
                val now = System.currentTimeMillis()
                Bridge.capture(this, "TEST", "테스트", "입금 870,000원", now, UUID.randomUUID().toString(), "BANK_DEPOSIT")
                toast("테스트 이벤트를 저장했습니다. 전송 결과는 로그에서 확인하세요.")
                updateStatus()
            }
        }
        button("대기 이벤트 재전송") { if (save()) { Bridge.schedule(this); toast("재전송을 예약했습니다.") } }
        button("전송 로그 화면") { showLogs() }
        label("은행 이벤트는 최대 200건까지 기기에 저장하고 인터넷 복구 후 자동 재전송합니다. 팩스 브릿지를 켜면 업무폰이 ERP의 팩스 대기열을 확인하며, 팩스 요청 알림을 누르면 번호를 복사하고 거래명세서 PDF를 모바일팩스 앱으로 전달합니다.", 14f)
    }

    private fun label(text: String, size: Float): TextView = TextView(this).apply {
        this.text = text; textSize = size; setPadding(0, 12, 0, 12); root.addView(this)
    }

    private fun field(key: String, title: String, multi: Boolean = false, type: Int = InputType.TYPE_CLASS_TEXT) {
        label(title, 15f)
        inputs[key] = EditText(this).apply {
            inputType = if (multi) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
            setSingleLine(!multi)
            if (multi) minLines = 2
            setText(settings.get(key))
            root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun button(title: String, action: () -> Unit) {
        root.addView(Button(this).apply { text = title; setOnClickListener { action() } })
    }

    private fun validHttpUrl(value: String): Boolean {
        if (value.isBlank()) return true
        return runCatching {
            val uri = URI(value)
            uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
        }.getOrDefault(false)
    }

    private fun save(): Boolean {
        val url = inputs.getValue("url").text.toString().trim()
        if (!validHttpUrl(url)) { toast("입금알림 서버 URL을 확인하세요. http:// 또는 https:// 형식입니다."); return false }
        val faxServer = inputs.getValue("fax_server").text.toString().trim().trimEnd('/')
        if (!validHttpUrl(faxServer)) { toast("팩스 서버 주소를 확인하세요. 예: http://192.168.0.10:8010"); return false }
        val token = inputs.getValue("token").text.toString()
        val faxToken = inputs.getValue("fax_token").text.toString()
        if (token.contains('\n') || token.contains('\r') || faxToken.contains('\n') || faxToken.contains('\r')) {
            toast("토큰/연결코드에는 줄바꿈을 넣을 수 없습니다."); return false
        }
        val numbers = inputs.getValue("numbers").text.toString().split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (numbers.any { Parser.phone(it).isEmpty() || !it.matches(Regex("[+0-9()\\s-]+")) }) {
            toast("SMS 번호는 숫자, +, 공백, 하이픈으로 입력하세요."); return false
        }
        val packages = inputs.getValue("packages").text.toString().split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (packages.any { !it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) }) {
            toast("은행 앱 packageName 형식을 확인하세요. 예: com.example.bank"); return false
        }
        settings.save(inputs.mapValues { it.value.text.toString() })
        Bridge.schedule(this)
        toast("설정을 저장했습니다.")
        updateStatus()
        return true
    }

    private fun startFaxBridgeWithPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            return
        }
        FaxBridgeService.start(this)
        toast("팩스 브릿지를 켰습니다. ERP 팩스 요청을 기다립니다.")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2 && settings.faxEnabled) {
            FaxBridgeService.start(this)
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toast("팩스 요청 알림을 사용할 수 있습니다.")
            else toast("알림 권한이 없어 팩스 요청을 놓칠 수 있습니다. 설정에서 알림을 허용하세요.")
        }
    }

    private fun testFaxServer() {
        if (!save()) return
        val base = settings.get("fax_server").trimEnd('/')
        if (base.isBlank()) { toast("팩스 서버 주소를 먼저 입력하세요."); return }
        Thread {
            var con: HttpURLConnection? = null
            val result = try {
                con = URI("$base/health").toURL().openConnection() as HttpURLConnection
                con.requestMethod = "GET"; con.connectTimeout = 5000; con.readTimeout = 5000
                if (con.responseCode in 200..299) "연결 성공 · HC 모바일 팩스 브릿지 서버가 응답합니다." else "연결 실패 HTTP ${con.responseCode}"
            } catch (e: Exception) { "연결 실패 · ${e.javaClass.simpleName}" }
            finally { con?.disconnect() }
            runOnUiThread { toast(result) }
        }.start()
    }

    private fun openMobileFax() {
        val pkg = "com.dho.mobilefax"
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) { startActivity(launch); return }
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        try { startActivity(market) }
        catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) }
    }

    private fun updateStatus() {
        val sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val enabled = AndroidSettings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
            .split(':').mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == ComponentName(this, BankNotificationListener::class.java) }
        val count = Store(this).use { it.count() }
        val faxServer = settings.get("fax_server").ifBlank { "미설정" }
        status.text = "SMS 권한: " + (if (sms) "허용" else "미허용") +
            "\n알림 접근: " + (if (enabled) "허용" else "미허용") +
            "\n은행 자동 수집: " + (if (settings.collectionEnabled) "켜짐" else "꺼짐") +
            "\n전송 대기: " + count + " / 200건" +
            "\n팩스 브릿지: " + (if (settings.faxEnabled) "켜짐" else "꺼짐") +
            "\n팩스 서버: " + faxServer
    }

    private fun requestCollectionConsent(onApproved: () -> Unit = {}) {
        AlertDialog.Builder(this).setTitle("문자·알림 수집 및 전송 동의")
            .setMessage("등록한 발신번호의 새 SMS와 등록한 은행 앱의 새 알림을 앱이 닫혀 있어도 수집합니다. 발신정보, 문자·알림 원문, 파싱한 금액과 수신시각을 설정한 ERP 서버로 전송합니다. 금융정보나 인증번호가 원문에 포함될 수 있습니다.\n\n전송 대기는 기기에 최대 200건 저장합니다. 자동 수집을 끄거나 Android 권한을 철회하면 새 수집을 중단합니다. 이미 저장한 이벤트는 계속 전송됩니다. 본인 또는 동의받은 가족의 기기에서만 사용하세요.")
            .setPositiveButton("동의하고 자동 수집 켜기") { _, _ ->
                settings.setCollectionEnabled(true)
                updateStatus()
                onApproved()
            }
            .setNegativeButton("취소", null).show()
    }

    private fun showLogs() {
        val text = TextView(this).apply {
            text = Store(this@MainActivity).use { it.logs() }
            setPadding(24, 24, 24, 24); setTextIsSelectable(true)
        }
        AlertDialog.Builder(this).setTitle("전송 로그")
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("닫기", null).show()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        if (settings.faxEnabled) runCatching { FaxBridgeService.start(this) }
        handler.post(refresh)
    }

    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
