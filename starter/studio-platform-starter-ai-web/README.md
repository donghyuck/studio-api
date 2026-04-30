# studio-platform-starter-ai-web

AI 기능을 HTTP 엔드포인트로 노출하는 웹 스타터이다.
`studio-platform-starter-ai`와 분리되어 있으며, AI 코어 기능은 유지하면서 REST API 노출 여부를
별도로 제어할 수 있다. 채팅, 임베딩, 벡터 스토어, RAG 파이프라인, 쿼리 리라이트, AI 정보 조회
엔드포인트를 자동 등록한다.
이 스타터는 web adapter 역할만 담당하며 chunking strategy, document parsing, embedding batch
orchestration, vector persistence 구현을 추가하지 않는다.
RAG chunking 실행은 `starter:studio-platform-starter-ai`의 `RagPipelineService`와 선택적
`starter:studio-platform-starter-chunking`에 위임한다.

## 1) 의존성 추가

AI 코어 스타터와 웹 스타터를 함께 선언한다. 프로바이더 라이브러리도 소비 앱에서 직접 선언해야 한다.

```kotlin
dependencies {
    // AI 코어 자동 구성 (필수)
    implementation(project(":starter:studio-platform-starter-ai"))
    // AI 웹 엔드포인트 (이 스타터)
    implementation(project(":starter:studio-platform-starter-ai-web"))

    // REST 엔드포인트 활성화에 필요
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 사용할 프로바이더 라이브러리 선언 (예: OpenAI)
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
}
```

## 2) 기능 활성화

AI 코어(`studio.features.ai.enabled`)가 `true`여야 웹 엔드포인트도 활성화된다.
웹 엔드포인트 전체는 `studio.ai.endpoints.enabled`로 제어한다.

```yaml
studio:
  features:
    ai:
      enabled: true
  ai:
    routing:
      default-chat-provider: openai
      default-embedding-provider: openai
    endpoints:
      enabled: true              # AI web endpoint 전체 활성화 여부 (기본: true)
      base-path: /api/ai         # 사용자용 AI endpoint 기본 경로
      mgmt-base-path: /api/mgmt/ai # 관리용 AI endpoint 기본 경로
    providers:
      openai:
        type: OPENAI
        enabled: true
        chat:
          enabled: true
        embedding:
          enabled: true
```

## 3) REST 엔드포인트

사용자용 기본 base-path는 `/api/ai`이며 `studio.ai.endpoints.base-path`로 변경할 수 있다.
관리용 기본 base-path는 `/api/mgmt/ai`이며 `studio.ai.endpoints.mgmt-base-path`로 변경할 수 있다.
기존 `/api/ai/embedding`, `/api/ai/vectors`, `/api/ai/rag` 경로를 유지해야 하는 환경은
`studio.ai.endpoints.mgmt-base-path=/api/ai`로 설정한다.

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| `POST` | `{basePath}/chat` | 채팅 완성 요청 | `services:ai_chat write` |
| `POST` | `{basePath}/chat/stream` | SSE 채팅 스트림 | `services:ai_chat write` |
| `POST` | `{basePath}/chat/rag` | RAG 컨텍스트 주입 후 채팅 | `services:ai_chat write`, `services:ai_rag read`, object scope read |
| `GET` | `{basePath}/chat/conversations` | conversation 목록 조회 | `services:ai_chat read` |
| `GET` | `{basePath}/chat/conversations/{conversationId}` | conversation 상세 및 메시지 조회 | `services:ai_chat read` |
| `DELETE` | `{basePath}/chat/conversations/{conversationId}` | conversation 삭제 | `services:ai_chat write` |
| `POST` | `{basePath}/chat/regenerate` | 마지막 user turn 기준 assistant 응답 재생성 | `services:ai_chat write` |
| `POST` | `{basePath}/chat/truncate` | 특정 메시지 이후 conversation 절단 | `services:ai_chat write` |
| `POST` | `{basePath}/chat/fork` | 특정 메시지까지 새 conversation으로 fork | `services:ai_chat write` |
| `POST` | `{basePath}/chat/compact` | conversation summary 저장 및 compact 상태 표시 | `services:ai_chat write` |
| `POST` | `{basePath}/chat/cancel` | conversation cancel 상태 표시 | `services:ai_chat write` |
| `POST` | `{basePath}/query-rewrite` | 검색 쿼리 리라이트 | `services:ai_chat read` |
| `GET`  | `{basePath}/info/providers` | 프로바이더 및 벡터 스토어 상태 조회 | `services:ai_chat read` 또는 `services:ai_embedding read` |
| `POST` | `{mgmtBasePath}/embedding` | 텍스트 임베딩 벡터 생성 | `services:ai_embedding write` |
| `POST` | `{mgmtBasePath}/vectors` | 벡터 문서 업서트 | `services:ai_vector read` |
| `POST` | `{mgmtBasePath}/vectors/search` | 벡터 유사도 검색 | `services:ai_vector read` |
| `POST` | `{mgmtBasePath}/vectors/projections` | 벡터 2D projection 생성 job 요청 | `services:ai_vector admin` |
| `GET` | `{mgmtBasePath}/vectors/projections` | 벡터 projection 목록 조회 | `services:ai_vector read` |
| `GET` | `{mgmtBasePath}/vectors/projections/{projectionId}` | 벡터 projection 상세 조회 | `services:ai_vector read` |
| `GET` | `{mgmtBasePath}/vectors/projections/{projectionId}/points` | 산점도 렌더링용 projection point 조회 | `services:ai_vector admin` |
| `GET` | `{mgmtBasePath}/vectors/items/{vectorItemId}` | 벡터 항목 상세 조회 | `services:ai_vector admin` |
| `POST` | `{mgmtBasePath}/vectors/search-visualization` | 검색어 기반 projection highlight 좌표 조회 | `services:ai_vector admin` |
| `POST` | `{mgmtBasePath}/rag/index` | 문서 RAG 인덱싱 | `services:ai_rag read` |
| `POST` | `{mgmtBasePath}/rag/search` | RAG 시맨틱 검색 | `services:ai_rag read` |
| `GET` | `{mgmtBasePath}/rag/jobs` | RAG 색인 job 목록 조회 | `services:ai_rag read` |
| `GET` | `{mgmtBasePath}/rag/jobs/{jobId}` | RAG 색인 job 상세 조회 | `services:ai_rag read` |
| `POST` | `{mgmtBasePath}/rag/jobs` | RAG 색인 job 생성 및 비동기 실행 | `services:ai_rag write` |
| `POST` | `{mgmtBasePath}/rag/jobs/{jobId}/retry` | 실패 또는 완료 job 재시도 요청 | `services:ai_rag write` |
| `POST` | `{mgmtBasePath}/rag/jobs/{jobId}/cancel` | 진행 중인 job 취소 상태 표시 | `services:ai_rag write` |
| `GET` | `{mgmtBasePath}/rag/jobs/{jobId}/logs` | 단계별 색인 로그 조회 | `services:ai_rag read` |
| `GET` | `{mgmtBasePath}/rag/jobs/{jobId}/chunks` | job의 object scope 기준 chunk 조회 | `services:ai_rag read` |
| `GET` | `{mgmtBasePath}/rag/jobs/{jobId}/chunks/page` | job의 object scope 기준 chunk 페이지 조회 | `services:ai_rag read` |
| `GET` | `{mgmtBasePath}/rag/objects/{objectType}/{objectId}/chunks` | object scope 기준 chunk 조회 | `services:ai_rag read` |
| `GET` | `{mgmtBasePath}/rag/objects/{objectType}/{objectId}/chunks/page` | object scope 기준 chunk 페이지 조회 | `services:ai_rag read` |
| `GET` | `{mgmtBasePath}/rag/objects/{objectType}/{objectId}/metadata` | object scope 기준 RAG metadata 조회 | `services:ai_rag read` |
| `POST` | `{mgmtBasePath}/rag/chunks/preview` | 색인 전 text chunk preview | `services:ai_rag read` |
| `GET` | `{mgmtBasePath}/rag/chunks/config` | chunk/RAG 설정 조회 | `services:ai_rag read` |

> `studio.ai.endpoints.enabled=false`이면 위 AI web endpoint 전체가 등록되지 않는다.

### Vector Projection Visualization

관리자 화면에서 기존 `tb_ai_document_chunk` 벡터를 2D 산점도로 표시할 수 있도록 projection API를 제공한다.
원본 embedding 테이블은 변경하지 않고, `tb_ai_vector_projection`과 `tb_ai_vector_projection_point`에
projection job 상태와 미리 계산된 좌표만 저장한다. 화면 요청 시마다 고차원 벡터를 다시 projection하지 않는다.

기본 알고리즘은 `PCA`다. v1 구현은 Java 내장 연산으로 PCA 좌표를 계산하고, 후속 UMAP/t-SNE는
`VectorProjectionGenerator` 구현을 추가해 확장한다. `targetTypes`가 비어 있으면 전체 vector item을 대상으로 한다.
`filters`는 v1에서 metadata equality 조건만 사용하며 null 값은 무시한다. 한 projection job은 최대
1,000개 vector item, 2,048 embedding dimension까지 처리한다. 더 큰 범위는 `targetTypes`나 metadata filter로 나눠 생성한다.
projection 생성과 point/item/search visualization 조회는 object별 ACL을 행마다 평가하지 않는 corpus-level 관리 API이므로
`services:ai_vector admin` 권한이 필요하다.

Projection 생성:

```http
POST /api/mgmt/ai/vectors/projections
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "NCS-과정-청크 벡터맵",
  "targetTypes": ["NCS_UNIT", "COURSE", "COURSE_CHUNK"],
  "algorithm": "PCA",
  "filters": {
    "useYn": "Y"
  }
}
```

응답은 즉시 `REQUESTED`를 반환한다. 서버는 비동기 job에서 `PROCESSING`으로 전환한 뒤 기존
`tb_ai_document_chunk`의 embedding을 읽어 좌표를 만들고, 기존 point를 삭제 후 재생성한다.
완료 시 `COMPLETED`, 실패 시 `FAILED`와 `errorMessage`를 저장한다.

```json
{
  "data": {
    "projectionId": "proj-20260430010000-a1b2c3d4",
    "status": "REQUESTED",
    "message": "벡터 시각화 좌표 생성 작업이 요청되었습니다."
  }
}
```

산점도 point 조회:

```http
GET /api/mgmt/ai/vectors/projections/{projectionId}/points?targetType=COURSE_CHUNK&keyword=java&limit=2000&offset=0
Authorization: Bearer <token>
```

`limit` 기본값은 2000, 최대값은 5000이다. projection 상태가 `COMPLETED`가 아니면
`409 Conflict`와 `PROJECTION_NOT_READY` detail을 반환하므로 클라이언트는 목록/상세 API를 polling하다가
완료 후 point를 요청해야 한다. point 응답은 산점도 렌더링에 필요한 `vectorItemId`, `targetType`,
`sourceId`, `label`, `x`, `y`, `clusterId`, `metadata`를 함께 제공한다. 응답 metadata에서는
embedding, 원문 text/content, keyword text 같은 대용량/민감 가능성이 큰 필드는 반환하지 않고,
표시용 allowlist metadata만 반환한다.

검색어 기반 시각화:

```http
POST /api/mgmt/ai/vectors/search-visualization
Authorization: Bearer <token>
Content-Type: application/json

{
  "projectionId": "proj-20260430010000-a1b2c3d4",
  "query": "자바 백엔드 개발자가 되고 싶어",
  "targetTypes": ["NCS_UNIT", "COURSE", "COURSE_CHUNK"],
  "topK": 10
}
```

이 API는 기존 `EmbeddingPort`와 `VectorStorePort` 검색을 재사용한다. 검색 결과의 `vectorItemId`와
projection point를 매칭하고, query 위치는 매칭된 Top-K point 좌표의 평균으로 계산한다.
매칭 point가 없으면 `query.x`, `query.y`는 `null`, `results`는 빈 배열로 200 응답한다.
검색은 선택된 projection의 `targetTypes`와 `filters` 범위를 기준으로 제한하고, 요청 `targetTypes`가 있으면
projection 범위와 교집합인 type만 대상으로 한다. `query`는 provider 비용과 지연을 제한하기 위해 최대
2,000자까지 허용한다.

### RAG Index Job Management

RAG 운영 화면은 신규 job API를 사용해 색인 실행 상태, 단계별 로그, 색인된 chunk를 조회할 수 있다.
기존 `POST {mgmtBasePath}/rag/index` 테스트 API는 응답 body 없이 `202 Accepted`를 유지하며,
job 추적이 활성화된 경우 `X-RAG-Job-Id` 헤더만 추가한다. 신규 `POST {mgmtBasePath}/rag/jobs`는
raw `text`가 있으면 같은 `RagPipelineService`를 in-memory job service로 감싸 비동기 실행하고,
응답 body에 생성된 job을 반환한다. `text`가 없고 `sourceType`이 있으면 등록된
`RagIndexJobSourceExecutor`가 해당 source를 실행한다. `content-embedding-pipeline`은
`sourceType=attachment` job executor와 source 표시명 resolver를 제공하며 기존 attachment RAG index 경로를 재사용한다.
job 생성과 retry는 `services:ai_rag write`가 필요하다. attachment source job 생성과 retry는 기존
attachment 색인 API와 동일하게 `features:attachment write` 권한도 필요하다.
job 응답 DTO에는 grid 표시용 `sourceName`이 포함된다. 표시명은 요청 `sourceName`, metadata의
`sourceName`/`title`/`filename`/`fileName`/`name`, 등록된 `RagIndexJobSourceNameResolver`, `documentId`
순서로 결정한다. attachment source job은 resolver가 `AttachmentService`로 파일명을 조회해 저장하므로,
generic job API가 attachment 모듈에 직접 의존하지 않는다. `sourceName`은 `GET /jobs`,
`GET /jobs/{jobId}`, create/retry/cancel 응답에 동일하게 포함되며 저장 경계에 맞춰 최대 300자까지 보존한다.

attachment source job 예시:

```http
POST /api/mgmt/ai/rag/jobs
Authorization: Bearer <token>
Content-Type: application/json

{
  "objectType": "attachment",
  "objectId": "101",
  "documentId": "doc-101",
  "sourceType": "attachment",
  "metadata": {
    "category": "manual"
  },
  "keywords": ["spring", "java"],
  "useLlmKeywordExtraction": false
}
```

Job `status`는 `PENDING`, `RUNNING`, `SUCCEEDED`, `WARNING`, `FAILED`, `CANCELLED`이며,
`currentStep`은 `EXTRACTING`, `CHUNKING`, `EMBEDDING`, `INDEXING`, `COMPLETED`이다.
`WARNING`은 색인은 완료됐지만 경고 로그가 있는 상태를 뜻한다. 기본 저장소는 단일 인스턴스용
in-memory repository이므로 재시작 시 job 이력은 사라진다. 운영 장기 보관이 필요하면
`RagIndexJobRepository`를 DB 기반 Bean으로 교체한다.
`PENDING`/`RUNNING` job은 중복 실행을 막기 위해 retry 요청이 `409 Conflict`로 거절된다.
JDBC repository를 사용하더라도 재시도 실행에 필요한 원본 request가 서버 메모리에 남아 있지 않으면
retry는 `409 Conflict`로 거절된다.
`POST {mgmtBasePath}/rag/jobs/{jobId}/cancel`은 `PENDING`/`RUNNING` job만 `CANCELLED`로 전환한다.
외부 큐나 분산 worker를 사용하지 않는 기본 구현에서는 이미 실행 중인 provider/vector 호출을 강제로 중단하지 않고,
늦게 도착한 progress callback이 취소 상태를 덮어쓰지 않도록 방어한다.

운영 화면의 일반 흐름:

1. `POST {mgmtBasePath}/rag/jobs`로 색인 job을 생성한다.
2. `GET {mgmtBasePath}/rag/jobs?offset=0&limit=50&sort=createdAt&direction=desc`로 최신 job 목록을 표시한다.
3. `GET {mgmtBasePath}/rag/jobs/{jobId}`를 polling해 `ApiResponse.data.status`, `currentStep`, count를 표시한다.
4. `GET {mgmtBasePath}/rag/jobs/{jobId}/logs`의 `ApiResponse.data`로 경고와 오류 detail을 표시한다.
5. 완료 후 `GET {mgmtBasePath}/rag/jobs/{jobId}/chunks` 또는 object scope chunk API의 `ApiResponse.data`로 색인 결과를 확인한다.
   운영 화면에서 페이지 이동이 필요하면 `/chunks/page?offset=0&limit=50` variant를 사용한다.
6. 사용자가 중단을 요청하면 active job에 `POST {mgmtBasePath}/rag/jobs/{jobId}/cancel`을 호출한다.
7. 실패 또는 완료 상태가 된 뒤 `POST {mgmtBasePath}/rag/jobs/{jobId}/retry`를 호출한다.

목록 API는 `status`, `objectType`, `objectId`, `documentId` filter와 함께 `offset`, `limit`, `sort`,
`direction`을 지원한다. 기본 정렬은 `createdAt desc`이며, 잘못된 정렬 값은 기본값으로 normalize된다.
지원 정렬 필드는 `createdAt`, `startedAt`, `finishedAt`, `status`, `currentStep`, `objectType`,
`objectId`, `documentId`, `sourceType`, `durationMs`이다.
정렬 적용은 `RagIndexJobService`/`RagIndexJobRepository` 구현체가 `RagIndexJobSort`를 받는 overload를
override해야 보장된다. 기본 in-memory 구현은 정렬을 지원한다.

Chunk 조회 API는 기존 list 응답 호환을 유지한다. `/chunks`는 `limit`만 받는 inspection API이고,
`/chunks/page`는 `offset`, `limit`, `returned`, `hasMore`, `items`를 반환한다. `limit` 기본값과 최대값은
200이며, `hasMore`는 서버가 요청한 범위보다 한 건 더 조회할 수 있을 때 `true`다.

RAG 색인/검색 계열 요청은 기존 body를 유지하면서 `embeddingProfileId`, `embeddingProvider`,
`embeddingModel` optional field를 받을 수 있다. 이 필드는 retrieval embedding 선택용이며,
chat request의 `provider`/`model`은 답변 생성 모델 선택용이다.
서버 설정과 운영 화면 적용 기준은
[`RAG embedding profile 운영 가이드`](../../docs/dev/rag-embedding-profile-ops.md)를 따른다.
기존 chunk를 profile 기반 metadata로 재색인하는 운영 절차는
[`Legacy RAG chunk metadata 재색인 가이드`](../../docs/dev/rag-legacy-chunk-migration.md)를 따른다.

### Chunk Preview / Config

운영 화면은 `POST {mgmtBasePath}/rag/chunks/preview`로 색인 전에 text chunk 결과를 확인할 수 있다.
이 API는 embedding 생성, vector upsert, job 생성을 하지 않고 현재 등록된 `ChunkingOrchestrator`만 호출한다.
`ChunkingOrchestrator`가 없으면 `503 Service Unavailable`을 반환한다.

```http
POST /api/mgmt/ai/rag/chunks/preview
Authorization: Bearer <token>
Content-Type: application/json

{
  "text": "첫 번째 문단입니다.\n\n두 번째 문단입니다.",
  "documentId": "preview-doc",
  "objectType": "attachment",
  "objectId": "101",
  "strategy": "recursive",
  "maxSize": 800,
  "overlap": 100,
  "metadata": {
    "category": "manual"
  }
}
```

`strategy`, `maxSize`, `overlap`, `unit`을 생략하면 실제 신규 chunking 경로와 같은
`studio.chunking.*` configured default를 사용한다. 지원 preview 전략은 `fixed-size`, `recursive`,
`structure-based`이며, text-only `structure-based` 요청은 starter-chunking의 text fallback 동작을 따른다.
`semantic`, `llm-based`는 AI-linked 전략 후보라 이 preview API에서 실행하지 않는다.

preview 안전장치는 `studio.ai.endpoints.rag.chunk-preview.*`로 조정한다. 이 설정은 운영 화면 API limit만
제어하며 실제 색인 chunk 크기와 overlap은 계속 `studio.chunking.*`로 조정한다.

```yaml
studio:
  ai:
    endpoints:
      rag:
        chunk-preview:
          enabled: true
          max-input-chars: 200000
          max-preview-chunks: 500
```

`GET {mgmtBasePath}/rag/chunks/config`는 비밀 없는 운영 안전값만 반환한다. 응답에는
chunking availability, `studio.chunking.*` 값, preview에서 사용할 수 있는 기본 전략 여부(`previewStrategy`,
`defaultStrategyPreviewSupported`), 등록된 chunker 이름, deprecated legacy fallback
`studio.ai.rag.chunk-size/chunk-overlap`, RAG context/expansion 설정, preview limit이 포함된다.
API key, raw environment 값, provider secret은 노출하지 않는다.
`studio.chunking.strategy`가 `semantic` 또는 `llm-based`처럼 preview에서 실행하지 않는 전략이면
config API는 원래 설정값을 `strategy`로 보여주되 `defaultStrategyPreviewSupported=false`를 반환하고,
preview 요청에서 `strategy`를 생략하면 `400 Bad Request`가 반환된다.

Attachment 파일을 직접 읽어 `parseStructured` 결과를 preview하는 기능과 `NormalizedDocument` JSON preview는
후속 PR 범위이다. 현재 attachment RAG 색인은 기존 attachment RAG index/job API를 사용한다.

### 채팅 요청 예시

```http
POST /api/ai/chat
Authorization: Bearer <token>
Content-Type: application/json

{
  "provider": "openai",
  "systemPrompt": "답변은 간결하게 작성하세요.",
  "messages": [
    {"role": "user", "content": "안녕하세요"}
  ],
  "model": "gpt-4o-mini",
  "temperature": 0.7,
  "maxOutputTokens": 512
}
```

`provider`를 생략하면 `studio.ai.routing.default-chat-provider`가 사용된다. `systemPrompt`가 있으면
서버가 첫 system message로 변환해 provider에 전달한다.

기본 chat endpoint는 이전 대화를 서버에 저장하지 않는다. 요청에서 memory를 명시적으로 켜고,
서버 설정도 활성화된 경우에만 `conversationId` 기준으로 최근 대화 메시지를 in-memory에 보관한다.
memory 사용 시 클라이언트는 같은 `conversationId`와 새 턴 메시지만 보내야 한다.
이전 대화 전체를 `messages`에 다시 포함하면 서버 memory에 같은 턴이 중복 저장될 수 있다.
서버는 인증된 사용자 식별자와 `conversationId`를 합성한 내부 key로 저장해 사용자 간
동일한 `conversationId`가 충돌하지 않도록 분리한다.

```yaml
studio:
  ai:
    endpoints:
      chat:
        memory:
          enabled: false
          max-messages: 20
          max-conversations: 1000
          ttl: 30m
```

```http
POST /api/ai/chat
Authorization: Bearer <token>
Content-Type: application/json

{
  "provider": "openai",
  "messages": [
    {"role": "user", "content": "방금 이야기한 내용을 이어서 설명해줘"}
  ],
  "memory": {
    "enabled": true,
    "conversationId": "chat-123"
  }
}
```

| 설정 | 기본값 | 설명 |
|---|---:|---|
| `studio.ai.endpoints.chat.memory.enabled` | `false` | chat memory 요청 허용 여부 |
| `studio.ai.endpoints.chat.memory.max-messages` | `20` | conversation별 보관할 최근 메시지 수 |
| `studio.ai.endpoints.chat.memory.max-conversations` | `1000` | 인스턴스 메모리에 보관할 최대 conversation 수 |
| `studio.ai.endpoints.chat.memory.ttl` | `30m` | 마지막 접근 이후 conversation 보관 시간 |

응답 metadata는 기존 map 구조를 유지하면서 provider/model/latency/memory 정보를 추가한다.

```json
{
  "messages": [
    {"role": "assistant", "content": "안녕하세요"}
  ],
  "model": "gpt-4o-mini",
  "metadata": {
    "provider": "OPENAI",
    "resolvedModel": "gpt-4o-mini",
    "latencyMs": 120,
    "memoryUsed": true,
    "conversationId": "chat-123",
    "tokenUsage": {
      "inputTokens": 10,
      "outputTokens": 5,
      "totalTokens": 15
    }
  }
}
```

이 memory는 단일 앱 인스턴스의 in-memory cache다. 애플리케이션 재시작 시 사라지며, 다중 인스턴스 간 공유되지 않는다.
운영에서 여러 인스턴스 간 대화 memory가 필요하면 `ChatMemoryStore`와 `ConversationRepositoryPort`를 외부 저장소 기반 구현으로 교체한다.

memory가 활성화된 `/chat`, `/chat/rag`, `/chat/stream` 요청은 conversation repository에도 기록된다.
기본 구현은 단일 인스턴스용 `InMemoryConversationRepository`이며, conversation 목록/상세/삭제/regenerate/fork/truncate/compact/cancel API의 개발 및 smoke 용도다.
장기 보관, 감사 로그, 다중 인스턴스 공유가 필요하면 운영 저장소 구현을 별도 Bean으로 등록한다.
기존 `/chat` 응답 shape는 유지되며, conversation 관련 필드는 metadata에 additive하게 추가된다.

### Streaming Chat 예시

```http
POST /api/ai/chat/stream
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream

{
  "provider": "openai",
  "messages": [
    {"role": "user", "content": "짧게 설명해줘"}
  ],
  "memory": {
    "enabled": true,
    "conversationId": "chat-123"
  }
}
```

SSE event type은 `delta`, `usage`, `complete`, `error`이다. 각 event data에는 `requestId`가 포함되어 stream lifecycle을 추적할 수 있다.
Spring MVC의 `StreamingResponseBody`를 사용하므로 WebFlux/Netty event-loop에서 직접 소비하지 않는다.

```text
event: delta
data: {"type":"delta","requestId":"req-abc123","delta":"짧게","model":"gpt-4o-mini","metadata":{"provider":"OPENAI","resolvedModel":"gpt-4o-mini"}}

event: usage
data: {"type":"usage","requestId":"req-abc123","metadata":{"tokenUsage":{"inputTokens":10,"outputTokens":5,"totalTokens":15},"latencyMs":120}}

event: complete
data: {"type":"complete","requestId":"req-abc123","model":"gpt-4o-mini","metadata":{"provider":"OPENAI","resolvedModel":"gpt-4o-mini"}}
```

### Conversation API 예시

```http
GET /api/ai/chat/conversations?offset=0&limit=20
Authorization: Bearer <token>
```

목록 항목에는 `conversationId`, `title`, `summary`, `messageCount`, `lastUpdatedAt`, `status`가 포함된다.
상세 API는 동일 conversation의 active message 목록을 함께 반환한다.

```http
POST /api/ai/chat/regenerate
Authorization: Bearer <token>
Content-Type: application/json

{
  "conversationId": "chat-123"
}
```

`regenerate`는 마지막 user turn까지의 메시지로 provider를 다시 호출하고 마지막 assistant 응답을 대체한다.
`truncate`와 `fork`는 `messageId`가 필수다.

```json
{
  "conversationId": "chat-123",
  "messageId": "message-id-for-truncate-or-fork",
  "newConversationId": "chat-copy"
}
```

`compact`는 `summary`가 필수이며, `cancel`은 `conversationId`만 사용한다.

```json
{
  "conversationId": "chat-123",
  "summary": "압축 요약"
}
```

### RAG Chat 예시

```http
POST /api/ai/chat/rag
Authorization: Bearer <token>
Content-Type: application/json

{
  "chat": {
    "provider": "openai",
    "systemPrompt": "제공된 파일 컨텍스트를 기반으로 답변하세요.",
    "messages": [
      {"role": "user", "content": "이 파일의 핵심 내용을 요약해줘"}
    ]
  },
  "ragQuery": "핵심 내용 요약",
  "topK": 3,
  "minScore": 0.15,
  "objectType": "attachment",
  "objectId": "123",
  "embeddingProfileId": "retrieval-ko"
}
```

`topK`는 `1` 이상 `100` 이하만 허용한다. 기존 `ragTopK`도 호환용으로 계속 받지만, 둘 다 있으면 `topK`가 우선한다.
`minScore`를 지정하면 최종 검색 결과에서 해당 점수 이상인 항목만 context 후보로 사용한다. 요청값이 없으면
`studio.ai.rag.retrieval.top-k`, `studio.ai.rag.retrieval.min-score` 설정값을 사용한다.
`minScore` 적용 후 결과 수는 `topK`보다 적을 수 있다. `objectType`/`objectId`를 지정하면 해당 객체 범위의 RAG 인덱스에서 검색한다.
fallback 전략 선택은 서버 설정 `min-relevance-score` 기준으로 결정되며, client `minScore`는 최종 반환/context 후보 cutoff로만 적용한다.
이 값은 attachment 전용 ID가 아니라 색인 시 저장된 chunk metadata의 object scope와 같은 의미다. 예를 들어 RAG job이
`objectType=2001`, `objectId=6`으로 생성됐다면 RAG Chat에도 같은 값을 전달해야 한다. `ragQuery`가 없고
객체 범위만 있으면 저장된 chunk를 순서대로 가져와 컨텍스트로 사용한다.

RAG context는 이슈 #202부터 설정된 chunk 수와 문자 수를 넘지 않도록 제한된다.
문자 수 한도는 context header를 포함해 계산하며, 한도를 초과하는 chunk는 문장 중간에서 자르지 않고 통째로 제외한다.
현재 context assembly 구현은 web starter 내부의 `RagContextBuilder`가 담당한다.
별도 core `ContextAssembler` 계약은 만들지 않고, `ChunkContextExpander` bean이 있으면 object-scoped
검색 결과의 chunk metadata를 기준으로 parent/neighbor/table 주변 문맥을 선택적으로 확장한다.
`ChunkContextExpander`가 없거나 `objectType`/`objectId` 또는 `chunkId` metadata가 부족하면 기존처럼
retrieval hit content만 사용한다. 확장 후에도 아래 `max-chunks`, `max-chars`, `include-scores`
설정은 그대로 적용된다.

RAG Chat 응답은 실제 답변 생성 프롬프트에 포함된 근거를 `metadata.ragReferences` 배열로 반환한다.
순서는 system context의 `[1]`, `[2]` 순서와 같으며, context expansion 또는 fallback이 적용된 경우에도
최종 프롬프트에 들어간 content를 기준으로 한다. 클라이언트는 별도 management search를 다시 호출하지 않고
이 값으로 출처 UI를 구성할 수 있다.

```json
{
  "metadata": {
    "ragReferences": [
      {
        "index": 1,
        "documentId": "3",
        "sourceName": "sample.pdf",
        "chunkId": "chunk-1",
        "chunkOrder": 0,
        "score": 0.91,
        "content": "프롬프트에 포함된 근거 본문",
        "page": 3,
        "pageNumber": 3,
        "sourceRef": "page[3]",
        "metadata": {
          "objectType": "attachment",
          "objectId": "3"
        }
      }
    ]
  }
}
```

`sourceName`은 `sourceName`, `title`, `filename`, `fileName`, `name` metadata 순서로 선택한다.
위치 정보는 metadata에 있으면 `page`/`pageNumber`, `slide`/`slideNumber`, `sourceRef`,
`section`, `heading`으로 함께 내려간다.

```yaml
studio:
  ai:
    endpoints:
      rag:
        context:
          max-chunks: 8
          max-chars: 12000
          include-scores: true
          expansion:
            enabled: true
            candidate-multiplier: 4
            max-candidates: 100
            previous-window: 1
            next-window: 1
            include-parent-content: true
        diagnostics:
          allow-client-debug: false
```

| 설정 | 기본값 | 설명 |
|---|---:|---|
| `studio.ai.endpoints.rag.context.max-chunks` | `8` | chat system context에 포함할 최대 RAG chunk 수 |
| `studio.ai.endpoints.rag.context.max-chars` | `12000` | header 포함 chat system context 최대 문자 수 |
| `studio.ai.endpoints.rag.context.include-scores` | `true` | context에 retrieval score를 포함할지 여부 |
| `studio.ai.endpoints.rag.context.expansion.enabled` | `true` | `ChunkContextExpander` 기반 주변 문맥 확장 사용 여부 |
| `studio.ai.endpoints.rag.context.expansion.candidate-multiplier` | `4` | object-scoped 검색 시 effective `topK` 대비 후보 chunk 조회 배수. 런타임 최대 20으로 제한 |
| `studio.ai.endpoints.rag.context.expansion.max-candidates` | `100` | context expansion 후보 chunk 조회 limit 상한. 런타임 최대 500으로 제한 |
| `studio.ai.endpoints.rag.context.expansion.previous-window` | `1` | neighbor expansion에 전달할 이전 chunk window |
| `studio.ai.endpoints.rag.context.expansion.next-window` | `1` | neighbor expansion에 전달할 다음 chunk window |
| `studio.ai.endpoints.rag.context.expansion.include-parent-content` | `true` | parent/table expansion에서 parent content metadata를 사용할지 여부 |
| `studio.ai.endpoints.rag.diagnostics.allow-client-debug` | `false` | client `debug=true` 요청에 `metadata.ragDiagnostics` 노출 허용 여부 |

이슈 #205부터 RAG retrieval diagnostics를 선택적으로 사용할 수 있다. 서버의
`studio.ai.rag.diagnostics.enabled=true`가 켜진 경우 검색 fallback strategy를 기록하고,
클라이언트 요청의 `debug=true`와 서버 `allow-client-debug=true`가 모두 만족될 때만 응답 metadata에
`ragDiagnostics`를 추가한다. diagnostics metadata에는 chunk 본문이나 snippet을 포함하지 않는다.
result snippet 로그는 `studio.ai.rag.diagnostics.log-results=true`일 때만 debug level로 제한 길이만 출력한다.

이슈 #305부터 같은 opt-in 조건에서 RAG context expansion diagnostics도
`ChatResponseDto.metadata.ragContextDiagnostics`로 노출한다. 이 값은 확장 지원 여부, 적용 여부, 전략,
후보/결과/확장 hit 수, fallback reason만 포함하며 chunk 본문, snippet, embedding vector는 포함하지 않는다.

### 임베딩 요청 예시

```http
POST /api/mgmt/ai/embedding
Authorization: Bearer <token>
Content-Type: application/json

{
  "texts": ["임베딩할 텍스트1", "임베딩할 텍스트2"]
}
```

### RAG 인덱싱 예시

```http
POST /api/mgmt/ai/rag/index
Authorization: Bearer <token>
Content-Type: application/json

{
  "documentId": "doc-001",
  "text": "인덱싱할 문서 내용",
  "metadata": {"source": "manual"},
  "useLlmKeywordExtraction": true
}
```

### RAG 검색 예시

```http
POST /api/mgmt/ai/rag/search
Authorization: Bearer <token>
Content-Type: application/json

{
  "query": "검색어",
  "topK": 5,
  "minScore": 0.15,
  "objectType": "attachment",
  "objectId": "123"
}
```

`objectType`/`objectId`는 core `MetadataFilter.objectScope(...)`로 변환되어 RAG pipeline에 전달된다.
둘 다 생략하면 전체 RAG 인덱스 검색을 수행하고, 둘 중 하나만 보내면 `400 Bad Request`로 처리한다.
`topK`/`minScore` 요청값이 없으면 `studio.ai.rag.retrieval.top-k`, `studio.ai.rag.retrieval.min-score`
설정값을 사용한다.
fallback 전략 선택은 서버 설정 `min-relevance-score` 기준으로 결정되며, client `minScore`는 최종 반환 후보 cutoff로만 적용한다.
`POST /api/mgmt/ai/rag/search`는 query 기반 semantic search API이므로 검색 전에 embedding provider를 호출해
query embedding을 생성한다. OpenAI quota 부족처럼 provider quota/rate limit이 발생하면 chat 오류와 구분되는
HTTP 429 `ProblemDetails.detail`로 `Embedding provider quota exceeded...` 메시지를 반환한다.
저장된 chunk 원문을 provider 호출 없이 확인하려면 `GET /api/mgmt/ai/rag/jobs/{jobId}/chunks`,
`GET /api/mgmt/ai/rag/objects/{objectType}/{objectId}/chunks` 또는 각 `/chunks/page` variant를 사용한다.

### 파일 기반 RAG 흐름

첨부파일 기반 답변은 `content-embedding-pipeline`과 함께 사용한다.

1. `POST /api/mgmt/attachments/{attachmentId}/rag/index`로 파일 텍스트를 추출하고 RAG 인덱스에 저장한다.
2. `GET /api/mgmt/attachments/{attachmentId}/rag/metadata`로 `objectType=attachment`, `objectId=<attachmentId>` 메타데이터를 확인한다.
3. `POST /api/ai/chat/rag`에 `objectType=attachment`, `objectId=<attachmentId>`를 넘겨 해당 파일 내용 기반 답변을 요청한다.

`/api/ai/chat/rag`에서 객체 범위 RAG를 사용할 때 `objectType`/`objectId`는 management RAG search와
job chunk 조회 API의 object scope와 동일하게 해석된다. RAG 인덱스 데이터를 읽기 때문에 기본 AI chat 권한
(`services:ai_chat write`) 외에 RAG 조회 권한(`services:ai_rag read`)도 필요하다. `objectType=attachment`인
경우에는 첨부 읽기 권한(`features:attachment read`)도 추가로 필요하다. 그 외 object scope는
`objects:<objectType>:<objectId> read` 또는 `objects:<objectType> read` 정책 중 하나가 필요하다.

### 벡터 검색 응답

`POST /api/mgmt/ai/vectors/search` 응답 항목은 기존 `documentId`와 클라이언트 grid 호환용 `id`를 함께 반환한다.
내부 검색 결과는 core `VectorSearchResults`/`VectorSearchHit` aggregate 계약으로 정규화하지만,
HTTP 응답 shape는 기존 `List<VectorSearchResultDto>`를 유지한다.
`topK`/`minScore`를 생략하면 기존 `topK=5`, `minScore` 미적용 대신
`studio.ai.rag.retrieval.top-k`, `studio.ai.rag.retrieval.min-score` 설정값을 사용한다.
요청에 `embedding`을 직접 전달하면 provider 호출 없이 해당 벡터로 검색한다. `embedding` 없이 `query`만 전달하면
검색 전에 configured `EmbeddingPort`가 호출되어 query embedding을 생성하므로 Spring AI/OpenAI 등 embedding
provider quota를 사용한다. `hybrid=true`도 lexical score와 vector score를 함께 쓰기 때문에 query embedding이
필요하며, 이 스타터는 별도 lexical-only debug search를 제공하지 않는다.
query embedding 생성 중 provider quota/rate limit이 발생하면 HTTP 429 `ProblemDetails.detail`에
`Embedding provider quota exceeded...` 메시지를 반환한다. 원시 chunk 확인이 목적이면 RAG chunk inspection API
(`GET /api/mgmt/ai/rag/jobs/{jobId}/chunks`, `GET /api/mgmt/ai/rag/objects/{objectType}/{objectId}/chunks`,
또는 각 `/chunks/page` variant)를 사용하면 provider 호출이 발생하지 않는다.
요청에 `includeText=false` 또는 `includeMetadata=false`를 전달하면 core `VectorSearchRequest`에 그대로 전달되어
응답 항목의 `content`가 `null`이거나 `metadata`가 빈 객체일 수 있다.
`documentId`, `chunkId`, `sourceRef`, `page`, `slide` 같은 provenance key는
[`studio-platform-ai` RAG metadata key reference](../../studio-platform-ai/README.md#rag-metadata-key-reference)를 따른다.

```json
{
  "id": "doc-1",
  "documentId": "doc-1",
  "content": "...",
  "metadata": {
    "objectType": "attachment",
    "objectId": "123"
  },
  "score": 0.91
}
```

## 4) 자동 구성되는 주요 빈 (엔드포인트)

| 빈 | 설명 |
|---|---|
| `ChatController` | 채팅 및 RAG 채팅 엔드포인트 |
| `EmbeddingController` | 임베딩 엔드포인트 |
| `VectorController` | 벡터 업서트 및 검색 엔드포인트 |
| `RagController` | RAG 인덱싱 및 검색 엔드포인트 |
| `QueryRewriteController` | 쿼리 리라이트 엔드포인트 |
| `AiInfoController` | AI 정보 조회 엔드포인트 |
| `AiWebExceptionHandler` | AI web endpoint의 `ProblemDetails` 오류 매핑 |

## 5) 참고 사항

- `VectorController`는 `VectorStorePort` 빈이 없어도 등록되지만, 벡터 관련 요청 시 HTTP 503을 반환한다.
- 벡터/RAG 검색에서 `query`만 전달하는 semantic search는 embedding provider 호출을 수반한다.
- 벡터 검색 시 `hybrid=true`를 설정하면 BM25 + 벡터 하이브리드 검색이 활성화된다. lexical score 계산에는
  `query` 텍스트가 필요하고, vector score 계산에는 직접 전달한 `embedding` 또는 query로 생성한 embedding이 필요하다.
- 채팅 API의 `provider`는 Studio provider id 선택만 담당한다. OpenAI 런타임 설정은 계속 `spring.ai.openai.*`가 소유한다.
- Google GenAI 등 provider quota/rate limit 오류는 AI web exception handler가 HTTP 429 `ProblemDetails`로 변환한다.
- Vector/RAG query embedding quota/rate limit 오류는 chat quota와 구분되는 embedding provider 메시지로 반환한다.
- `query-rewrite` 엔드포인트는 Mustache 템플릿(`query-rewrite`)이 없으면 내장 폴백 프롬프트를 사용한다.
- 모든 엔드포인트는 Spring Security의 메서드 레벨 권한 검사(`@PreAuthorize`)를 사용한다.
  `endpointAuthz` 빈이 컨텍스트에 등록되어 있어야 한다.
- 이 스타터만 단독으로 추가해도 `studio-platform-starter-ai`가 `api` 의존성으로 전이된다.
