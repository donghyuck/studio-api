# Changelog

## 2026-04-16

### 변경됨
- 이슈 #221 대응으로 `studio-platform-chunking` 계약 모듈과 `starter:studio-platform-starter-chunking`을 추가해 RAG indexing chunking을 starter 형태로 분리했다.
- `DefaultRagPipelineService`가 `ChunkingOrchestrator`를 optional로 사용하고, 없으면 기존 `TextChunker` fallback을 유지하도록 했다.
- `VectorStorePort`에 object scope replace/delete 흐름을 추가해 같은 `objectType`/`objectId` 재색인 시 stale chunk가 남지 않도록 했다.
- 이슈 #219 대응으로 Chunking starter와 Spring AI Retrieval pipeline을 병렬 구현하기 위한 `docs/dev/chunking-rag-pipeline-plan.md` 계획 문서를 추가했다.
- 이슈 #213 대응으로 AI web endpoint에서 provider quota/rate limit 예외를 500 대신 429 `ProblemDetails`로 반환하도록 했다.
- 이슈 #217 대응으로 `studio-platform-ai`를 AI/RAG 공통 계약 중심 모듈로 축소하고, RAG pipeline 구현체와 pgvector adapter, LLM 기반 keyword/cleaner 구현을 `starter:studio-platform-starter-ai`로 이동했다.
- 기존 `RagPipelineService`는 같은 FQN의 facade interface로 유지하고, 기본 구현은 starter의 `DefaultRagPipelineService`로 분리해 web/content 소비 모듈의 계약 의존을 유지했다.
- `TextCleaner`, `KeywordExtractor`, `PromptRenderer`, RAG option 타입은 확장 계약으로 `studio-platform-ai`에 유지하고, 관련 테스트 fixture와 구현 테스트를 starter 모듈로 이동했다.
- `RagPipelineService.SERVICE_NAME`의 `rag-pipelien-service` 오탈자를 `rag-pipeline-service`로 수정하고, 기존 bean name은 `LEGACY_SERVICE_NAME` alias로 유지했다.
- `studio-platform-ai`에서 Spring/JDBC/pgvector/Caffeine/Resilience4j 구현 의존을 제거했다.
- 이슈 #215 대응으로 AI web endpoint 설정을 `studio.ai.endpoints.enabled`, `base-path`, `mgmt-base-path` 기준으로 정리했다.
- Breaking: embedding/vector/RAG endpoint 기본 경로를 `/api/ai`에서 `/api/mgmt/ai`로 변경했다. 기존 경로를 유지하려면 `studio.ai.endpoints.mgmt-base-path=/api/ai`를 설정해야 한다.
- 사용자용 AI endpoint는 기본 `/api/ai`, 관리용 embedding/vector/RAG endpoint는 기본 `/api/mgmt/ai`를 사용하도록 분리했다.
- `studio.ai.endpoints.enabled=false`가 AI web controller 전체 비활성화로 동작하도록 자동 구성 조건을 정리했다.
- 기존 `studio.ai.endpoints.enabled=false`는 `AiInfoController`만 숨겼지만, 이제 AI web endpoint 전체를 비활성화한다.
- 이슈 #206 대응으로 `studio.ai.pipeline.keywords.scope`, `max-input-chars`와 `studio.ai.pipeline.retrieval.query-expansion.*` 설정을 추가해 keyword metadata 범위와 query expansion 동작을 조정할 수 있도록 했다.
- 기본 `keywords.scope=document`는 기존 문서 단위 `keywords`/`keywordsText` 동작을 유지하고, `chunk` 또는 `both` 설정 시 chunk metadata에 `chunkKeywords`/`chunkKeywordsText`를 추가한다.
- `LlmKeywordExtractor`의 입력 최대 길이 4000자 제한을 설정으로 이동하고, keyword trim/blank 제거/case-insensitive de-duplication을 적용했다.
- `studio.ai.vector.postgres.text-search-config=simple`은 향후 PostgreSQL FTS config 지원을 위한 문서화된 설정 후보로 남기고, 이번 작업에서는 기존 PostgreSQL SQL ranking 동작과 DB migration은 변경하지 않았다.
- 이슈 #205 대응으로 `studio.ai.pipeline.diagnostics.*`와 `studio.ai.endpoints.rag.diagnostics.allow-client-debug` 설정을 추가해 RAG 검색 fallback 전략과 결과 상태를 선택적으로 관찰할 수 있도록 했다.
- `RagRetrievalDiagnostics`를 추가해 strategy, result count, score threshold, hybrid weight, object scope, topK를 기록하고, client debug 허용 시에만 `ChatResponseDto.metadata.ragDiagnostics`에 노출한다.
- 기존 `POST /api/ai/chat/rag`의 per-hit info 로그를 제거하고, diagnostics result logging이 명시적으로 활성화된 경우에만 bounded debug snippet을 출력하도록 했다.
- 이슈 #204 대응으로 `studio.ai.pipeline.cleaner.*` 설정을 추가해 RAG 색인 전 LLM 기반 텍스트 정제를 선택적으로 적용할 수 있도록 했다.
- `TextCleaner`/`LlmTextCleaner`를 추가하고 `rag-cleaner` prompt의 `clean_text` JSON 응답을 색인 텍스트로 사용하도록 했다.
- `RagPipelineService.index()`가 cleaner 적용 여부, 원문/색인 텍스트 길이, chunk 수, chunk 길이를 vector metadata에 additive로 기록하도록 했다.
- 첨부 RAG 인덱싱 metadata에 `filename`, `sourceType=attachment`, `indexedAt`을 `putIfAbsent`로 추가해 클라이언트/운영 추적 정보를 보강했다.
- 이슈 #202 대응으로 `RagPipelineService`의 hybrid 검색 weight, 최소 relevance score, keyword/semantic fallback 사용 여부를 `studio.ai.pipeline.retrieval.*` 설정으로 조정할 수 있도록 했다.
- query 없는 object-scope RAG 조회가 과도한 chunk를 반환하지 않도록 `studio.ai.pipeline.object-scope.default-list-limit`, `max-list-limit` 설정과 service layer clamp를 추가했다.
- `POST /api/ai/chat/rag`가 system context에 포함하는 RAG chunk 수/문자 수와 score 포함 여부를 `studio.ai.endpoints.rag.context.*` 설정으로 제한하도록 했다.
- hybrid search weight는 합계가 0보다 커야 하며, context 문자 수 한도 초과 시 chunk를 중간 절단하지 않고 제외하도록 명확히 했다.
- Issue #203의 RAG 품질 개선 Phase 2 범위로 live LLM 호출 없이 동작하는 deterministic RAG smoke fixture를 추가했다.
- 한국어 정책형 fixture와 첨부 요약형 fixture를 추가해 한국어 질의가 기대 chunk로 매핑되는지, object scope 검색이 다른 첨부 chunk를 반환하지 않는지, `listByObject`가 chunk 순서를 보존하는지 검증한다.
- Phase 1의 context truncation 구현에 의존하는 검증은 이번 범위에서 제외하고 후속 Phase 1/통합 검증 대상으로 남겼다.
- 이 작업은 PR #207의 RAG 설정화 변경과 통합 검증되도록 `2.x` 최신으로 rebase했다.

### 검증
- `gradle :studio-platform-chunking:test :starter:studio-platform-starter-chunking:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test :studio-platform-ai:test`
- `git diff --check`
- `./gradlew :starter:studio-platform-starter-ai-web:test`
- `gradle :studio-platform-ai:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test`
- `gradle :starter:studio-platform-starter-ai-web:test`
- `gradle :studio-platform-ai:test`
- `gradle :starter:studio-platform-starter-ai:test`
- `gradle :studio-platform-ai:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test --rerun-tasks`
- `gradle :studio-platform-ai:test`
- `gradle :starter:studio-platform-starter-ai-web:test`
- `gradle :studio-platform-ai:test --tests 'studio.one.platform.ai.service.pipeline.RagQualitySmokeTest'`
- `gradle :studio-platform-ai:test`
- `gradle :starter:studio-platform-starter-ai:test`
- `gradle :studio-application-modules:content-embedding-pipeline:test`
- `./gradlew :studio-platform-ai:test`
- `./gradlew :starter:studio-platform-starter-ai-web:test`
- `git diff --check`

## 2026-04-15

### 변경됨
- Spring AI 기반 AI web API가 기존 React 클라이언트 계약을 수용하도록 `ChatRequestDto.provider`, `ChatRequestDto.systemPrompt`를 추가하고, `ChatController`가 `AiProviderRegistry`로 provider별 `ChatPort`를 선택하도록 변경했다.
- `POST /api/ai/chat/rag`가 파일/객체 범위 RAG 답변에 사용할 `objectType`/`objectId` 흐름과 client system prompt를 함께 지원하도록 보강했다.
- `POST /api/ai/chat/rag`의 attachment 범위 RAG가 `features:attachment read` 권한을 추가로 요구하고, 안전하게 권한 확인할 수 없는 object scope 요청을 거부하도록 보강했다.
- `POST /api/ai/chat`의 `provider` 입력을 trim/blank normalize하고 알 수 없는 provider를 400 오류로 반환하도록 정리했다.
- `POST /api/ai/vectors/search` 응답에 기존 `documentId`와 동일한 `id` alias를 추가해 클라이언트 grid와 기존 소비자를 모두 지원하도록 했다.
- `ObjectTypeMgmtController`에 `GET /api/mgmt/object-types/{objectType}/policy/effective`를 추가해 저장 정책이 없을 때도 클라이언트가 실제 적용 정책(`source=default`, ObjectType별 추가 제한 없음)을 안내할 수 있도록 했다.

### 검증
- `./gradlew :starter:studio-platform-starter-ai-web:test`
- `./gradlew :starter:studio-platform-starter-ai:compileJava`
- `./gradlew :studio-platform-ai:test`
- `./gradlew :studio-platform-objecttype:test`
- `./gradlew :starter:studio-platform-starter-objecttype:compileJava`

## 2026-04-14

### 변경됨
- README 계층을 소스 기준으로 현행화해 활성 모듈/스타터 목록, 대표 스타터 조합, 환경변수 매핑을 보강했다.
- `starter/studio-platform-starter-realtime/README.md`를 추가해 STOMP/WebSocket, Redis Pub/Sub, JWT handshake 자동 구성을 문서화했다.
- schema 보유 모듈 README의 Flyway 버전 참조를 실제 `Vxxx__*.sql` 파일명과 `docs/flyway-versioning.md` 기준으로 정리했다.
- `docs/documentation-improvements.md`를 현재 상태/완료 항목/즉시 보완 후보/표준화/소스 품질 개선 후보 기준으로 재정리했다.
- `template-service`의 관리 컨트롤러 클래스를 `TemplateController`에서 `TemplateMgmtController`로 변경해 관리용 컨트롤러 명명 규칙을 맞췄다.
- `GET /api/mgmt/templates` 계열 응답의 `createdBy`, `updatedBy`를 숫자 userId 대신 `{ userId, username }` 형태의 `UserDto`로 변경했다.
- `template-service`에 사용자 응답 매핑 회귀 테스트를 추가하고, 기존 권한 테스트를 새 컨트롤러명 기준으로 갱신했다.
- `starter:studio-application-starter-template`의 auto-configuration과 관련 README가 새 컨트롤러명 `TemplateMgmtController`를 참조하도록 맞춰 starter 컴파일 오류를 수정했다.

### 검증
- `rg "TemplateController|V0__" README.md starter studio-application-modules studio-platform* docs -g 'README.md' -g '*.md' -g '!**/build/**' -g '!**/bin/**'`
- `rg "STARTER_GUIDE|spring-ai-openai|flyway-versioning|studio-platform-starter-realtime/README.md" README.md starter studio-application-modules studio-platform* docs -g 'README.md' -g '*.md' -g '!**/build/**' -g '!**/bin/**'`
- README 누락 확인 스크립트 (`settings.gradle.kts`의 공식 include 기준)
- `./gradlew :starter:studio-platform-starter-realtime:compileJava`
- `./gradlew :studio-application-modules:template-service:test`
- `./gradlew :starter:studio-application-starter-template:compileJava`

## 2026-04-10

### 변경됨
- `DELETE /groups/{id}/members`의 요청 body를 raw `List<Long>`에서 `{"userIds":[...]}` 형태의 `AddMembersRequest`로 교체해 `POST /groups/{id}/members`와 API 계약을 통일했다.
- `GroupMgmtController`의 메서드명 오타 `removeaddMemberships`를 `removeMemberships`로 수정했다.
- `GET /groups/{id}/member-summaries`의 `ApplicationGroupMemberSummary` → `GroupMemberSummaryDto` 매핑을 컨트롤러 인라인에서 `ApplicationGroupService.getMemberSummaryDtos()` default 메서드로 위임했다.
- `GroupMgmtControllerTest`에 `removeMembershipsCallsServiceWithUserIds` 테스트를 추가했다.

### 검증
- `./gradlew :studio-platform-user:test`
- `./gradlew :studio-platform-user:compileJava :studio-platform-user-default:compileJava`

## 2026-04-09

### 변경됨
- 그룹 멤버 조회용 전용 읽기 API `GET /api/mgmt/groups/{id}/member-summaries`를 추가했다.
- 그룹 멤버 summary 조회는 `userId`, `username`, `name`, `enabled`만 반환하고 `username`/`name`/`email` 기준 `q` 검색을 지원한다.
- 기존 `GET /api/mgmt/groups/{id}/members`는 유지하고, `studio-platform-identity` 계약은 변경하지 않았다.
- `studio-platform-user`에 그룹 멤버 summary 조회용 repository/service/controller 테스트와 최소 테스트 의존성을 추가했다.

### 검증
- `./gradlew :studio-platform-user:test`
- `./gradlew :studio-platform-user:compileJava :studio-platform-user-default:compileJava`

## 2026-04-08 (local nexus publish)

### 변경됨
- `gradle.properties`를 수정하지 않고 로컬 Nexus로 배포할 수 있도록 `scripts/publish-local-nexus.sh`를 추가했다.
- 로컬 Nexus에 같은 버전의 모듈이 이미 있을 때 `--delete-existing`로 전체 모듈을 확인해 삭제 후 재배포할 수 있도록 했다.
- 특정 모듈만 처리할 수 있도록 `--delete-existing --module <gradle-path>`도 지원한다.
- 로컬 Nexus 배포 스크립트가 기본적으로 `.env.local`을 읽어 `NEXUS_USERNAME`, `NEXUS_PASSWORD`, `NEXUS_URL` 값을 사용할 수 있도록 했다.
- README에 로컬 Nexus 배포 절차와 특정 모듈 publish 예시를 추가했다.

### 검증
- `bash -n scripts/publish-local-nexus.sh`
- `scripts/publish-local-nexus.sh` 환경변수 미설정 실패 경로 확인
- `scripts/publish-local-nexus.sh --delete-existing` 환경변수 미설정 실패 경로 확인
- `git diff --check`

## 2026-04-08

### 변경됨
- `UserMgmtControllerApi`를 `UserMgmtApi`로 변경해 사용자 관리 엔드포인트 확장 인터페이스 이름에서 컨트롤러 구현 세부 표현을 제거했다.
- `UserPublicControllerApi`, `UserMeControllerApi`, `UserAuthPublicControllerApi`도 같은 기준의 `UserPublicApi`, `UserMeApi`, `UserAuthPublicApi`로 정리했다.
- 일반 사용자 정보 수정 시 기존 비밀번호 해시가 다시 인코딩되지 않도록 비밀번호 인코딩을 비밀번호 전용 변경/초기화 경로로 제한했다.
- 일반 사용자 정보 수정 경로에서 mutator가 비밀번호 값을 변경하면 저장 전에 실패하도록 방어를 추가했다.
- 일반 사용자 정보 수정과 비밀번호 초기화 경로를 구분하는 회귀 테스트를 추가했다.

### 검증
- `./gradlew :studio-platform-user:compileJava :studio-platform-user-default:compileJava :starter:studio-platform-starter-user:compileJava`
- `./gradlew :studio-platform-user-default:test --tests 'studio.one.base.user.service.impl.ApplicationUserServiceImplTest'`
- `rg "User(Mgmt|Public|AuthPublic|Me)ControllerApi|User(Mgmt|Public|AuthPublic|Me)Api" studio-platform-user studio-platform-user-default starter/studio-platform-starter-user`

## 2026-03-31 (follow-up)

### 변경됨
- `AttachmentServiceImpl`의 `InputStream` 기반 size 계산을 `available()`에서 임시 파일 버퍼링으로 바꿔 정확한 크기를 보장하도록 수정했다. 서비스 레이어에서도 최대 50MB 상한을 다시 적용한다.
- `AttachmentServiceImpl`의 `File` 기반 업로드는 입력 스트림을 try-with-resources로 닫고, 너무 큰 파일은 명시적으로 실패하도록 정리했다.
- storage save 실패 시 partial binary만 best-effort로 정리하고, 메타데이터는 트랜잭션 rollback에 맡기도록 저장 경계를 명확히 했다. 입력 스트림 close 실패는 경고로만 남기고 저장 성공을 뒤집지 않도록 정리했다.
- `attachment-service`에 `AttachmentServiceImpl`, `LocalFileStore`, `JpaFileStore` 회귀 테스트를 추가해 unknown-size stream 처리, explicit size 유지, filesystem/database 저장 경로를 검증했다.

### 검증
- `./gradlew :studio-application-modules:attachment-service:test --tests 'studio.one.application.attachment.service.AttachmentServiceImplTest' --tests 'studio.one.application.attachment.storage.LocalFileStoreTest' --tests 'studio.one.application.attachment.storage.JpaFileStoreTest'`
- `./gradlew :studio-application-modules:attachment-service:compileJava`

## 2026-03-31

### 변경됨
- `attachment-service`의 `AttachmentController`, `AttachmentMgmtController`, `MeAttachmentController`가 파일명 정제, MIME 정규화, 다운로드 헤더 구성을 공통 `AttachmentWebSupport`로 공유하도록 정리했다.
- `AttachmentMgmtController`의 관리자 판별이 `ADMIN`과 `ROLE_ADMIN`을 모두 허용하도록 보강해 Spring Security authority 표현 차이로 인한 owner 우회 오판정을 줄였다.
- `attachment-service`에 attachment 웹 helper 회귀 테스트를 추가하고, mgmt 권한 테스트가 `ROLE_ADMIN` 경로를 검증하도록 보강했다.
- `attachment-service`의 접근 제어 helper를 `AttachmentAccessSupport`로 분리해 principal 조회, 관리자 판별, owner 접근 검사를 컨트롤러에서 공통으로 사용하도록 정리했다.

### 검증
- `./gradlew :studio-application-modules:attachment-service:test --tests 'studio.one.application.web.controller.AttachmentAccessSupportTest' --tests 'studio.one.application.web.controller.AttachmentControllerTest' --tests 'studio.one.application.web.controller.AttachmentMgmtControllerAuthorizationTest' --tests 'studio.one.application.web.controller.AttachmentWebSupportTest' --tests 'studio.one.application.web.controller.MeAttachmentControllerTest'`
- `./gradlew :studio-application-modules:attachment-service:compileJava`

## 2026-03-30

### 변경됨
- `PropertyValidator`가 점(`.`)과 하이픈(`-`)이 포함된 민감 프로퍼티 키도 감지하도록 수정했다.
- `RepositoryImpl`에 경로 탐색 방어를 추가하고, startup refresh 이벤트가 즉시 `UnsupportedOperationException`으로 실패하지 않도록 정리했다.
- `GlobalExceptionHandler`가 unsupported method/media type 예외를 각각 405/415로 응답하도록 수정했다.
- `JasyptHttpController`의 토큰 검증을 상수 시간 비교로 바꾸고, `JasyptProperties`에 암호화 비밀번호 최소 길이 검증을 추가했다.
- `studio-platform`과 `starter-jasypt`에 회귀 테스트를 추가하고, 테스트용 웹 의존성을 보강했다.

### 검증
- `./gradlew :studio-platform:test --tests 'studio.one.platform.component.PropertyValidatorTest' --tests 'studio.one.platform.component.RepositoryImplTest' --tests 'studio.one.platform.web.advice.GlobalExceptionHandlerTest'`
- `./gradlew :starter:studio-platform-starter-jasypt:test --tests 'studio.one.platform.autoconfigure.jasypt.JasyptHttpControllerTest' --tests 'studio.one.platform.autoconfigure.jasypt.JasyptPropertiesTest'`

## 2026-03-30 (follow-up)

### 변경됨
- `studio-platform-autoconfigure`의 `CompositeAuditorAware`가 외부에서 주입한 `AuditorAware`를 우선 처리할 수 있도록 확장했다.
- `CompositeAuditorAware`의 기존 security/header/fixed 기본 합성 동작은 유지했다.
- `studio-platform-autoconfigure`에 `CompositeAuditorAware` 회귀 테스트와 테스트용 `spring-data-commons` 의존성을 추가했다.
- 루트 OWASP dependency-check의 `failBuildOnCVSS`를 `7.0F`로 낮춰 High 이상 취약점에서 빌드가 실패하도록 조정했다.
- `studio-platform-data`에 `PaginationDialect` 회귀 테스트를 추가했다.
- `studio-platform`에 `DomainPolicyRegistryImpl` 병합/정규화 회귀 테스트를 추가하고, contributor 병합 시 불변 맵을 다시 수정하던 경로를 안전하게 고쳤다.
- `starter`의 `perisitence`와 `studio-platform-autoconfigure`의 `perisistence` 오타 패키지에 대응해 정상 패키지명 `persistence` 경로를 추가하고, 기존 경로는 deprecated 호환 브리지로 유지했다.
- Spring Boot auto-configuration 등록 경로를 `persistence` 패키지로 전환했다.
- `studio-platform-identity` 계약에 principal/resolver 규약과 `UserDto` 용도를 문서화하고, identity service bean 이름 상수를 별도 상수 클래스로 분리했다.
- `studio-platform-identity`를 순수 계약 모듈로 유지하도록 Spring Boot 플러그인을 제거했다.

### 검증
- `./gradlew :studio-platform-autoconfigure:test --tests 'studio.one.platform.autoconfigure.perisistence.jpa.auditor.CompositeAuditorAwareTest'`
- `./gradlew :studio-platform:test --tests 'studio.one.platform.security.authz.DomainPolicyRegistryImplTest'`
- `./gradlew :studio-platform-data:test --tests 'studio.one.platform.data.jdbc.pagination.PaginationDialectTest'`
- `./gradlew :studio-platform-autoconfigure:test --tests 'studio.one.platform.autoconfigure.persistence.jpa.auditor.CompositeAuditorAwareTest'`
- `./gradlew :starter:studio-platform-starter:compileJava`
- `./gradlew :studio-platform-identity:test`
- `./gradlew :studio-platform-identity:build`

## 2026-03-31

### 변경됨
- `studio-platform-objecttype`의 `ObjectTypeRuntimeService`와 `ObjectTypeAdminService`가 `web.dto` 대신 서비스 전용 command/result 타입을 사용하도록 정리했다.
- `ObjectTypeController`와 `ObjectTypeMgmtController`가 서비스 모델을 기존 웹 DTO로 매핑하도록 책임을 이동해 HTTP 응답 형식은 유지했다.
- `attachment-service`의 objecttype 업로드 정책 검증 호출이 서비스 전용 `ValidateUploadCommand`를 사용하도록 변경했다.
- `studio-platform-objecttype`에 runtime 성공 경로와 controller 매핑 회귀 테스트를 추가했다.

### 검증
- `./gradlew :studio-platform-objecttype:test --tests 'studio.one.platform.objecttype.ObjectTypeRuntimeServiceTest' --tests 'studio.one.platform.objecttype.ObjectTypeControllerTest' --tests 'studio.one.platform.objecttype.ObjectTypeMgmtControllerTest'`
- `./gradlew :studio-application-modules:attachment-service:test --tests 'studio.one.application.attachment.service.AttachmentServiceImplTest' --tests 'studio.one.application.web.controller.AttachmentMgmtControllerAuthorizationTest'`
- `./gradlew :studio-application-modules:attachment-service:compileJava :starter:studio-platform-starter-objecttype:compileJava`

## 2026-03-26

### 변경됨
- `studio-platform-starter-ai`의 provider 의존성(OpenAI, Google GenAI, Ollama)을 `implementation`에서 `compileOnly`로 전환했다. 소비 애플리케이션이 필요한 provider 라이브러리를 직접 선언해야 한다.
- Spring AI BOM을 `api(platform(...))`으로 노출하여 소비 앱이 별도 BOM 선언 없이 Spring AI 버전을 일관되게 관리할 수 있도록 했다.
- `ProviderChatPortFactory` / `ProviderEmbeddingPortFactory` 인터페이스와 provider별 `@Configuration` 구현체를 도입했다. 각 구현체는 `@ConditionalOnClass`로 보호되어, provider 라이브러리가 classpath에 있을 때만 해당 factory가 등록된다.
- `ProviderChatConfiguration` / `ProviderEmbeddingConfiguration`의 switch 기반 직접 참조를 제거하고, 등록된 factory를 수집하는 방식으로 교체했다. factory가 없는 provider는 조용히 제외된다.
- `AiSecretPresenceGuard`에서 `ChatModel` / `EmbeddingModel` bean 주입을 제거했다. property 기반 검증만 유지한다.
- `AiProviderRegistryConfiguration`에 fail-fast guard를 추가했다. `studio.ai.default-provider`에 지정된 provider가 chat port와 embedding port 모두에 없으면 시작 시점에 명확한 오류로 실패한다.

### 사용 방법 (OpenAI 예시)
```kotlin
// build.gradle.kts
implementation("studio-platform-starter-ai")
implementation("org.springframework.ai:spring-ai-starter-model-openai")
```
```yaml
# application.yml
studio:
  ai:
    enabled: true
    default-provider: openai
    providers:
      openai:
        type: OPENAI
        chat:
          enabled: true
        embedding:
          enabled: true
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat.options.model: gpt-4o-mini
      embedding.options.model: text-embedding-3-small
```

### 검증
- `./gradlew :starter:studio-platform-starter-ai:build`

## 2026-03-23

### 변경됨
- JWT refresh cookie가 설정된 cookie name/path/SameSite/Secure 값을 따르도록 수정했다.
- realtime STOMP 기본값을 same-origin, JWT 요구, 익명 연결 거부 방향으로 강화했다.
- 파일 텍스트 추출에 기본 10MB 상한을 추가해 과도한 메모리 적재를 막았다.
- realtime JWT 의존성 누락을 startup 단계에서 fail-fast 처리하고, text extraction 상한 설정을 바인딩 단계에서 검증하도록 보강했다.

### 검증
- `./gradlew -PnimbusJoseJwtVersion=9.37.3 -PjsonSmartVersion=2.5.2 :studio-platform-security:test --tests 'studio.one.base.security.web.controller.JwtCookieSettingsTest' :studio-platform-realtime:test --tests 'studio.one.platform.realtime.stomp.config.RealtimeStompPropertiesTest' :studio-platform-data:test --tests 'studio.one.platform.text.service.FileContentExtractionServiceTest' ':studio-application-modules:attachment-service:test' --tests 'studio.one.application.web.controller.AttachmentMgmtControllerAuthorizationTest' :starter:studio-platform-starter-realtime:compileJava`

## 2026-03-24

### 변경됨
- `starter:studio-platform-starter-ai`에 Spring AI OpenAI starter 기반 스파이크를 추가하고, OpenAI 직접 모델 생성 대신 Spring AI auto-configuration bean을 alias port에 연결하도록 정리했다.
- `studio.ai.spring-ai.source-provider`와 fail-fast guard를 추가해 Spring AI alias가 명시된 OpenAI provider와 `spring.ai.openai.*` 설정을 사용하도록 고정했다.
- source provider로 지정된 OpenAI의 LangChain base 경로도 `spring.ai.openai.*`를 사용하도록 바꿔, OpenAI runtime 설정의 단일 소스를 유지한 채 LangChain/Spring AI 비교가 가능하게 했다.
- `openai-springai` default cutover 검증을 위해 `AiInfoController`, `ChatController`, `EmbeddingController` smoke 테스트를 추가했다.
- `studio.ai.default-provider`를 비웠을 때 Spring AI alias를 기본 provider로 승격하고, `default-provider=openai`를 명시하면 LangChain base provider로 rollback할 수 있게 정리했다.
- OpenAI provider를 Spring AI 단일 경로로 정리하고, `openai-springai` alias 및 LangChain OpenAI base path 제거 방향을 [spring-ai-openai.md](/Users/donghyuck.son/git/studio-api/docs/dev/spring-ai-openai.md)에 문서화했다.

### 검증
- `./gradlew -PnimbusJoseJwtVersion=9.37.3 -PjsonSmartVersion=2.5.2 :starter:studio-platform-starter-ai:test --tests 'studio.one.platform.ai.autoconfigure.AiSecretPresenceGuardTest' --tests 'studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapterTest' --tests 'studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapterTest' --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderRegistrationTest' --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderAutoConfigurationTest'`
# 2026-03-24

- refactor(ai): start splitting AI HTTP endpoints into a dedicated web starter module.
- refactor(ai): remove remaining core bean stereotypes so AI service ownership stays in starter auto-configuration.
- refactor(ai): narrow AI dependency ownership so web/security concerns stay with the web starter boundary.
- refactor(ai): replace AI starter component scanning with explicit auto-configuration bean registration.
- refactor(ai): prune unused compileOnly Spring starter dependencies from `studio-platform-starter-ai`.
- refactor(ai): remove LangChain4j `TokenUsage` coupling from ai-web starter and normalize chat `tokenUsage` metadata shape.
- refactor(ai): migrate Ollama embedding wiring from LangChain4j to Spring AI and validate `spring.ai.ollama.embedding.options.model` at startup.
- refactor(ai): migrate Google embedding wiring from LangChain4j to Spring AI and validate `spring.ai.google.genai.embedding.*` at startup.
- refactor(ai): preserve Google embedding `taskType` during the Spring AI migration; `titleMetadataKey` remains inactive because the current embedding request model carries text only.
- refactor(ai): remove the remaining LangChain4j embedding adapter and dead embedding wiring, keeping LangChain4j only for the Google chat path.
- refactor(ai): migrate Google chat wiring from LangChain4j to Spring AI and remove the remaining LangChain4j chat adapter path.
- fix(ai): preserve custom Google chat base URL when building the Spring AI Google GenAI client.
- refactor(ai): rename provider wiring configurations to neutral names after removing LangChain4j runtime paths.
