# Content Embedding Pipeline

첨부파일 텍스트를 추출하고 임베딩을 생성해 벡터 스토어 업서트 또는 RAG 인덱싱을 수행하는 모듈이다.
`studio-application-modules:attachment-service`와 함께 사용하며, AI 플랫폼 포트(`studio-platform-ai`)에 의존한다.

## 사용 요약

- attachment 모듈과 함께 사용한다.
- 임베딩 생성에는 `EmbeddingPort` 빈이 필요하다.
- 벡터 저장에는 `VectorStorePort` 빈이 필요하다(선택).
- RAG 인덱싱에는 `RagPipelineService` 빈이 필요하다(선택).

## 파이프라인 흐름

```
첨부파일 ID
    │
    ▼
AttachmentService.getInputStream()    ← attachment-service 모듈 제공
    │
    ▼
FileContentExtractionService.extractText()   ← studio-platform-ai 또는 별도 구현 제공
    │  텍스트 추출 (PDF, DOCX, 텍스트 등)
    ▼
EmbeddingPort.embed()                 ← studio-platform-ai 어댑터 제공
    │  임베딩 벡터 생성
    ▼
    ├─ [storeVector=true] VectorStorePort.upsert()    ← 선택적 벡터 저장
    │
    └─ [RAG 인덱싱] RagPipelineService.index()        ← 선택적 RAG 인덱스 등록
```

청킹은 현재 단일 텍스트 단위로 처리되며, 멀티청크 지원은 `RagPipelineService` 구현체에 위임된다.
`studio.ai.pipeline.cleaner.enabled=true`이면 `RagPipelineService`가 chunking 전에 추출 텍스트를 정제한다.

## 주요 서비스 클래스와 역할

| 클래스 | 역할 |
|--------|------|
| `AttachmentEmbeddingPipelineController` | 임베딩 생성/저장, 벡터 존재 여부 확인, RAG 인덱싱/검색 REST 엔드포인트 제공 |
| `FileContentExtractionService` | 파일 MIME 타입과 이름을 기반으로 텍스트 추출. 구현체는 `studio-platform-ai` 또는 별도 스타터가 제공한다 |
| `EmbeddingPort` | 텍스트 리스트를 받아 임베딩 벡터 반환. `studio-platform-ai` AI 어댑터(OpenAI 등)가 구현체를 제공한다 |
| `VectorStorePort` | 벡터 문서 업서트/존재 확인/메타데이터 조회. 벡터 DB 어댑터가 구현체를 제공한다 |
| `RagPipelineService` | 텍스트 인덱싱 및 시맨틱 검색. RAG 파이프라인 스타터가 구현체를 제공한다 |

## 전제 조건 빈

| 빈 타입 | 필수 여부 | 제공 스타터/모듈 |
|---------|----------|-----------------|
| `AttachmentService` | 필수 | `:starter:studio-application-starter-attachment` |
| `FileContentExtractionService` | 임베딩/텍스트 추출 시 필수 | `studio-platform-ai` 또는 별도 텍스트 추출 스타터 |
| `EmbeddingPort` | 임베딩 생성 시 필수 | `studio-platform-ai` AI 어댑터 (예: OpenAI 스타터) |
| `VectorStorePort` | 벡터 저장 시 필수 | 벡터 DB 어댑터 (예: pgvector, Qdrant 스타터) |
| `RagPipelineService` | RAG 인덱싱/검색 시 필수 | RAG 파이프라인 스타터 |

빈이 없는 경우 해당 엔드포인트는 `501 NOT_IMPLEMENTED`를 반환한다.

## REST 엔드포인트

컨트롤러는 `studio.features.attachment.web.mgmt-base-path`(기본 `/api/mgmt/attachments`) 경로에 등록된다.
따라서 attachment-service의 `web.enabled=true`와 `mgmt-base-path` 설정을 공유한다.

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `GET` | `/{attachmentId}/embedding` | 텍스트 추출 + 임베딩 생성. `storeVector=true`(기본)이면 벡터 스토어에 업서트 | `features:attachment/write` |
| `GET` | `/{attachmentId}/embedding/exists` | 벡터 스토어에 해당 첨부파일 벡터 존재 여부 확인 | `features:attachment/write` |
| `GET` | `/{attachmentId}/rag/metadata` | 벡터 스토어에서 첨부파일 메타데이터 조회 | `features:attachment/read` |
| `POST` | `/{attachmentId}/rag/index` | 첨부파일 내용을 RAG 인덱스에 등록 | `features:attachment/write` |
| `POST` | `/rag/search` | RAG 인덱스에 대해 시맨틱 검색 수행 | `features:attachment/read` |

### 임베딩 생성 예시

```bash
# 텍스트 추출 + 임베딩 생성 + 벡터 저장
GET /api/mgmt/attachments/101/embedding?storeVector=true
Authorization: Bearer <token>

# 벡터 저장 없이 임베딩만 반환
GET /api/mgmt/attachments/101/embedding?storeVector=false
```

### RAG 인덱싱 요청 예시

```bash
POST /api/mgmt/attachments/101/rag/index
Authorization: Bearer <token>
Content-Type: application/json

{
  "documentId": "doc-101",
  "objectType": "forums-post",
  "objectId": "42",
  "keywords": ["spring", "java"],
  "useLlmKeywordExtraction": false,
  "metadata": {
    "category": "tech"
  }
}
```

첨부 RAG 인덱싱은 metadata에 아래 값을 `putIfAbsent`로 보강한다. 요청 metadata에 같은 key가 있으면 요청 값을 유지한다.

| key | 설명 |
|---|---|
| `objectType` | 기본값 `attachment` 또는 요청 값 |
| `objectId` | 기본값 attachment ID 문자열 또는 요청 값 |
| `attachmentId` | 첨부 ID |
| `name` | 첨부 원본 이름 |
| `filename` | 첨부 파일명. 클라이언트 표시용 alias |
| `sourceType` | `attachment` |
| `indexedAt` | RAG 인덱싱 요청 시각 |
| `contentType` | 첨부 MIME 타입 |
| `size` | 첨부 크기 |

### RAG 검색 요청 예시

```bash
POST /api/mgmt/attachments/rag/search
Authorization: Bearer <token>
Content-Type: application/json

{
  "query": "Spring Boot 자동 구성 방법",
  "topK": 5
}
```

## attachment-service와의 통합 방법

1. `studio-application-starter-attachment`로 `AttachmentService` 빈을 활성화한다.
2. `content-embedding-pipeline` 모듈을 의존성에 추가한다.
3. `EmbeddingPort` 구현체(AI 어댑터 스타터)를 추가한다.
4. 필요에 따라 `VectorStorePort`, `RagPipelineService` 구현체 스타터를 추가한다.

```kotlin
dependencies {
    implementation(project(":starter:studio-application-starter-attachment"))
    implementation(project(":studio-application-modules:content-embedding-pipeline"))
    // AI 어댑터 (EmbeddingPort + FileContentExtractionService + VectorStorePort 제공)
    implementation(project(":starter:studio-platform-starter-ai"))
    // provider 라이브러리는 직접 선언 (예: OpenAI)
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    // VectorStorePort는 JdbcTemplate이 컨텍스트에 있으면 studio-platform-starter-ai가 pgvector 기반으로 자동 구성
}
```

## 제공 기능

- 첨부 본문 텍스트 추출 (PDF, DOCX, 텍스트 등)
- 임베딩 벡터 생성
- 벡터 스토어 업서트 및 존재 여부 확인
- RAG 인덱스 등록 및 시맨틱 검색
- 메타데이터 조회
