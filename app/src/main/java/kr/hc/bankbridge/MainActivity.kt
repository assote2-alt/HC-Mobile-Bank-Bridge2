package kr.hc.bankbridge

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.widget.*
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
        label("HC 입금알림 브릿지", 25f)
        label("등록한 번호의 SMS와 등록한 은행 앱 알림을 ERP 서버로 전송합니다. 키워드는 분류에만 사용하며 수집 내용을 제한하지 않습니다.", 15f)
        status = label("", 15f)
        button("SMS 수신 권한 허용") {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1)
        }
        button("알림 접근 권한 설정") {
            startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        label("서버 연결", 21f)
        field("url", "ERP 서버 URL", false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        field("token", "API 토큰 (Bearer)")
        inputs["token"]?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
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
        label("대기 이벤트는 최대 200건이며 초과하면 가장 오래된 건부터 삭제합니다. 인터넷 복구 후 자동 재시도합니다. 앱 강제 종료 후에는 앱을 다시 열어 주세요.", 14f)
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
    private fun save(): Boolean {
        val url = inputs.getValue("url").text.toString().trim()
        val valid = runCatching {
            val uri = URI(url)
            uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
        }.getOrDefault(false)
        if (!valid) { toast("http:// 또는 https://로 시작하는 서버 URL을 입력하세요."); return false }
        val token = inputs.getValue("token").text.toString()
        if (token.contains('\n') || token.contains('\r')) { toast("API 토큰에는 줄바꿈을 넣을 수 없습니다."); return false }
        val numbers = inputs.getValue("numbers").text.toString().split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (numbers.any { Parser.phone(it).isEmpty() || !it.matches(Regex("[+0-9()\\s-]+")) }) {
            toast("SMS 번호는 숫자, +, 공백, 하이픈으로 입력하세요."); return false
        }
        val packages = inputs.getValue("packages").text.toString().split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (packages.any { !it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) }) {
            toast("은행 앱 packageName 형식을 확인하세요. 예: com.example.bank"); return false
        }
        settings.save(inputs.mapValues { it.value.text.toString() })
        Bridge.schedule(this); toast("설정을 저장했습니다."); updateStatus()
        return true
    }
    private fun updateStatus() {
        val sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val enabled = AndroidSettings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
            .split(':').mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == ComponentName(this, BankNotificationListener::class.java) }
        val count = Store(this).use { it.count() }
        status.text = "SMS 권한: " + (if (sms) "허용" else "미허용") +
            "\n알림 접근: " + (if (enabled) "허용" else "미허용") +
            "\n전송 대기: " + count + " / 200건"
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
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
