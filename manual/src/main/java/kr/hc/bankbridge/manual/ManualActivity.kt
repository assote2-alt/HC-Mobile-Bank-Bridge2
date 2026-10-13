package kr.hc.bankbridge.manual

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.widget.*
import java.net.URI
import java.util.UUID
import kr.hc.bankbridge.Bridge
import kr.hc.bankbridge.Settings
import kr.hc.bankbridge.Store

class ManualActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var message: EditText
    private lateinit var sender: EditText
    private val inputs = linkedMapOf<String, EditText>()
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { updateStatus(); handler.postDelayed(this, 3000) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 40)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        @Suppress("DEPRECATION")
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        label("HC 입금알림 브릿지 · 수동", 25f)
        label("문자나 은행 알림을 복사해서 붙여넣고 전송하세요. 이 버전은 SMS와 다른 앱 알림을 자동으로 읽지 않습니다.", 16f)
        label("전송을 누르면 입력한 원문, 발신정보, 금액, 시각을 설정한 ERP 서버로 보냅니다. 금융정보나 인증번호가 포함될 수 있으므로 전송할 내용을 확인하세요.", 14f)
        status = label("", 15f)
        label("서버 연결", 21f)
        field("url", "ERP 서버 URL", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        field("token", "API 토큰 (Bearer)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        label("전송할 문자", 21f)
        label("발신정보", 15f)
        sender = EditText(this).apply { setSingleLine(true); setText("수동입력"); root.addView(this) }
        label("문자·알림 내용", 15f)
        message = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            filters = arrayOf(InputFilter.LengthFilter(50000))
            root.addView(this)
        }
        button("입력한 이벤트 전송") {
            val body = message.text.toString().trim()
            if (body.isBlank()) toast("전송할 문자를 입력하세요.")
            else if (save()) {
                Bridge.capture(this, "MANUAL", sender.text.toString().trim().ifBlank { "수동입력" },
                    body, System.currentTimeMillis(), UUID.randomUUID().toString())
                toast("이벤트를 저장했습니다. 전송 결과는 로그에서 확인하세요.")
                message.text.clear()
                updateStatus()
            }
        }
        button("테스트 입금 이벤트 전송") {
            if (save()) {
                Bridge.capture(this, "TEST", "테스트", "입금 870,000원", System.currentTimeMillis(),
                    UUID.randomUUID().toString(), "BANK_DEPOSIT")
                toast("테스트 입금 이벤트를 저장했습니다.")
                updateStatus()
            }
        }
        label("분류 키워드", 21f)
        field("deposit", "입금 키워드")
        field("withdrawal", "출금 키워드 · 입금보다 우선 적용")
        field("alert", "은행 알림 키워드")
        button("설정 저장") { save() }
        button("대기 이벤트 재전송") { if (save()) { Bridge.schedule(this); toast("재전송을 예약했습니다.") } }
        button("전송 로그 화면") { showLogs() }
        label("인터넷 연결이 끊기면 최대 200건 저장하고 연결 복구 후 자동 재시도합니다. 초과하면 가장 오래된 이벤트부터 삭제합니다.", 14f)
        readSharedText(intent)
    }
    private fun label(value: String, size: Float): TextView = TextView(this).apply {
        text = value; textSize = size; setPadding(0, 12, 0, 12); root.addView(this)
    }
    private fun field(key: String, title: String, type: Int = InputType.TYPE_CLASS_TEXT) {
        label(title, 15f)
        inputs[key] = EditText(this).apply {
            inputType = type; setSingleLine(true)
            setText(settings.get(key)); root.addView(this)
        }
    }
    private fun button(title: String, action: () -> Unit) {
        root.addView(Button(this).apply { text = title; setOnClickListener { action() } })
    }
    private fun save(): Boolean {
        val url = inputs.getValue("url").text.toString().trim()
        val valid = runCatching {
            val uri = URI(url)
            uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.fragment == null
        }.getOrDefault(false)
        if (!valid) { toast("http:// 또는 https://로 시작하는 서버 URL을 입력하세요."); return false }
        val token = inputs.getValue("token").text.toString()
        if (token.contains('\n') || token.contains('\r')) { toast("API 토큰에는 줄바꿈을 넣을 수 없습니다."); return false }
        settings.save(inputs.mapValues { it.value.text.toString() })
        Bridge.schedule(this)
        return true
    }
    private fun updateStatus() {
        status.text = "수동 입력 모드 · 자동 수집 없음\n전송 대기: " + Store(this).use { it.count() } + " / 200건"
    }
    private fun showLogs() {
        val view = TextView(this).apply {
            text = Store(this@ManualActivity).use { it.logs() }
            setPadding(24, 24, 24, 24); setTextIsSelectable(true)
        }
        AlertDialog.Builder(this).setTitle("전송 로그")
            .setView(ScrollView(this).apply { addView(view) }).setPositiveButton("닫기", null).show()
    }
    @Suppress("DEPRECATION")
    private fun readSharedText(incoming: Intent) {
        if (incoming.action == Intent.ACTION_SEND && incoming.type == "text/plain") {
            message.setText(incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(50000).orEmpty())
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readSharedText(intent) }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
