# studio-platform-starter-ai-web

AI 기능을 HTTP 엔드포인트로 노출하는 웹 스타터이다.
`studio-platform-starter-ai`와 분리되어 있으며, AI 코어 기능은 유지하면서 REST API 노출 여부를
별도로 제어할 수 있다. 채팅, 임베딩, 벡터 스토어, RAG 파이프라인, 쿼리 리라이트, AI 정보 조회
엔드포인트를 자동 등록한다.

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

AI 코어(`studio.ai.enabled`)가 `true`여야 웹 엔드포인트도 활성화된다.
웹 엔드포인트 전체는 `studio.ai.endpoints.enabled`로 제어한다.

```yaml
studio:
  ai:
    enabled: true
    default-provider: openai
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
| `POST` | `{basePath}/chat/rag` | RAG 컨텍스트 주입 후 채팅 | `services:ai_chat write` |
| `POST` | `{basePath}/query-rewrite` | 검색 쿼리 리라이트 | `services:ai_chat read` |
| `GET`  | `{basePath}/info/providers` | 프로바이더 및 벡터 스토어 상태 조회 | `services:ai_chat read` 또는 `services:ai_embedding read` |
| `POST` | `{mgmtBasePath}/embedding` | 텍스트 임베딩 벡터 생성 | `services:ai_embedding write` |
| `POST` | `{mgmtBasePath}/vectors` | 벡터 문서 업서트 | `services:ai_vector read` |
| `POST` | `{mgmtBasePath}/vectors/search` | 벡터 유사도 검색 | `services:ai_vector read` |
| `POST` | `{mgmtBasePath}/rag/index` | 문서 RAG 인덱싱 | `services:ai_rag read` |
| `POST` | `{mgmtBasePath}/rag/search` | RAG 시맨틱 검색 | `services:ai_rag read` |

> `studio.ai.endpoints.enabled=false`이면 위 AI web endpoint 전체가 등록되지 않는다.

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

`provider`를 생략하면 `studio.ai.default-provider`가 사용된다. `systemPrompt`가 있으면
서버가 첫 system message로 변환해 provider에 전달한다.

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
  "ragTopK": 3,
  "objectType": "attachment",
  "objectId": "123"
}
```

`objectType`/`objectId`를 지정하면 해당 객체 범위의 RAG 인덱스에서 검색한다. `ragQuery`가 없고
객체 범위만 있으면 저장된 chunk를 순서대로 가져와 컨텍스트로 사용한다.

RAG context는 이슈 #202부터 설정된 chunk 수와 문자 수를 넘지 않도록 제한된다.
문자 수 한도는 context header를 포함해 계산하며, 한도를 초과하는 chunk는 문장 중간에서 자르지 않고 통째로 제외한다.

```yaml
studio:
  ai:
    endpoints:
      rag:
        context:
          max-chunks: 8
          max-chars: 12000
          include-scores: true
        diagnostics:
          allow-client-debug: false
```

| 설정 | 기본값 | 설명 |
|---|---:|---|
| `studio.ai.endpoints.rag.context.max-chunks` | `8` | chat system context에 포함할 최대 RAG chunk 수 |
| `studio.ai.endpoints.rag.context.max-chars` | `12000` | header 포함 chat system context 최대 문자 수 |
| `studio.ai.endpoints.rag.context.include-scores` | `true` | context에 retrieval score를 포함할지 여부 |
| `studio.ai.endpoints.rag.diagnostics.allow-client-debug` | `false` | client `debug=true` 요청에 `metadata.ragDiagnostics` 노출 허용 여부 |

이슈 #205부터 RAG retrieval diagnostics를 선택적으로 사용할 수 있다. 서버의
`studio.ai.pipeline.diagnostics.enabled=true`가 켜진 경우 검색 fallback strategy를 기록하고,
클라이언트 요청의 `debug=true`와 서버 `allow-client-debug=true`가 모두 만족될 때만 응답 metadata에
`ragDiagnostics`를 추가한다. diagnostics metadata에는 chunk 본문이나 snippet을 포함하지 않는다.
result snippet 로그는 `studio.ai.pipeline.diagnostics.log-results=true`일 때만 debug level로 제한 길이만 출력한다.

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

### 파일 기반 RAG 흐름

첨부파일 기반 답변은 `content-embedding-pipeline`과 함께 사용한다.

1. `POST /api/mgmt/attachments/{attachmentId}/rag/index`로 파일 텍스트를 추출하고 RAG 인덱스에 저장한다.
2. `GET /api/mgmt/attachments/{attachmentId}/rag/metadata`로 `objectType=attachment`, `objectId=<attachmentId>` 메타데이터를 확인한다.
3. `POST /api/ai/chat/rag`에 `objectType=attachment`, `objectId=<attachmentId>`를 넘겨 해당 파일 내용 기반 답변을 요청한다.

`/api/ai/chat/rag`에서 객체 범위 RAG를 사용할 때 현재 안전하게 지원되는 범위는
`objectType=attachment`와 구체적인 `objectId` 조합이다. 이 경우 기본 AI chat 권한
(`services:ai_chat write`) 외에 첨부 읽기 권한(`features:attachment read`)도 필요하다.

### 벡터 검색 응답

`POST /api/mgmt/ai/vectors/search` 응답 항목은 기존 `documentId`와 클라이언트 grid 호환용 `id`를 함께 반환한다.

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
- 벡터 검색 시 `hybrid=true`를 설정하면 BM25 + 벡터 하이브리드 검색이 활성화된다(query 텍스트 필수).
- 채팅 API의 `provider`는 Studio provider id 선택만 담당한다. OpenAI 런타임 설정은 계속 `spring.ai.openai.*`가 소유한다.
- Google GenAI 등 provider quota/rate limit 오류는 AI web exception handler가 HTTP 429 `ProblemDetails`로 변환한다.
- `query-rewrite` 엔드포인트는 Mustache 템플릿(`query-rewrite`)이 없으면 내장 폴백 프롬프트를 사용한다.
- 모든 엔드포인트는 Spring Security의 메서드 레벨 권한 검사(`@PreAuthorize`)를 사용한다.
  `endpointAuthz` 빈이 컨텍스트에 등록되어 있어야 한다.
- 이 스타터만 단독으로 추가해도 `studio-platform-starter-ai`가 `api` 의존성으로 전이된다.
