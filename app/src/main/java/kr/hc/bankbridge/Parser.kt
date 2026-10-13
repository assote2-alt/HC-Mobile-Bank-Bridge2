package kr.hc.bankbridge

object Parser {
    fun phone(value: String): String {
        val digits = value.filter { it.isDigit() }
        return if (digits.startsWith("82") && digits.length >= 10) "0" + digits.drop(2) else digits
    }
    fun classify(text: String, deposit: List<String>, withdrawal: List<String>, alert: List<String>): String {
        fun contains(words: List<String>) = words.any { it.isNotBlank() && text.contains(it, ignoreCase = true) }
        return when {
            contains(withdrawal) -> "BANK_WITHDRAWAL"
            contains(deposit) -> "BANK_DEPOSIT"
            contains(alert) -> "BANK_ALERT"
            else -> "GENERAL"
        }
    }
    fun amount(text: String): Long? {
        val matches = Regex("(?:₩\\s*)?([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\s*원").findAll(text).toList()
        // Prefer the amount next to the transaction keyword over a later balance.
        val transaction = Regex("입금|출금|이체|결제").find(text)
        val match = if (transaction != null) matches.firstOrNull { it.range.first >= transaction.range.first } ?: matches.firstOrNull() else matches.firstOrNull()
        return match?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
            ?: Regex("₩\\s*([0-9,]+)").find(text)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
    }
}
