# Security Policy

## Supported Versions

보안 수정과 secret 관리 기준은 현재 기본 브랜치 기준으로만 유지한다. 별도 공지 없는 과거 태그/브랜치는 보안 업데이트 지원 대상이 아니다.

## Reporting a Vulnerability

취약점이나 secret 노출을 확인하면 공개 이슈로 먼저 올리지 말고 비공개 채널로 전달한다. 제보에는 영향 범위, 재현 절차, 관련 모듈/파일/엔드포인트, 인증 필요 여부, 가능한 임시 완화책을 포함한다.

## Secret Management

저장소에는 비밀값을 커밋하지 않는다. 다음 값들은 환경변수, 시크릿 매니저, 또는 로컬 전용 `~/.gradle/gradle.properties` 로만 제공한다.

- `STUDIO_JWT_SECRET`
- `JASYPT_ENCRYPTOR_PASSWORD`
- `JASYPT_HTTP_TOKEN`
- `STUDIO_MAIL_IMAP_USERNAME`
- `STUDIO_MAIL_IMAP_PASSWORD`
- `STUDIO_MAIL_SMTP_USERNAME`
- `STUDIO_MAIL_SMTP_PASSWORD`
- `OPENAI_API_KEY`
- `GEMINI_API_KEY`
- `STUDIO_OBJECT_STORAGE_ACCESS_KEY`
- `STUDIO_OBJECT_STORAGE_SECRET_KEY`
- `NEXUS_USERNAME`
- `NEXUS_PASSWORD`

이미 저장소, 로그, 백업 등에 노출된 값은 코드에서 지워도 안전하지 않다. 즉시 회전하고 외부 시스템 감사 로그를 확인한다.

## Fail-Fast Configuration

다음 기능은 활성화된 상태에서 필수 secret이 없으면 부팅 초기에 실패한다.

- JWT: `studio.security.jwt.enabled=true` 이면 `studio.security.jwt.secret` 필요
- Mail: `studio.features.mail.enabled=true` 이면 IMAP `host`, `username`, `password` 필요
- AI: `studio.ai.enabled=true` 이면 provider type에 따라 `api-key` 또는 `base-url` 필요
- Object Storage: 활성 provider가 `s3`면 `access-key`, `secret-key`, `fs`면 `fs.root` 필요

개발 환경에서도 빈 문자열이나 임시 하드코딩으로 우회하지 않는다.

## Local Development

로컬 실행 시 필요한 값은 셸 환경변수로 주입한다.

```bash
export JASYPT_ENCRYPTOR_PASSWORD=change-me
export STUDIO_JWT_SECRET=change-me
export STUDIO_MAIL_IMAP_USERNAME=change-me
export STUDIO_MAIL_IMAP_PASSWORD=change-me
export NEXUS_USERNAME=change-me
export NEXUS_PASSWORD=change-me
```

프로젝트 루트 `gradle.properties` 는 비밀값 보관용이 아니다. 로컬 전용 Gradle 자격증명이 필요하면 사용자 홈 디렉터리의 `~/.gradle/gradle.properties` 를 사용한다.
