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
| 오류 처리 | provider quota/rate limit은 HTTP 429 `ProblemDetails`로 처리 |
| RAG diagnostics | 서버와 요청이 모두 허용할 때만 `metadata.ragDiagnostics` 표시 |

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
| RAG 색인 job 재시도 | `POST` | `/api/mgmt/ai/rag/jobs/{jobId}/retry` |
| RAG object chunk 조회 | `GET` | `/api/mgmt/ai/rag/objects/{objectType}/{objectId}/chunks` |
| RAG object metadata 조회 | `GET` | `/api/mgmt/ai/rag/objects/{objectType}/{objectId}/metadata` |

기존 `/api/ai/embedding`, `/api/ai/vectors`, `/api/ai/rag/*` 경로를 계속 쓰는 환경은
서버에서 `studio.ai.endpoints.mgmt-base-path=/api/ai`로 호환 설정했는지 확인한다.
호환 설정이 없으면 클라이언트 호출 경로를 `/api/mgmt/ai`로 바꾼다.

## RAG 색인 운영 화면

기존 `POST /api/mgmt/ai/rag/index`와 `POST /api/mgmt/attachments/{attachmentId}/rag/index`는
응답 body 없이 `202 Accepted`를 유지한다. 서버가 job tracking을 제공하면 응답 헤더
`X-RAG-Job-Id`가 추가되므로, 클라이언트는 이 값을 이용해 job 상세와 로그를 조회할 수 있다.

신규 `POST /api/mgmt/ai/rag/jobs`는 첫 범위에서 raw text 색인만 실행한다. 요청에는 `objectType`,
`objectId`, `text`가 필요하며, source-only attachment 실행은 기존 attachment RAG index API를 사용한다.
생성 후 운영 화면은 아래 순서로 동작한다.

1. `POST /api/mgmt/ai/rag/jobs`로 job을 생성한다.
2. `GET /api/mgmt/ai/rag/jobs/{jobId}`를 polling해 `status`, `currentStep`, `chunkCount`, `embeddedCount`, `indexedCount`를 표시한다.
3. `GET /api/mgmt/ai/rag/jobs/{jobId}/logs`로 단계별 `INFO`/`WARN`/`ERROR` 로그를 표시한다.
4. 완료 후 `GET /api/mgmt/ai/rag/jobs/{jobId}/chunks` 또는 object chunk API로 색인 결과를 보여준다.
5. `FAILED`, `SUCCEEDED`, `WARNING`, `CANCELLED` 상태에서만 retry 버튼을 활성화한다. `PENDING`/`RUNNING` retry는 `409 Conflict`로 처리한다.

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

- 파일 기반 답변은 `objectType=attachment`, `objectId=<attachmentId>`를 보낸다.
- `ragQuery`가 있으면 객체 범위 안에서 검색한다.
- `ragQuery`가 없고 객체 범위만 있으면 저장된 chunk를 순서대로 컨텍스트에 사용한다.
- `ragTopK`는 화면의 최대 참고 문서 수와 맞춘다.
- `debug=true`는 운영 화면 또는 개발자 도구에서만 사용한다.
- 첨부 파일 RAG 채팅은 기본 AI chat 권한 외에 첨부 읽기 권한이 필요하다.

## 파일 기반 RAG 화면 흐름

파일 기반 질의응답 화면은 다음 순서를 기준으로 구성한다.

1. 파일 업로드 또는 기존 첨부 선택을 완료한다.
2. `POST /api/mgmt/attachments/{attachmentId}/rag/index`로 인덱싱을 요청한다.
3. `GET /api/mgmt/attachments/{attachmentId}/rag/metadata`로 RAG metadata를 확인한다.
4. `POST /api/ai/chat/rag`에 `objectType=attachment`, `objectId=<attachmentId>`를 넣어 질문한다.

재인덱싱 시 서버는 같은 `objectType`/`objectId`의 기존 chunk를 교체한다.
클라이언트는 재인덱싱 완료 후 이전 검색 결과나 미리보기 캐시를 무효화한다.

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

응답 위치는 `ChatResponseDto.metadata.ragDiagnostics`다.
diagnostics에는 chunk 본문이 포함되지 않는다.
화면에는 strategy, result count, threshold, weight, object scope, topK 같은 운영 정보만 표시한다.
일반 사용자 화면에서는 기본적으로 숨긴다.

## 오류 처리

AI provider quota 또는 rate limit 오류는 HTTP 429 `ProblemDetails`로 내려온다.
클라이언트는 500 장애로 표시하지 말고 재시도 안내 또는 provider quota 확인 안내로 처리한다.

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
| RAG 채팅 | `services:ai_chat write` |
| 첨부 파일 RAG 채팅 | `services:ai_chat write`, `features:attachment read` |
| Query rewrite | `services:ai_chat read` |
| Provider 정보 | `services:ai_chat read` 또는 `services:ai_embedding read` |
| 임베딩 생성 | `services:ai_embedding write` |
| 벡터 업서트/검색 | `services:ai_vector read` |
| RAG 인덱싱/검색 | `services:ai_rag read` |

## 클라이언트 체크리스트

- `/api/ai/embedding`, `/api/ai/vectors`, `/api/ai/rag/*` 호출이 남아 있는지 검색한다.
- 관리용 AI 호출을 `/api/mgmt/ai`로 변경하거나 서버 호환 설정 여부를 확인한다.
- 채팅 화면의 provider 선택값을 `provider` 필드로 전달한다.
- system prompt 입력값을 `systemPrompt` 필드로 전달한다.
- RAG 채팅 화면에서 `objectType`과 `objectId`를 명시한다.
- 파일 재인덱싱 완료 후 RAG 검색 결과 캐시를 비운다.
- 벡터 검색 grid key를 `id` 기준으로 정리한다.
- 429 `ProblemDetails`를 quota/rate limit 메시지로 처리한다.
- diagnostics 정보는 운영/개발자 화면에서만 노출한다.
