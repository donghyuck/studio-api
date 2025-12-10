# Studio Application Starter - Mail Service

IMAP 기반 메일 동기화 모듈(`studio-application-modules/mail-service`)을 자동 구성하는 스타터다. 전역 `studio.persistence.type` 또는 `studio.features.mail.persistence` 값에 따라 JPA/JDBC 구현을 선택하고, REST 컨트롤러를 조건부로 노출한다.

## 제공 기능
- `MailMessageService` / `MailAttachmentService` / `MailSyncService` / `MailSyncLogService` 자동 빈 등록 (JPA/JDBC 선택)
- JPA 선택 시 리포지토리/엔티티 스캔 포함
- REST 컨트롤러(`MailController`)는 `studio.features.mail.web.enabled=true` 일 때 등록

## 설정 예시
```yaml
studio:
  features:
    mail:
      enabled: true
      persistence: jdbc         # 선택: jpa|jdbc (미지정 시 studio.persistence.type 사용)
      web:
        enabled: true
        base-path: /api/mgmt/mail
      imap:
        host: imap.example.com
        port: 993
        username: user@example.com
        password: secret
        max-messages: 200
        concurrency: 4
        max-attachment-bytes: 10485760
        max-body-bytes: 1048576
        delete-after-fetch: false      # true 시 동기화 후 서버에서 메일 삭제(READ_WRITE 모드)
  persistence:
    type: jpa                   # 글로벌 기본값 (mail.persistence 미설정 시)
```

## REST 엔드포인트 (기본 base-path: `/api/mgmt/mail`)
- `GET /{mailId}`: 메일 + 첨부 메타 조회 (권한 `features:mail/read`)
- `GET /` : 메일 목록 페이지 조회(`page`,`size`) (권한 `features:mail/read`)
- `POST /sync`: IMAP 수동 동기화 요청(비동기) → `logId` 반환 (권한 `features:mail/write`)
- `GET /sync/logs?limit=50` 또는 `GET /sync/logs/page?page=&size=`: 최근 동기화 이력 조회 (권한 `features:mail/read`)
- `GET /sync/stream`: SSE 스트림으로 동기화 완료 이벤트 수신 (권한 `features:mail/read`)
- 동시 실행 방지: 이미 동기화 중이면 409(`error.mail.sync.in-progress`) 반환
- 중복 UID/파싱 오류 메일은 건너뛰고 실패 건수에 집계됨

## 의존성
- `studio-application-modules:mail-service`
- `studio-platform` / `studio-platform-autoconfigure` / `starter:studio-platform-starter`
- JavaMail (jakarta.mail) 런타임
