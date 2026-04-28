# AI 모듈 업데이트 클라이언트 수정 가이드

이 문서는 최근 AI/RAG 모듈 변경 후 클라이언트가 맞춰야 할 API 계약과 화면 동작을 정리한다.
서버 설정과 내부 구현 상세는 `starter/studio-platform-starter-ai/README.md`와
`starter/studio-platform-starter-ai-web/README.md`를 기준으로 확인한다.

## 적용 대상

- AI 채팅 화면
- 파일 기반 RAG 채팅 화면
- 임베딩, 벡터 검색, RAG 인덱싱 관리 화면
- AI provider 상태를 조회하는 설정 또는 운영 화면

## 수정 요약

| 영역 | 클라이언트 조치 |
|---|---|
| 관리용 AI API 경로 | 임베딩, 벡터, RAG 인덱싱/검색 호출을 기본 `/api/mgmt/ai`로 변경 |
| 사용자용 AI API 경로 | 채팅, RAG 채팅, query rewrite, provider 정보 조회는 `/api/ai` 유지 |
| 채팅 요청 | `provider`, `systemPrompt` 필드 사용 가능 |
| RAG 채팅 | `objectType`, `objectId`, `ragQuery`, `ragTopK`, `debug` 흐름 확인 |
| 벡터 검색 응답 | grid key는 `id`를 우선 사용하고, 기존 `documentId`도 호환 처리 |
| 오류 처리 | 현재 명시적으로 매핑된 provider quota/rate limit만 HTTP 429 `ProblemDetails`로 처리 |
| RAG diagnostics | 서버와 요청이 모두 허용할 때만 `metadata.ragDiagnostics`, `metadata.ragContextDiagnostics` 표시 |
| RAG embedding profile | 색인/검색/채팅 RAG 요청에서 `embeddingProfileId`를 같은 값으로 유지 |

## API 경로 변경

사용자용 API는 기본 `/api/ai`를 사용한다.

| 기능 | 메서드 | 경로 |
|---|---|---|
| 채팅 | `POST` | `/api/ai/chat` |
| RAG 채팅 | `POST` | `/api/ai/chat/rag` |
| Query rewrite | `POST` | `/api/ai/query-rewrite` |
| Provider 정보 | `GET` | `/api/ai/info/providers` |

관리용 API는 기본 `/api/mgmt/ai`를 사용한다.

| 기능 | 메서드 | 경로 |
|---|---|---|
| 임베딩 생성 | `POST` | `/api/mgmt/ai/embedding` |
| 벡터 업서트 | `POST` | `/api/mgmt/ai/vectors` |
| 벡터 검색 | `POST` | `/api/mgmt/ai/vectors/search` |
| RAG 인덱싱 | `POST` | `/api/mgmt/ai/rag/index` |
| RAG 검색 | `POST` | `/api/mgmt/ai/rag/search` |
| RAG 색인 job 생성 | `POST` | `/api/mgmt/ai/rag/jobs` |
| RAG 색인 job 목록 | `GET` | `/api/mgmt/ai/rag/jobs` |
| RAG 색인 job 상세 | `GET` | `/api/mgmt/ai/rag/jobs/{jobId}` |
| RAG 색인 job 로그 | `GET` | `/api/mgmt/ai/rag/jobs/{jobId}/logs` |
| RAG 색인 job chunk 조회 | `GET` | `/api/mgmt/ai/rag/jobs/{jobId}/chunks` |
| RAG 색인 job chunk 페이지 조회 | `GET` | `/api/mgmt/ai/rag/jobs/{jobId}/chunks/page` |
| RAG 색인 job 재시도 | `POST` | `/api/mgmt/ai/rag/jobs/{jobId}/retry` |
| RAG 색인 job 취소 | `POST` | `/api/mgmt/ai/rag/jobs/{jobId}/cancel` |
| RAG object chunk 조회 | `GET` | `/api/mgmt/ai/rag/objects/{objectType}/{objectId}/chunks` |
| RAG object chunk 페이지 조회 | `GET` | `/api/mgmt/ai/rag/objects/{objectType}/{objectId}/chunks/page` |
| RAG object metadata 조회 | `GET` | `/api/mgmt/ai/rag/objects/{objectType}/{objectId}/metadata` |

기존 `/api/ai/embedding`, `/api/ai/vectors`, `/api/ai/rag/*` 경로를 계속 쓰는 환경은
서버에서 `studio.ai.endpoints.mgmt-base-path=/api/ai`로 호환 설정했는지 확인한다.
호환 설정이 없으면 클라이언트 호출 경로를 `/api/mgmt/ai`로 바꾼다.

## RAG 색인 운영 화면

기존 `POST /api/mgmt/ai/rag/index`와 `POST /api/mgmt/attachments/{attachmentId}/rag/index`는
응답 body 없이 `202 Accepted`를 유지한다. 서버가 job tracking을 제공하면 응답 헤더
`X-RAG-Job-Id`가 추가되므로, 클라이언트는 이 값을 이용해 job 상세를 조회할 수 있다.
attachment job의 로그와 chunk까지 조회하려면 `services:ai_rag read`와 `features:attachment read`가 모두 필요하다.

신규 `POST /api/mgmt/ai/rag/jobs`는 raw text 색인과 source 기반 색인을 모두 받을 수 있다.
`text`가 있으면 `RagPipelineService` raw text 색인을 실행하고, `sourceType=attachment`와 `text`가 없으면
attachment source executor가 기존 attachment RAG 색인 흐름을 비동기로 실행한다. attachment source job은
attachment 쓰기 권한도 필요하다. 기존 attachment 전용 API를 사용하는 화면은 그대로 유지할 수 있고,
응답 헤더의 `X-RAG-Job-Id`로 동일한 job 조회 화면에 연결할 수 있다. 단, attachment 전용 API 호출 권한과
job 조회 권한은 별도이므로 job 화면 연결은 `services:ai_rag read` 권한이 있는 운영자 화면에서만 활성화한다.

생성 요청 예시는 다음과 같다.

```json
{
  "objectType": "attachment",
  "objectId": "123",
  "documentId": "123",
  "sourceType": "attachment",
  "metadata": {
    "attachmentId": "123"
  },
  "keywords": ["계약서"],
  "useLlmKeywordExtraction": false
}
```

RAG embedding profile을 선택하는 운영 화면은 색인과 검색/채팅에 같은 `embeddingProfileId`를 보낸다.
`chat.provider`와 `chat.model`은 답변 생성 모델 선택이고, `embeddingProfileId`, `embeddingProvider`,
`embeddingModel`은 retrieval embedding 선택이다. 설정 책임과 provider별 예시는
[`RAG embedding profile 운영 가이드`](rag-embedding-profile-ops.md)를 따른다.

운영 화면은 아래 순서로 동작한다.

1. `POST /api/mgmt/ai/rag/jobs`로 job을 생성한다.
2. 목록 화면은 `GET /api/mgmt/ai/rag/jobs?offset=0&limit=50&sort=createdAt&direction=desc`를 호출한다.
3. 목록 filter는 `status`, `objectType`, `objectId`, `documentId`를 사용한다.
4. `GET /api/mgmt/ai/rag/jobs/{jobId}`를 polling하고 `data.status`, `data.currentStep`, `data.chunkCount`, `data.embeddedCount`, `data.indexedCount`, `data.warningCount`를 표시한다.
5. `GET /api/mgmt/ai/rag/jobs/{jobId}/logs`로 단계별 `data[].level`, `data[].step`, `data[].message`를 표시한다.
6. 완료 후 `GET /api/mgmt/ai/rag/jobs/{jobId}/chunks?limit=200` 또는 object chunk API의 `data`로 색인 결과를 보여준다.
   페이지 이동 UI는 `/chunks/page?offset=0&limit=50` variant를 사용한다.
7. `FAILED`, `SUCCEEDED`, `WARNING`, `CANCELLED` 상태이고 권한이 있을 때만 retry 버튼을 활성화한다. `PENDING`/`RUNNING` retry는 `409 Conflict`로 처리한다.
8. `PENDING`, `RUNNING` 상태이고 권한이 있을 때만 cancel 버튼을 활성화한다. terminal job cancel은 `409 Conflict`로 처리한다.

기본 job 저장소는 in-memory이므로 서버 재시작 또는 저장소 교체 정책에 따라 job 상세, 로그, chunk 조회가
`404 Not Found`를 반환할 수 있다. polling 중 404를 받으면 해당 job polling을 중단하고 retry/cancel 버튼을
비활성화한다. 필요하면 object metadata/chunk API로 현재 색인 상태를 다시 조회하거나 새 색인 job을 생성한다.

`GET /api/mgmt/ai/rag/jobs/{jobId}/chunks`와 object chunk API는 `limit` query parameter를 받는다.
서버 기본값과 최대값은 모두 200이며, 운영 화면의 색인 점검용 조회로 사용한다. 전체 chunk export가 필요하면
별도 API를 설계한다. `/chunks/page` variant는 `ApiResponse.data` 안에 `offset`, `limit`, `returned`,
`hasMore`, `items`를 반환하므로 운영 화면의 페이지 이동에는 이 응답을 사용한다.

`status`와 버튼 동작 기준은 다음과 같다.

| status | 화면 처리 | retry | cancel |
|---|---|---|---|
| `PENDING` | 대기 상태 표시 | 비활성 | 권한 있으면 활성 |
| `RUNNING` | `currentStep`와 count polling | 비활성 | 권한 있으면 활성 |
| `SUCCEEDED` | 완료 상태와 chunk 조회 링크 표시 | 권한 있으면 활성 | 비활성 |
| `WARNING` | 완료 상태와 경고 badge/log 표시 | 권한 있으면 활성 | 비활성 |
| `FAILED` | 오류 메시지와 retry 표시 | 권한 있으면 활성 | 비활성 |
| `CANCELLED` | 취소 상태 표시 | 권한 있으면 활성 | 비활성 |

RAG job 운영 API 권한은 다음 기준으로 처리한다.

| API | 기본 권한 | attachment job/object 추가 권한 |
|---|---|---|
| `POST /api/mgmt/ai/rag/jobs` | `services:ai_rag write` | `objectType=attachment` 또는 `sourceType=attachment`이면 `features:attachment write` |
| `GET /api/mgmt/ai/rag/jobs`, `GET /api/mgmt/ai/rag/jobs/{jobId}` | `services:ai_rag read` | 없음 |
| `GET /api/mgmt/ai/rag/jobs/{jobId}/logs` | `services:ai_rag read` | attachment job이면 `features:attachment read` |
| `GET /api/mgmt/ai/rag/jobs/{jobId}/chunks`, `/chunks/page` | `services:ai_rag read` | attachment job이면 `features:attachment read` |
| `POST /api/mgmt/ai/rag/jobs/{jobId}/retry` | `services:ai_rag write` | attachment job이면 `features:attachment write` |
| `POST /api/mgmt/ai/rag/jobs/{jobId}/cancel` | `services:ai_rag write` | attachment job이면 `features:attachment write` |
| `GET /api/mgmt/ai/rag/objects/{objectType}/{objectId}/chunks`, `/chunks/page` | `services:ai_rag read` | `objectType=attachment`이면 `features:attachment read` |
| `GET /api/mgmt/ai/rag/objects/{objectType}/{objectId}/metadata` | `services:ai_rag read` | `objectType=attachment`이면 `features:attachment read` |

`POST /api/mgmt/ai/rag/jobs/{jobId}/cancel`은 job 상태를 `CANCELLED`로 표시한다.
서버 기본 구현은 이미 시작된 외부 provider/vector 호출을 강제로 중단하지 않으므로, 화면은 cancel 직후에도
짧은 시간 동안 polling을 유지해 최종 `CANCELLED` 상태를 확인한다.

## 채팅 요청 변경

`POST /api/ai/chat`은 `provider`와 `systemPrompt`를 받을 수 있다.

```json
{
  "provider": "openai",
  "systemPrompt": "답변은 간결하게 작성하세요.",
  "messages": [
    {
      "role": "user",
      "content": "요약해줘"
    }
  ],
  "model": "gpt-4o-mini",
  "temperature": 0.7,
  "maxOutputTokens": 512
}
```

클라이언트 기준:

- `provider`는 사용자가 provider를 고르는 화면에서만 보낸다.
- `provider`를 비우면 서버의 `studio.ai.default-provider`가 사용된다.
- 알 수 없는 `provider`는 400 오류로 처리한다.
- `systemPrompt`는 별도 system message로 직접 넣지 않고 최상위 필드로 보낸다.
- 기존 `messages` 배열은 유지한다.

## RAG 채팅 변경

`POST /api/ai/chat/rag`는 채팅 요청을 `chat` 필드에 넣고 RAG 검색 조건을 함께 보낸다.

```json
{
  "chat": {
    "provider": "openai",
    "systemPrompt": "제공된 파일 컨텍스트를 기반으로 답변하세요.",
    "messages": [
      {
        "role": "user",
        "content": "이 파일의 핵심 내용을 요약해줘"
      }
    ]
  },
  "ragQuery": "핵심 내용 요약",
  "ragTopK": 3,
  "objectType": "attachment",
  "objectId": "123",
  "debug": false
}
```

클라이언트 기준:

- 파일 기반 답변은 색인된 job/chunk의 object scope를 그대로 보낸다. attachment 전용 색인이라면
  `objectType=attachment`, `objectId=<attachmentId>`이고, 도메인 객체 scope로 색인했다면 예를 들어
  `objectType=2001`, `objectId=6`을 그대로 사용한다.
- `/api/ai/chat/rag`의 `objectType`/`objectId`는 management RAG search와 job chunk 조회 API의 object scope와
  같은 의미다. 둘 중 하나만 보내면 서버는 `400 Bad Request`로 처리한다. 둘 다 생략하고 `ragQuery`만 보내는
  전역 RAG 검색은 별도 흐름으로 허용된다.
- RAG 채팅은 기본 `services:ai_chat write` 외에 `services:ai_rag read` 권한이 필요하다.
- `objectType=attachment`인 경우에는 `features:attachment read` 권한도 추가로 필요하다.
- 그 외 object scope는 `objects:<objectType>:<objectId> read` 또는 `objects:<objectType> read` 정책 중
  하나가 필요하다.
- `ragQuery`가 있으면 객체 범위 안에서 검색한다.
- `ragQuery`가 없고 객체 범위만 있으면 저장된 chunk를 순서대로 컨텍스트에 사용한다.
- `ragTopK`는 화면의 최대 참고 문서 수와 맞춘다.
- `debug=true`는 운영 화면 또는 개발자 도구에서만 사용한다.
- 첨부 파일 RAG 채팅은 기본 AI chat 권한 외에 첨부 읽기 권한이 필요하다.

## 파일 기반 RAG 화면 흐름

파일 기반 질의응답 화면은 다음 순서를 기준으로 구성한다.

1. 파일 업로드 또는 기존 첨부 선택을 완료한다.
2. `POST /api/mgmt/attachments/{attachmentId}/rag/index`로 인덱싱을 요청한다.
3. 응답에 `X-RAG-Job-Id`가 있으면 `services:ai_rag read` 권한이 있는 운영자 화면에서 job 상세를 polling한다.
   attachment job 로그와 chunk 조회는 추가로 `features:attachment read` 권한이 있을 때만 활성화한다.
4. `GET /api/mgmt/attachments/{attachmentId}/rag/metadata`로 RAG metadata를 확인한다.
5. `POST /api/ai/chat/rag`에 `objectType=attachment`, `objectId=<attachmentId>`를 넣어 질문한다.

재인덱싱 시 서버는 같은 `objectType`/`objectId`의 기존 chunk를 교체한다.
클라이언트는 재인덱싱 완료 후 이전 검색 결과나 미리보기 캐시를 무효화한다.
embedding profile metadata가 없는 기존 chunk를 profile 기반 운영으로 전환하는 절차는
[`Legacy RAG chunk metadata 재색인 가이드`](rag-legacy-chunk-migration.md)를 따른다.

## 벡터 검색 응답 변경

`POST /api/mgmt/ai/vectors/search` 응답 항목에는 `id`와 `documentId`가 함께 온다.

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

클라이언트 기준:

- grid row key는 `id`를 우선 사용한다.
- 기존 화면이 `documentId`를 쓰면 즉시 깨지지는 않지만, 신규 코드는 `id` 기준으로 맞춘다.
- `metadata.objectType`과 `metadata.objectId`는 객체 범위 필터와 상세 링크 구성에 사용한다.

## Diagnostics 표시

RAG diagnostics는 서버 설정과 클라이언트 요청이 모두 허용될 때만 응답에 포함된다.

- 서버 `studio.ai.pipeline.diagnostics.enabled=true`
- 서버 `studio.ai.endpoints.rag.diagnostics.allow-client-debug=true`
- 클라이언트 요청 `debug=true`

검색 진단 응답 위치는 `ChatResponseDto.metadata.ragDiagnostics`다.
context expansion 진단 응답 위치는 `ChatResponseDto.metadata.ragContextDiagnostics`다.
diagnostics에는 chunk 본문이 포함되지 않는다.
화면에는 strategy, result count, threshold, weight, object scope, topK, context expansion 적용 여부 같은 운영 정보만 표시한다.
일반 사용자 화면에서는 기본적으로 숨긴다.

## 오류 처리

현재 서버가 명시적으로 감지하는 Google GenAI quota 또는 rate limit 오류는 HTTP 429 `ProblemDetails`로 내려온다.
클라이언트는 429를 500 장애로 표시하지 말고 재시도 안내 또는 provider quota 확인 안내로 처리한다.
다른 provider 제한 오류는 provider adapter와 예외 형태에 따라 다른 status로 내려올 수 있으므로,
메시지와 `ProblemDetails.detail`을 함께 확인한다.

권장 처리:

- 400: 요청 필드, provider, RAG 조건 오류를 사용자 입력 오류로 표시
- 401/403: 로그인 또는 권한 오류로 표시
- 429: 사용량 제한 또는 일시적 provider 제한으로 표시
- 503: 벡터 스토어 등 AI 의존 구성 미준비로 표시
- 500: 서버 장애로 표시

## 권한 확인

| 기능 | 필요 권한 |
|---|---|
| 채팅 | `services:ai_chat write` |
| RAG 채팅 | `services:ai_chat write`, `services:ai_rag read`, object scope read |
| 첨부 파일 RAG 채팅 | `services:ai_chat write`, `services:ai_rag read`, `features:attachment read` |
| Query rewrite | `services:ai_chat read` |
| Provider 정보 | `services:ai_chat read` 또는 `services:ai_embedding read` |
| 임베딩 생성 | `services:ai_embedding write` |
| 벡터 업서트/검색 | `services:ai_vector read` |
| RAG 인덱싱/검색/job 조회 | `services:ai_rag read` |
| RAG job 생성/retry/cancel | `services:ai_rag write` |
| attachment RAG job 생성/retry/cancel | `features:attachment write` 추가 필요 |
| attachment RAG job logs/chunks/object metadata | `features:attachment read` 추가 필요 |

## 클라이언트 체크리스트

- `/api/ai/embedding`, `/api/ai/vectors`, `/api/ai/rag/*` 호출이 남아 있는지 검색한다.
- 관리용 AI 호출을 `/api/mgmt/ai`로 변경하거나 서버 호환 설정 여부를 확인한다.
- 채팅 화면의 provider 선택값을 `provider` 필드로 전달한다.
- system prompt 입력값을 `systemPrompt` 필드로 전달한다.
- RAG 채팅 화면에서 `objectType`과 `objectId`를 명시한다.
- 파일 재인덱싱 완료 후 RAG 검색 결과 캐시를 비운다.
- 벡터 검색 grid key를 `id` 기준으로 정리한다.
- 429 `ProblemDetails`를 quota/rate limit 메시지로 처리하되, 모든 provider 제한 오류가 429로 통일된다고 가정하지 않는다.
- `metadata.ragDiagnostics`, `metadata.ragContextDiagnostics` 정보는 운영/개발자 화면에서만 노출한다.
