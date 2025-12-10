# Mail Service (IMAP Sync)

IMAP 서버에서 메일을 읽어 DB(JPA/JDBC)로 동기화하는 모듈이다. `studio-application-starter-mail` 을 사용하면 persistence 유형에 따라 적절한 구현이 자동 등록되고, IMAP 설정만으로 동기화/첨부 수집을 수행할 수 있다.

## 구성 요소
- **MailMessageService**: 메일 저장/조회 추상화(JPA: `JpaMailMessageService`, JDBC: `JdbcMailMessageService`).
- **MailAttachmentService**: 첨부 저장/조회(JPA: `JpaMailAttachmentService`, JDBC: `JdbcMailAttachmentService`).
- **MailSyncService (ImapMailSyncService)**: IMAP에서 메시지/첨부를 가져와 upsert.
- **MailSyncLogService**: 동기화 이력 기록/조회(JPA/JDBC).
- **MailController**: REST/SSE(`/api/mgmt/mail` 기본) — 단건 조회(`GET /{mailId}`), 페이지 조회(`GET /?page=&size=`), 수동 동기화 요청(`POST /sync` → logId 반환, 비동기 실행), 동기화 이력 조회(`GET /sync/logs`, `GET /sync/logs/page`), SSE 완료 이벤트(`GET /sync/stream`).
- **도메인/엔티티**: `MailMessage`/`MailMessageEntity`, `MailAttachment`/`MailAttachmentEntity`(본문/첨부/헤더/프로퍼티).
- **SQL/DDL**: `sql/mail-sqlset.xml`, `schema/postgres/V0.8.0__create_mail_tables.sql`.

## 설정 예시
```yaml
studio:
  features:
    mail:
      enabled: true
      persistence: jdbc        # mail 전용 설정 (없으면 studio.persistence.type 사용)
      imap:
        host: imap.example.com
        port: 993
        username: user@example.com
        password: secret
        protocol: imaps        # 기본 imaps
        folder: INBOX
        max-messages: 500
        concurrency: 4         # 동시 처리 스레드 수
        max-attachment-bytes: 10485760  # 10MB 초과 첨부는 저장하지 않음
        max-body-bytes: 1048576         # 본문은 길이 제한으로 자름
        delete-after-fetch: false       # true 시 동기화 후 서버에서 메일 삭제(READ_WRITE 모드)
      web:
        enabled: true
        base-path: /api/mgmt/mail
  persistence:
    type: jpa                  # jpa | jdbc (mail.persistence 미설정 시 사용)
```

## 사용 방법
1. 의존성 추가: `starter/studio-application-starter-mail`.
2. IMAP 설정(host/port/user/password)과 `studio.persistence.type` 을 지정.
3. 애플리케이션에서 `MailSyncService` 빈을 주입해 `sync()` 를 호출하거나 스케줄링한다.
4. REST 사용 시 `studio.features.mail.web.enabled=true` 로 컨트롤러를 노출한다. 동기화는 `POST /sync` 로 트리거하고 `logId` 를 받아 `/sync/logs`/`/sync/logs/page` 또는 `SSE(/sync/stream)` 로 상태/완료 이벤트를 확인한다.

### Vue 예시: 동기화 요청 + SSE 수신
```ts
// axios 및 EventSource 사용 예
import axios from 'axios';

// 1) 동기화 요청 (logId 즉시 반환)
async function requestMailSync() {
  const { data } = await axios.post('/api/mgmt/mail/sync');
  const logId = data.body; // ApiResponse.ok(logId)
  console.log('sync requested, logId:', logId);
  return logId;
}

// 2) SSE 구독 (완료 이벤트 수신)
function subscribeMailSync(onMessage: (payload: any) => void) {
  const es = new EventSource('/api/mgmt/mail/sync/stream');
  es.addEventListener('mail-sync', (event: MessageEvent) => {
    const payload = JSON.parse(event.data); // MailSyncLogDto
    onMessage(payload);
  });
  es.onerror = () => es.close(); // 에러 시 정리
  return () => es.close();
}

// 3) Vue 컴포넌트에서 사용 예시
// setup() 또는 created() 등에서:
// const stop = subscribeMailSync((log) => {
//   if (log.logId === currentLogId) {
//     // 상태/성공/실패 UI 갱신
//     console.log('sync finished:', log);
//   }
// });
// onUnmounted(() => stop());
```

## 저장 모델
- **TB_APPLICATION_MAIL_MESSAGE**: `MAIL_ID`(PK), `FOLDER`+`UID`(UNIQUE), `MESSAGE_ID`, `SUBJECT`, 주소(From/To/Cc/Bcc), `SENT_AT`, `RECEIVED_AT`, `FLAGS`, `BODY`, `CREATED_AT`, `UPDATED_AT`.
- **TB_APPLICATION_MAIL_ATTACHMENT**: 첨부 바이너리/메타데이터 저장(`ATTACHMENT_ID` PK, `MAIL_ID` FK, `FILENAME`, `CONTENT_TYPE`, `SIZE`, `CONTENT`, `CREATED_AT`, `UPDATED_AT`).
- **TB_APPLICATION_MAIL_PROPERTY**: 메일별 확장 프로퍼티 맵.
