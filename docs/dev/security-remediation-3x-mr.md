## Why

3.x에서 검증을 완료한 보안 이슈 9건을 검토 가능한 변경으로 반영한다. RAG 객체 권한, 사용자 인증 상태, 토큰의 단일 소비, 실시간 연결 인가, 문서 미리보기 처리, ACL 변경 후 권한 갱신을 보강한다.

## What

| 이슈 | 반영 내용 |
|---|---|
| #531 | RAG 검색과 저장된 대화 재실행에서 승인된 검색 범위를 확인하고 범위 없는 요청을 거부 |
| #532 | JWT 인증·refresh 요청에서 현재 사용자 상태를 재확인 |
| #533 | 최신 비밀번호 재설정 토큰의 단일 소비와 트랜잭션 rollback 보장 |
| #534 | refresh token 회전의 단일 successor 생성, 실패 rollback 및 JPA 상태 갱신 보강 |
| #535 | WebSocket/STOMP 연결 인증과 목적지별 권한 검증 |
| #537 | EPUB/PPTX 미리보기의 외부 리소스·XML 처리 제한 |
| #538 | RAG 변경 작업에 명시적 write와 객체 변경 권한 요구, attachment source 일치 확인 |
| #539 | RAG 관리 검색·원문 조회에 저장된 객체 범위의 read 권한 요구 |
| #542 | ACL 객체 권한 삭제 후 유효 권한 캐시 갱신 |

- 공통 코드가 겹치는 #531/#538/#539는 하나의 RAG 커밋으로 묶고, 나머지는 이슈별로 분리한다.
- #533/#534의 JDBC/JPA 회귀에 필요한 테스트 의존성만 함께 반영한다. 운영 의존성이나 스키마 변경은 없다.
- #536/#540/#541/#543의 후보와 준비 전용 파일, Sanakan 정책·운영 지침 변경, 로컬 상태 파일은 포함하지 않는다. 이 네 이슈는 해결 완료로 표시하지 않는다.

3.x에 생성한 수정 커밋은 다음과 같다.

| 커밋 | 범위 |
|---|---|
| `6e4e93af` | #531, #538, #539 |
| `9957aa28` | #532 |
| `64c458e7` | #533 |
| `673462cb` | #534 |
| `15c69cf5` | #535 |
| `ca781bff` | #537 |
| `6b173d2e` | #542 |

## Related Issues

- Refs #531, #532, #533, #534, #535, #537, #538, #539, #542

## Validation

완료된 수정 65개 파일이 기존 검증 산출물과 동일함을 SHA-256으로 대조했다. 필요한 빌드 설정 1개를 포함한 정확한 staging 내용만 별도 복사본으로 만들어 아래 명령을 실행했다. 다른 미커밋 변경은 이 검증에 포함하지 않았다.

```sh
bash ./gradlew --offline --no-daemon --console=plain --max-workers=1 \
  :studio-platform:test \
  :starter:studio-platform-starter-ai-web:test \
  :studio-application-modules:attachment-service:test \
  :studio-application-modules:web-knowledge-service:test \
  :starter:studio-platform-starter-markdown:test \
  :studio-application-modules:content-embedding-pipeline:test \
  :starter:studio-platform-starter-ai:test \
  :studio-platform-security:test \
  :starter:studio-platform-starter-security:test \
  :studio-platform-realtime:test \
  :starter:studio-platform-starter-realtime:test \
  :studio-platform-thumbnail:test \
  :studio-platform-security-acl:test
```

- Result: `BUILD SUCCESSFUL in 2m 10s`, 116 tasks 모두 executed.
- JUnit: 13개 모듈, tests=1,253 / failures=0 / errors=0 / skipped=0.
- 실제 일회용 PostgreSQL을 사용한 `PasswordResetTokenDatabaseRegressionTest` 6개와 `RefreshTokenDatabaseRegressionTest` 6개 통과. JDBC v1/v2·JPA 경쟁, rollback 및 stale persistence context 회귀를 포함한다. 기존 또는 운영 DB에 연결하지 않았다.
- `git diff --cached --check` 통과.
- 기존 Sanakan 이슈별 일반·보안 리뷰 및 ready 결과를 대조했다. #533/#534는 각각 불변 patch의 두 리뷰가 pass/0 blocking이며, 이번에는 그 소스 bytes를 유지하고 통합 회귀를 다시 수행했다.
- 전체 저장소에 취약점이 없다는 판정이나 운영 배포·원격 CI 결과는 아니다.

## Risk / Rollback

- Risk: RAG 변경 권한과 객체 범위를 더 엄격하게 검사하므로, 기존 read fallback이나 범위 없는 요청을 사용한 호출자는 명시적 write 정책·올바른 객체 범위를 제공해야 한다. 잠금·비활성 사용자와 권한 없는 STOMP 목적지 접근은 거부된다.
- Risk: 토큰 소비·회전은 JDBC/JPA 트랜잭션 동작에 의존한다. PostgreSQL 회귀 결과를 기록했으며 다른 DB의 운영 동시성 결과를 주장하지 않는다.
- Rollback: 회귀가 발생한 변경 묶음의 커밋을 의존성을 확인해 함께 revert한다. 새 DDL이나 데이터 변환이 없으므로 역방향 마이그레이션은 필요하지 않다. 권한 검사를 되돌리면 해당 보안 보호도 해제되므로 적용 전 검토한다.

## AI / Subagent Usage

- AI-Assisted: Yes
- Subagent used: Yes
- Delegated scope: 기존 Sanakan 이슈별 구현·호스트 검증·독립 일반 리뷰·독립 보안 리뷰. 이번 작업은 완료 산출물의 선별, bytes 대조, 통합 회귀, 커밋 및 MR 초안 작성이다.
- Main author validation: 완료 9건의 정확한 파일과 기존 리뷰 증거, staging 범위, JUnit XML, 변경 분리 및 미완료 후보 제외를 확인했다.

## Checklist

- [x] commit message follows policy
- [x] issue template used or exception recorded
- [x] `AI-Assisted` value is correct
- [x] validation recorded
- [x] subagent usage recorded when used
- [x] CI / repository verification passed
- [ ] human review completed before merge
- [x] no unrelated changes included
