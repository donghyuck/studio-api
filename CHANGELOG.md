# Changelog

## 2026-04-28

### 변경됨
- 이슈 #354 대응으로 namespace migration 문서를 `spring.*`, `studio.features.<module>.*`, `studio.<module>.*`의 3층 모델 기준으로 정리했다.
- `README.md`, `starter/README.md`, `starter/STARTER_GUIDE.md`, 모듈/스타터 README의 configuration 예시를 새 키 기준으로 정리하고, legacy fallback은 migration note로만 남겼다.
- `studio-platform-starter-user`, `studio-application-starter-attachment`, `studio-application-starter-mail`, `studio-platform-starter-ai`에 additional Spring configuration metadata를 추가해 legacy key deprecation과 대표 target property를 노출했다.
- `.env.example`와 AI 테스트용 sample application.yml을 현재 Google GenAI 환경 변수와 AI namespace 기준에 맞게 정리했다.

## 2026-04-26

### 변경됨
- 이슈 #333 대응으로 `content-embedding-pipeline`이 `AttachmentRagIndexService`와 attachment source executor를 auto-configuration으로 등록해 attachment RAG controller 생성 시 service bean 누락이 발생하지 않도록 했다.
- 이슈 #329/#330/#331 대응으로 RAG job JDBC repository opt-in, chunk page 조회 API, job HTTP 상태 smoke 테스트를 보강하고 job 생성/retry 권한을 write 기준으로 정리했다.
- 이슈 #327 대응으로 AI client update guide의 RAG chat attachment-only 제약, job 조회 권한, in-memory job 404 처리, chunk 조회 limit 안내를 보완했다.
- 이슈 #325 대응으로 AI client update guide의 RAG job 운영 화면 API 목록과 polling/retry/cancel 흐름을 최신화했다.
- 이슈 #323 대응으로 RAG index job cancel API(`POST /api/mgmt/ai/rag/jobs/{jobId}/cancel`)를 추가했다.
- `RagIndexJobService.cancelJob(jobId)` 계약과 `JOB_CANCELLED` 로그 코드를 추가하고, 기본 in-memory job service/repository가 취소 상태를 late progress callback으로 덮어쓰지 않도록 보강했다.
- 이슈 #321 대응으로 RAG job 목록 조회에 `sort`/`direction` 요청 계약을 추가하고 기본 정렬을 `createdAt desc`로 명시했다.
- 이슈 #319 대응으로 `RagIndexJobSourceExecutor` 계약을 추가하고, `sourceType=attachment` RAG job이 기존 attachment RAG 색인 흐름을 비동기로 실행하도록 연결했다.
- `POST /api/mgmt/ai/rag/jobs`는 raw `text` 없이 source 기반 요청을 받을 수 있으며, `content-embedding-pipeline`은 attachment source executor를 제공한다.
- 이슈 #317 대응으로 RAG 색인 작업 management API와 in-memory job repository/service 계약을 추가했다.
- `POST /api/mgmt/ai/rag/index`와 attachment RAG index API는 기존 empty `202 Accepted` 응답을 유지하면서 `X-RAG-Job-Id` 헤더를 additive하게 반환한다.
- RAG 색인 진행 단계(`EXTRACTING`, `CHUNKING`, `EMBEDDING`, `INDEXING`, `COMPLETED`)와 chunk/embedding/index count, warning/error log를 조회할 수 있도록 했다.
- 이슈 #313 대응으로 RAG metadata key reference를 `studio-platform-ai` README에 통합하고 관련 모듈 README에서 참조하도록 문서화했다.
- 이슈 #312 대응으로 AI web vector 검색 경로가 내부적으로 `VectorSearchResults`/`VectorSearchHit` aggregate 계약을 사용하도록 연결했다.
- `POST /api/mgmt/ai/vectors/search` 요청에서 `includeText`/`includeMetadata`를 optional field로 받아 core `VectorSearchRequest`에 전달한다.
- 이슈 #311 대응으로 `DefaultRagPipelineService`의 기본 RAG indexing 저장 경로를 `VectorRecord.builder()`와 `VectorStorePort.upsertAll(...)`/`replaceRecordsByObject(...)` 사용으로 전환했다.
- `VectorRecord` metadata pass-through가 blank string 값을 보존하도록 해 기존 `cleanerPrompt=""` metadata 호환성을 유지했다.
- 이슈 #309 대응으로 `VectorSearchHit.from(...)`이 문자열 숫자 `page`/`slide`와 iterable `headingPath`/`sourceRef` metadata를 안정적으로 변환하도록 보강했다.
- 이슈 #305 대응으로 첨부 RAG 색인과 web RAG context expansion에 opt-in diagnostics를 추가했다.
- `POST /api/mgmt/attachments/{id}/rag/index`는 서버 `allow-client-debug=true`와 요청 `debug=true`가 모두 만족될 때 안전한 `X-RAG-Index-*` 헤더로 구조화/fallback 경로와 count 정보를 노출한다.
- `POST /api/ai/chat/rag`는 서버 `allow-client-debug=true`와 요청 `debug=true`가 모두 만족될 때 `metadata.ragContextDiagnostics`에 context expansion 적용 상태를 노출한다.
- 이슈 #304 대응으로 `content-embedding-pipeline`의 구조화 attachment RAG 색인 경로가 `VectorRecord.builder()`와 `VectorStorePort.replaceRecordsByObject(...)`를 사용하도록 전환됐다.
- `VectorStorePort`에 record 기반 object-scope replace default adapter를 추가해 기존 `VectorDocument` 기반 store 구현과 호환되도록 했다.
- 이슈 #303 대응으로 `starter-ai-web`의 RAG context expansion을 `studio.ai.endpoints.rag.context.expansion.*` 설정으로 제어할 수 있도록 했다.
- context expansion candidate 조회 배수, 후보 조회 상한, previous/next window, parent content 포함 여부를 설정으로 분리했다.

### 검증
- `./gradlew :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test`
- `./gradlew :studio-platform-ai:compileJava :starter:studio-platform-starter-ai:compileJava :starter:studio-platform-starter-ai-web:compileJava :studio-application-modules:content-embedding-pipeline:compileJava`
- `./gradlew :studio-platform-ai:testClasses :starter:studio-platform-starter-ai:testClasses :starter:studio-platform-starter-ai-web:testClasses :studio-application-modules:content-embedding-pipeline:testClasses`
- `./gradlew :studio-platform-ai:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test`
- `./gradlew :studio-platform-ai:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test`
- `git diff --check`
- `./gradlew :starter:studio-platform-starter-ai:test :studio-platform-ai:test`
- `./gradlew :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test`
- `./gradlew :studio-platform-ai:test :studio-application-modules:content-embedding-pipeline:test :starter:studio-platform-starter-ai:test`
- `./gradlew :starter:studio-platform-starter-ai-web:test`
- `git diff --check`

## 2026-04-25

### 변경됨
- 이슈 #290 대응으로 `starter-ai-web`의 `RagContextBuilder`가 optional `ChunkContextExpander`를 사용해 object-scoped RAG 검색 결과의 parent/neighbor/table 문맥을 확장할 수 있도록 했다.
- RAG chat context 확장 후에도 기존 `max-chunks`, `max-chars`, `include-scores` 제한을 유지하고, expander 또는 metadata가 없으면 기존 retrieval hit content 조립 경로를 유지한다.
- 이슈 #299/#300 대응으로 `starter-ai` README에 legacy RAG chunk 설정 migration guide를 추가했다.
- `studio.ai.pipeline.chunk-size`와 `studio.ai.pipeline.chunk-overlap`는 deprecated `TextChunker` fallback 전용 설정으로 표시하고, 기존 binding 호환성 테스트를 보강했다.
- 이슈 #297 대응으로 `starter-ai`의 기본 `TextChunker` bean 생성을 `ChunkingOrchestrator`가 없을 때의 legacy fallback으로 제한했다.
- `RagPipelineService` auto-configuration은 `TextChunker`를 optional로 받아 orchestrator-only 환경에서도 동작하도록 했다.
- `AiAutoConfiguration`은 `ChunkingAutoConfiguration` 이후 평가되도록 정렬해 chunking starter가 제공하는 `ChunkingOrchestrator`를 우선 감지한다.
- 이슈 #295 대응으로 `starter-ai`의 RAG indexing chunking 분기를 `RagChunker` adapter로 분리했다.
- `DefaultRagPipelineService`는 `ChunkingOrchestrator` 우선 경로와 deprecated `TextChunker` fallback 변환을 직접 다루지 않고 adapter 결과만 사용하도록 정리했다.
- 이슈 #293 대응으로 `studio-platform-ai`의 `TextChunk`/`TextChunker`와 `starter-ai`의 `OverlapTextChunker`를 deprecated legacy fallback으로 표시했다.
- 신규 RAG chunking은 `studio-platform-chunking`의 `ChunkingOrchestrator`를 기준으로 사용하도록 README를 정리했다.
- 이슈 #188 대응으로 PostgreSQL 그룹 멤버 summary 검색에 `pg_trgm` 기반 `lower(username|name|email)` GIN trigram index migration을 추가했다.
- MySQL/MariaDB에는 PostgreSQL 전용 검색 최적화와 schema version 이력을 맞추기 위한 V301 schema-neutral migration을 추가했다.
- 그룹 멤버 summary 검색에서 blank keyword를 null keyword와 동일하게 전체 조회로 처리하도록 service/JPA repository 경로를 정리했다.
- 그룹 멤버 summary repository 검색 테스트에 null, blank, username/name/email 매칭 케이스를 추가했다.
- 이슈 #281 대응으로 `studio-platform-chunking`과 `starter:studio-platform-starter-chunking` README를 한국어 기준으로 현행화했다.
- chunk metadata key reference, parent-child 모델, context expansion 계약, textract 연계, 하위 호환성 기준을 문서화했다.
- README 예시와 실제 public API가 어긋나지 않도록 parent-child chunking, context expansion, text fallback 문서 시나리오 테스트를 추가했다.

### 검증
- `./gradlew :studio-platform-chunking:test`
- `./gradlew :starter:studio-platform-starter-ai:test`
- `./gradlew :studio-platform-ai:test :starter:studio-platform-starter-chunking:test`
- `./gradlew :studio-platform-user:test --tests 'studio.one.base.user.persistence.jpa.ApplicationGroupMembershipJpaRepositorySearchTest' --tests 'studio.one.base.user.persistence.jdbc.ApplicationGroupMembershipJdbcRepositoryTest'`
- `./gradlew :studio-platform-chunking:test :starter:studio-platform-starter-chunking:test`

## 2026-04-24

### 변경됨
- 이슈 #235 대응으로 `HtmlFileParser.parse()`가 기존 `Jsoup.text()` 기반 plain text 전용 추출에서 `parseStructured(...).plainText()` 경로를 사용하도록 변경됐다.
- HTML 추출은 `script`, `style`, `nav`, `aside`, `footer`, `form` 등 boilerplate 후보를 제거하고 `h1`-`h6`, `p`, `li`, `table`, `img` 기반 semantic block을 구성한다.
- PDF/PPTX/HTML 파서에 구조화 block/provenance 추출을 추가하고, DOCX footnote/list 및 OCR line block metadata를 보강했다.

### 검증
- `./gradlew :studio-platform-textract:test`
- `./gradlew :studio-platform-textract:build`

## 2026-04-20

### 변경됨
- `studio-platform-data`의 문서/텍스트 추출 계약과 구현을 `studio-platform-textract` 모듈로 분리했다.
- 텍스트 추출 자동설정을 `starter:studio-platform-textract-starter`로 분리하고, 기존 `studio.features.text` 설정 prefix는 유지했다.
- 기존 `studio.one.platform.text.*` API는 `studio-platform-data`의 deprecated wrapper로 유지하고, 내부 attachment/RAG 사용처는 `studio.one.platform.textract.*`를 직접 사용하도록 전환했다.
- `StructuredFileParser`, `ParsedFile`, `ParsedBlock`, `BlockType`, `ParseWarning`을 추가해 HWP/HWPX와 OCR 확장을 위한 RAG 친화 구조화 파싱 계약을 마련했다.
- `rhwp` 분석 결과를 참고해 HWPX 문단·표·이미지와 HWP BodyText/BinData 추출 parser를 추가했다.
- 후속 정리로 `starter:studio-platform-starter`의 legacy text auto-configuration과 `studio-platform-data`의 포맷별 parser wrapper를 제거하고, data에는 최소 facade만 남겼다.

### 검증
- `./gradlew :studio-platform-textract:test :studio-platform-data:test :starter:studio-platform-textract-starter:test :starter:studio-platform-starter:test :studio-application-modules:attachment-service:test :studio-application-modules:content-embedding-pipeline:test`
- `gradle :studio-platform-data:test :starter:studio-platform-starter:test :starter:studio-platform-textract-starter:test`
- `git diff --check`

## 2026-04-17

### 변경됨
- `POST /api/ai/chat`와 `POST /api/ai/chat/rag`에 요청 단위 opt-in chat memory를 추가했다.
- 기본 동작은 stateless로 유지하고, `studio.ai.endpoints.chat.memory.enabled=true`와 요청 `memory.enabled=true`가 모두 설정된 경우에만 `conversationId`별 최근 메시지를 in-memory로 보관한다.
- chat memory는 `max-messages`, `max-conversations`, `ttl` 상한을 적용하며, 재시작 시 소실되고 다중 인스턴스 간 공유되지 않는 제약을 README에 문서화했다.
- 응답 metadata에 memory 사용 여부와 conversation 상태를 노출하도록 했다.

### 검증
- `./gradlew :studio-platform-ai:test :starter:studio-platform-starter-ai-web:test`

## 2026-04-16

### 변경됨
- 최근 AI/RAG 모듈 변경에 따른 클라이언트 수정 항목을 `docs/dev/ai-client-update-guide.md`에 정리하고 README 문서 목록에 추가했다.
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
