package kr.hc.bankbridge

import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private val deposit = listOf("입금", "받으셨")
    private val withdrawal = listOf("출금", "이체완료", "결제")
    private val alert = listOf("은행", "계좌", "잔액", "bank")
    private fun classify(text: String) = Parser.classify(text, deposit, withdrawal, alert)
    @Test fun classifiesWithoutDroppingGeneralMessages() {
        assertEquals("BANK_DEPOSIT", classify("입금 870,000원"))
        assertEquals("BANK_WITHDRAWAL", classify("출금 2,000원"))
        assertEquals("BANK_ALERT", classify("은행 점검 안내"))
        assertEquals("GENERAL", classify("안녕하세요"))
        assertEquals("BANK_WITHDRAWAL", classify("출금 후 입금 계좌 확인"))
    }
    @Test fun parsesKoreanWonAndDoesNotUseBalanceAsTransactionAmount() {
        assertEquals(870000L, Parser.amount("입금 870,000원 잔액 1,000,000원"))
        assertEquals(5000L, Parser.amount("출금 5000원"))
        assertEquals(1200L, Parser.amount("₩1,200"))
        assertNull(Parser.amount("은행 점검 10:30"))
        assertNull(Parser.amount("입금 9999999999999999999999999원"))
    }
    @Test fun normalizesKoreanPhoneNumbers() {
        assertEquals("01012345678", Parser.phone("+82 10-1234-5678"))
        assertEquals(Parser.phone("1588-1234"), Parser.phone("15881234"))
    }
}
