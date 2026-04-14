# 문서 개선 및 보완 사항

이 문서는 2026-04-14 기준으로 리포지토리 문서화 작업을 진행하면서 확인된
미완성 항목, 불일치, 개선 여지를 정리한다.

---

## 1. 즉시 보완 필요 (누락)

### 1-1. `starter/studio-platform-starter-realtime/README.md` 미작성

유일하게 README가 없는 스타터 디렉터리다.
아래 내용이 포함되어야 한다.

- 역할: WebSocket/STOMP 엔드포인트, Redis Pub/Sub 브로커 자동 구성
- 의존성 추가 (`spring-boot-starter-websocket`, Redis 포함 여부)
- `studio.realtime.*` 설정 속성 (STOMP endpoint, 허용 origin, JWT 연동 여부, reject-anonymous)
- 자동 구성되는 주요 빈
- fail-fast 조건: JWT 빈 미존재 시 기동 실패
- 관련 모듈: `studio-platform-realtime` (구현), `studio-platform-starter-security` (JWT)

```
starter/studio-platform-starter-realtime/README.md  ← 생성 필요
```

---

### 1-2. `starter/README.md` 포함 스타터 목록 불완전

현재 "포함 starter" 목록에 다음 두 항목이 빠져 있다.

| 누락 항목 | 설명 |
|---|---|
| `studio-platform-starter-objecttype` | ObjectType 레지스트리/정책 자동 구성 |
| `studio-platform-starter-realtime` | WebSocket/STOMP + Redis Pub/Sub 자동 구성 |

"문서 바로가기" 섹션도 신규 작성된 개별 스타터 README를 포함하지 않는다.
현재 3개만 링크되어 있으며, 아래 항목을 추가해야 한다.

```
starter/studio-platform-starter/README.md
starter/studio-platform-starter-security/README.md
starter/studio-platform-starter-security-acl/README.md
starter/studio-platform-starter-objecttype/README.md
starter/studio-platform-starter-ai/README.md
starter/studio-platform-starter-ai-web/README.md
starter/studio-platform-starter-objectstorage/README.md
starter/studio-application-starter-attachment/README.md
starter/studio-application-starter-avatar/README.md
starter/studio-application-starter-template/README.md
```

---

### 1-3. `studio-application-modules/README.md` Template 서비스 섹션 누락

빠른 선택 가이드에는 `template-service`가 언급되어 있으나,
다른 서비스(Avatar, Attachment, Mail 등)처럼 별도 `## Template 서비스` 상세 섹션이 없다.

추가할 내용:
- 활성화 속성 (`studio.features.template.enabled`)
- 영속성 (`jpa|jdbc`)
- 서비스 API (`TemplatesService`)
- REST 엔드포인트 요약 (CRUD + `render/body`, `render/subject`)

---

## 2. 참조 연결 누락

### 2-1. `docs/flyway-versioning.md` 미참조

Flyway 버전 범위 소유권 정책이 `docs/flyway-versioning.md`에 정리되어 있으나,
DB 스키마를 포함하는 어떤 모듈 README에서도 이 파일을 참조하지 않는다.

스키마 파일 경로를 언급하는 아래 README에 링크를 추가하면 된다.

| README | 스키마 보유 범위 |
|---|---|
| `studio-platform-user-default/README.md` | V300–V399 |
| `studio-platform-security/README.md` | V400–V499 |
| `studio-platform-security-acl/README.md` | V500–V599 |
| `studio-application-modules/avatar-service/README.md` | V700–V799 |
| `studio-application-modules/attachment-service/README.md` | V800–V899 |
| `studio-application-modules/template-service/README.md` | V900–V999 |
| `studio-application-modules/mail-service/README.md` | V1000–V1099 |

추가 형식 예시:
```markdown
## 스키마
마이그레이션 파일 위치: `src/main/resources/schema/mail/{postgres,mysql,mariadb}/`
버전 범위: V1000–V1099 (`docs/flyway-versioning.md` 참고)
```

---

### 2-2. `starter/STARTER_GUIDE.md` 미참조

새 스타터를 만드는 절차(`build.gradle.kts` 스켈레톤, AutoConfiguration 작성법,
`AutoConfiguration.imports` 등록 등)가 `starter/STARTER_GUIDE.md`에 문서화되어 있으나,
`starter/README.md`나 `CONTRIBUTING.md` 어디에서도 이 파일을 언급하지 않는다.

`starter/README.md` 하단 "문서 바로가기"에 한 줄 추가로 해결된다.

```markdown
- 새 스타터 작성 절차: `STARTER_GUIDE.md`
```

---

## 3. 설정 네임스페이스 불일치

### 3-1. 오브젝트 스토리지 속성 경로 혼재

오브젝트 스토리지 설정이 두 가지 접두사로 혼용되고 있다.

| 위치 | 사용 중인 접두사 |
|---|---|
| `starter/studio-platform-starter-objectstorage/README.md` | `studio.cloud.storage.*` |
| 루트 `README.md` 기본 설정 예시 (부재) | — |
| `CONFIGURATION_NAMESPACE_GUIDE.md` | 확인 필요 |

`studio.cloud.storage.*`가 실제 `@ConfigurationProperties` 바인딩 경로라면,
`starter/README.md`의 objectstorage 항목 설명과 기타 문서에서도 이를 통일해야 한다.

---

### 3-2. `studio-platform-autoconfigure` 오타 패키지 브리지 미문서화

CHANGELOG(2026-03-30)에 따르면 `perisistence`(오타) 패키지와
`persistence`(정상) 패키지가 호환 브리지로 병존한다.
이 사실이 `studio-platform-autoconfigure/README.md`에 기재되지 않아
패키지 경로를 직접 참조하는 경우 혼란을 줄 수 있다.

```markdown
> **주의** `autoconfigure.perisistence` 패키지는 오타로 인한 레거시 경로이며
> `autoconfigure.persistence`로 대체되었다. 두 경로 모두 동작하나 신규 코드는
> `persistence` 경로를 사용한다.
```

---

## 4. 개선 권고

### 4-1. 대표 스타터 조합 예시 추가

현재 `starter/README.md`나 루트 `README.md`에는 최소 조합 예시 한 가지만 있다.
아래와 같은 시나리오별 조합을 추가하면 처음 시작하는 개발자의 진입 비용이 줄어든다.

**기본 인증 앱**
```kotlin
implementation(project(":starter:studio-platform-starter"))
implementation(project(":starter:studio-platform-starter-security"))
implementation(project(":starter:studio-platform-starter-user"))
implementation(project(":studio-platform-user-default"))
```

**첨부파일 + AI 임베딩 앱**
```kotlin
implementation(project(":starter:studio-platform-starter"))
implementation(project(":starter:studio-platform-starter-security"))
implementation(project(":starter:studio-platform-starter-user"))
implementation(project(":starter:studio-application-starter-attachment"))
implementation(project(":studio-application-modules:content-embedding-pipeline"))
implementation(project(":starter:studio-platform-starter-ai"))
implementation("org.springframework.ai:spring-ai-starter-model-openai")
```

**실시간 알림 앱**
```kotlin
implementation(project(":starter:studio-platform-starter"))
implementation(project(":starter:studio-platform-starter-security"))
implementation(project(":starter:studio-platform-starter-realtime"))
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

---

### 4-2. 환경변수 → 모듈 매핑 명시

루트 `README.md`에 환경변수 목록이 있으나, 어떤 변수가 어떤 스타터를 필요로 하는지
연결이 없다. 아래와 같은 표를 루트 README 또는 `SECURITY.md`에 추가하면
환경 설정 누락으로 인한 기동 실패를 줄일 수 있다.

| 환경변수 | 관련 스타터 | 미설정 시 동작 |
|---|---|---|
| `STUDIO_JWT_SECRET` | `studio-platform-starter-security` | 기동 실패 (JwtSecretPresenceGuard) |
| `JASYPT_ENCRYPTOR_PASSWORD` | `studio-platform-starter-jasypt` | 암호화된 프로퍼티 복호화 실패 |
| `JASYPT_HTTP_TOKEN` | `studio-platform-starter-jasypt` | `/internal/jasypt` 엔드포인트 비활성 |
| `OPENAI_API_KEY` | `studio-platform-starter-ai` + OpenAI provider | AI 기동 실패 (AiSecretPresenceGuard) |
| `NEXUS_USERNAME` / `NEXUS_PASSWORD` | `scripts/publish-local-nexus.sh` | 배포 스크립트 실패 |

---

### 4-3. API 권한 스코프 통합 목록

각 모듈 REST 엔드포인트에서 사용하는 권한 스코프(`features:attachment/read` 등)가
모듈별 README에 분산되어 있다.
ACL 또는 인가 서버를 설정할 때 한 곳에서 전체 스코프를 확인할 수 있는 목록이 없다.

`docs/` 하위에 `api-permission-scopes.md` 문서를 추가하거나,
`studio-platform-security-acl/README.md`의 부록으로 포함하는 방안을 검토한다.

---

### 4-4. `docs/dev/spring-ai-openai.md` 접근성 개선

이 파일은 CHANGELOG에서만 참조되고 있다.
LangChain4j에서 Spring AI로의 OpenAI provider 전환 결정과 롤백 방법이 담겨 있어
AI 기능을 처음 설정하는 개발자에게 유용하다.

`starter/studio-platform-starter-ai/README.md`의 "참고 사항" 또는 "관련 문서" 항목에
링크를 추가하는 것을 권고한다.

---

## 5. 완료된 작업 (참고)

이번 작업에서 완료된 항목은 아래와 같다.

| 항목 | 상태 |
|---|---|
| `CLAUDE.md` 최초 작성 | ✅ |
| 루트 `README.md` 현행화 (AI 스타터, 경로 수정) | ✅ |
| 플랫폼 모듈 README 신규 작성 3건 (ai, autoconfigure, storage) | ✅ |
| 스타터 README 신규 작성 15건 | ✅ (realtime 제외) |
| 얇은 모듈 README 보강 2건 (avatar-service, content-embedding-pipeline) | ✅ |
| 모듈 README ↔ 스타터 교차 참조 추가 | ✅ |
| 모듈 README에 "대응 스타터" 섹션 추가 (8건) | ✅ |
| `content-embedding-pipeline` 잘못된 모듈명 수정 | ✅ |
