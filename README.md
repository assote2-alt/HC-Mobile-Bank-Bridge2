# HC 입금알림 브릿지

가족끼리 직접 APK를 설치하는 Kotlin Android 앱입니다. Android 8.0(API 26) 이상을 지원합니다.

## 설치와 설정
1. GitHub Actions의 **Build Android APK** 성공 실행 → Artifacts → **HC-Mobile-Bank-Bridge-debug** 다운로드 → ZIP 안의 app-debug.apk 설치.
2. 앱에서 ERP 서버 URL과 API 토큰을 입력하고 SMS 발신번호 및 은행 앱 packageName을 등록하세요. 줄바꿈 또는 쉼표로 여러 항목을 등록합니다.
3. SMS 수신 권한과 Android 알림 접근 권한을 허용하세요. Android가 알림 접근을 제한하면 앱 정보의 메뉴에서 제한된 설정 허용 후 다시 시도하세요.
4. 설정 저장 후 테스트 입금 이벤트를 전송하고 전송 로그를 확인하세요. 은행 앱 packageName은 해당 앱의 Play Store URL의 id 값으로 확인할 수 있습니다.

등록한 번호의 SMS는 키워드와 관계없이 모두 수집합니다. 새로 게시된 등록 패키지의 알림만 수집하며 그룹 요약 알림은 제외합니다. 앱에 수신 이전의 SMS나 알림을 소급 조회하는 기능은 없습니다. 사용자가 권한을 철회하면 수집되지 않습니다.

## ERP 요청
Content-Type: application/json; charset=utf-8  
Authorization: Bearer 설정된_API_토큰 (빈 값이면 생략)  
Idempotency-Key: eventId

예:
```json
{
  "eventId": "uuid",
  "type": "BANK_DEPOSIT",
  "source": "SMS",
  "sender": "15881234",
  "message": "입금 870,000원",
  "amount": 870000,
  "receivedAt": "2026-10-13T01:00:00Z"
}
```
source는 SMS, NOTIFICATION, TEST입니다. NOTIFICATION은 sender와 packageName에 앱 패키지를 포함합니다.
분류는 출금 → 입금 → 은행 알림 → GENERAL 우선순위이며 키워드는 앱에서 수정합니다.
금액은 원/₩ 표기의 정수로 파싱하고 없거나 범위를 넘으면 null입니다. 여러 금액이 있으면 거래 키워드 뒤 첫 금액을 우선합니다. 은행별 문장 형식에 따라 정확한 거래 금액인지 ERP에서 확인하세요.

## 저장 및 재시도
모든 이벤트를 먼저 내부 SQLite에 저장합니다. 최대 200건이며 초과하면 가장 오래된 이벤트를 삭제하고 로그에 남깁니다.
WorkManager가 인터넷 연결 시 실행하며 실패하면 지수 백오프와 15분 주기 작업으로 자동 재시도합니다. Android 절전 정책에 따라 재전송이 지연될 수 있습니다.
HTTP 2xx만 전송 성공으로 처리하며 리다이렉트와 4xx/5xx도 저장한 상태로 재시도합니다. 잘못된 URL/토큰은 설정을 수정하세요.
응답 유실 때 동일 이벤트가 재전송될 수 있으므로 ERP는 eventId 또는 Idempotency-Key로 중복 처리 방지해야 합니다.
로그는 최근 300건만 보관하고 문자 본문이나 API 토큰을 기록하지 않습니다. 설정과 대기 데이터는 앱 내부 저장소에 보관하며 백업을 비활성화했습니다.
앱 강제 중지 후에는 다시 열어야 수신과 작업이 재개됩니다. 일부 제조사에서는 배터리 최적화 예외 설정이 필요합니다.
토큰 보호를 위해 HTTPS URL 사용을 권장합니다. 로컬 ERP의 HTTP도 지원합니다.

## 빌드
JDK 17, Android SDK 35 및 인터넷 연결:
```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```
APK: app/build/outputs/apk/debug/app-debug.apk  
main push 또는 수동 실행 시 .github/workflows/build-apk.yml에서 APK를 빌드합니다.
debug APK는 Actions 실행마다 서명키가 달라질 수 있어 이전 버전 위에 설치가 안 되면 삭제 후 설치해야 합니다(설정/대기열 삭제). 장기 업데이트에는 고정 서명키 사용이 필요합니다.
