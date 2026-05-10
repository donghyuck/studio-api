# Studio Application Starter - Mail Service

IMAP 기반 메일 동기화 모듈(`studio-application-modules/mail-service`)을 자동 구성하는 스타터다.
`studio.features.mail.persistence` 또는 전역 `studio.persistence.type` 값에 따라 JPA/JDBC 구현을 선택하고,
IMAP runtime 설정은 `studio.mail.imap.*`에서 읽는다. `studio.features.mail.imap.*`는 migration window 동안만 fallback으로 유지한다.

## 제공 기능
- `MailMessageService` / `MailAttachmentService` / `MailSyncService` / `MailSyncLogService` 자동 빈 등록 (JPA/JDBC 선택)
- JPA 선택 시 리포지토리/엔티티 스캔 포함
- REST 컨트롤러(`MailController`)는 `studio.features.mail.web.enabled=true`일 때 등록

## 패키지 import 기준
mail 모듈은 `domain/application/infrastructure/web` 구조를 사용한다. service 계약은 `studio.one.application.mail.application.usecase`, 구현체와 notifier는 `application.service`, IMAP 설정 모델은 `infrastructure.config`, JPA entity/repository는 `infrastructure.persistence.jpa`, DTO는 `web.dto.response` 기준으로 import한다. 기존 `studio.one.application.mail.service`, `config`, `domain.entity`, `persistence.repository`, `web.dto` 패키지는 제공하지 않는다.

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
        sse: true                # SSE stream 노출 여부. 미지정 시 true
        notify: sse              # 작업 완료 알림 전송 채널: sse|stomp
  persistence:
    type: jpa                   # 글로벌 기본값 (mail.persistence 미설정 시)
  mail:
    imap:
      host: imap.example.com
      port: 993
      username: ${STUDIO_MAIL_IMAP_USERNAME}
      password: ${STUDIO_MAIL_IMAP_PASSWORD}
      max-messages: 200
      concurrency: 4
      max-attachment-bytes: 10485760
      max-body-bytes: 1048576
      delete-after-fetch: false      # true 시 동기화 후 서버에서 메일 삭제(READ_WRITE 모드)
```

메일 스타터는 현재 `jpa`와 `jdbc` 구현만 제공한다. 애플리케이션 전역 기본값을
`studio.persistence.type=mybatis`로 두는 mixed persistence 앱에서는 mail의 MyBatis 전용
저장소가 없으므로 직접 JDBC 호환 경로를 사용한다. 이 직접 JDBC 경로는 PostgreSQL 전용 SQL을
사용하므로 PostgreSQL이 아닌 DB에서는 기동 시 fail-fast 된다. JPA를 사용하려면
`studio.features.mail.persistence=jpa`를 명시한다.

IMAP 계정/서버 설정은 `studio.mail.imap.*`를 기본으로 두고, 기존 `studio.features.mail.imap.*`는 transition fallback으로만 사용한다.

`studio.features.mail.web.sse`는 `/sync/stream` 엔드포인트 노출 여부만 제어한다. `studio.features.mail.web.notify=stomp`로 STOMP 알림을 쓰더라도 `sse=false`를 명시하지 않으면 SSE stream endpoint는 계속 노출되어 기존 클라이언트가 안정적으로 연결할 수 있다.

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

## 스키마
마이그레이션 파일 위치: `mail-service/src/main/resources/schema/mail/{db}/V1000__create_mail_tables.sql`

Flyway 버전 범위는 `docs/flyway-versioning.md`의 mail 범위(V1000-V1099)를 따른다.
