package kr.hc.bankbridge

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun get(key: String): String = prefs.getString(key, defaults[key] ?: "") ?: ""
    fun save(values: Map<String, String>) {
        prefs.edit().apply { values.forEach { (key, value) -> putString(key, value.trim()) } }.commit()
    }
    fun list(key: String) = get(key).split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
    fun acceptsSms(number: String) = list("numbers").any { Parser.phone(it) == Parser.phone(number) }
    fun acceptsPackage(name: String) = name in list("packages")
    companion object {
        val defaults = mapOf(
            "deposit" to "입금,입금완료,받으셨",
            "withdrawal" to "출금,출금완료,이체완료,결제",
            "alert" to "은행,계좌,잔액,뱅크,bank"
        )
    }
}
