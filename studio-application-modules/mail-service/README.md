# Mail Service (IMAP Sync)

IMAP 서버에서 메일을 읽어 DB(JPA/JDBC)로 동기화하는 모듈이다. `studio-application-starter-mail` 을 사용하면 persistence 유형에 따라 적절한 구현이 자동 등록되고, IMAP 설정만으로 동기화/첨부 수집을 수행할 수 있다.

## 구성 요소
- **MailMessageService**: 메일 저장/조회 추상화(JPA: `JpaMailMessageService`, JDBC: `JdbcMailMessageService`).
- **MailAttachmentService**: 첨부 저장/조회(JPA: `JpaMailAttachmentService`, JDBC: `JdbcMailAttachmentService`).
- **MailSyncService (ImapMailSyncService)**: IMAP에서 메시지/첨부를 가져와 upsert.
- **MailSyncLogService**: 동기화 이력 기록/조회(JPA/JDBC).
- **MailController**: REST API(`/api/mgmt/mail` 기본) 제공 — 단건 조회(`GET /{mailId}`), 수동 동기화(`POST /sync`), 동기화 이력 조회(`GET /sync/logs`).
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
4. REST 사용 시 `studio.features.mail.web.enabled=true` 로 컨트롤러를 노출한다. 동기화 결과는 `GET /sync/logs` 로 확인한다.

## 저장 모델
- **TB_APPLICATION_MAIL_MESSAGE**: `MAIL_ID`(PK), `FOLDER`+`UID`(UNIQUE), `MESSAGE_ID`, `SUBJECT`, 주소(From/To/Cc/Bcc), `SENT_AT`, `RECEIVED_AT`, `FLAGS`, `BODY`, `CREATED_AT`, `UPDATED_AT`.
- **TB_APPLICATION_MAIL_ATTACHMENT**: 첨부 바이너리/메타데이터 저장(`ATTACHMENT_ID` PK, `MAIL_ID` FK, `FILENAME`, `CONTENT_TYPE`, `SIZE`, `CONTENT`, `CREATED_AT`, `UPDATED_AT`).
- **TB_APPLICATION_MAIL_PROPERTY**: 메일별 확장 프로퍼티 맵.
