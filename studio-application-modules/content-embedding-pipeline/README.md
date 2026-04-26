# Content Embedding Pipeline

첨부파일 텍스트를 추출하고 임베딩을 생성해 벡터 스토어 업서트 또는 RAG 인덱싱을 수행하는 모듈이다.
`studio-application-modules:attachment-service`와 함께 사용하며, AI 플랫폼 포트(`studio-platform-ai`)에 의존한다.

## 사용 요약

- attachment 모듈과 함께 사용한다.
- 임베딩 생성에는 `EmbeddingPort` 빈이 필요하다.
- 벡터 저장에는 `VectorStorePort` 빈이 필요하다(선택).
- RAG 인덱싱에는 `RagPipelineService` 빈이 필요하다(선택).
- `starter:studio-platform-starter-chunking`이 있으면 RAG 인덱싱 시 chunk metadata가 vector metadata에 추가된다.

## 파이프라인 흐름

```
첨부파일 ID
    │
    ▼
AttachmentService.getInputStream()    ← attachment-service 모듈 제공
    │
    ▼
FileContentExtractionService.extractText()   ← 임베딩 생성 경로
FileContentExtractionService.parseStructured() ← 구조화 RAG 색인 경로
    │
    ▼
EmbeddingPort.embed()                 ← studio-platform-ai 어댑터 제공
    │  임베딩 벡터 생성
    ▼
    ├─ [storeVector=true] VectorStorePort.upsert()    ← 선택적 벡터 저장
    │
    └─ [RAG 인덱싱]
        ├─ [구조화 빈 사용 가능] ChunkingOrchestrator + VectorStorePort.replaceRecordsByObject()
        └─ [fallback] RagPipelineService.index()
```

RAG 색인은 `TextractNormalizedDocumentAdapter`, `ChunkingOrchestrator`, `EmbeddingPort`, `VectorStorePort`가 모두 있으면
구조화 문서 청킹 경로를 자동 사용한다. 구조화 경로는 `VectorRecord.builder()`로 chunk 저장 단위를 만든 뒤
`VectorStorePort.replaceRecordsByObject()`를 호출한다. 하나라도 없으면 기존 `RagPipelineService.index()` 경로로 fallback한다.
`studio.ai.pipeline.cleaner.enabled=true`이면 `RagPipelineService`가 chunking 전에 추출 텍스트를 정제한다.

## 주요 서비스 클래스와 역할

| 클래스 | 역할 |
|--------|------|
| `AttachmentEmbeddingPipelineController` | 임베딩 생성/저장, 벡터 존재 여부 확인, RAG 인덱싱/검색 REST 엔드포인트 제공 |
| `AttachmentRagIndexService` | attachment RAG 색인 실행 흐름을 controller와 job executor가 함께 재사용하는 service |
| `AttachmentRagIndexJobSourceExecutor` | `sourceType=attachment` RAG index job을 기존 attachment 색인 흐름으로 실행 |
| `FileContentExtractionService` | 파일 MIME 타입과 이름을 기반으로 텍스트 추출. 구현체는 `studio-platform-textract-starter`가 제공한다 |
| `TextractNormalizedDocumentAdapter` | `ParsedFile`을 structure-based chunking 입력인 `NormalizedDocument`로 변환한다 |
| `EmbeddingPort` | 텍스트 리스트를 받아 임베딩 벡터 반환. `studio-platform-ai` AI 어댑터(OpenAI 등)가 구현체를 제공한다 |
| `VectorStorePort` | 벡터 문서 업서트/존재 확인/메타데이터 조회. 벡터 DB 어댑터가 구현체를 제공한다 |
| `RagPipelineService` | 텍스트 인덱싱 및 시맨틱 검색. RAG 파이프라인 스타터가 구현체를 제공한다 |

## 전제 조건 빈

| 빈 타입 | 필수 여부 | 제공 스타터/모듈 |
|---------|----------|-----------------|
| `AttachmentService` | 필수 | `:starter:studio-application-starter-attachment` |
| `FileContentExtractionService` | 임베딩/텍스트 추출 시 필수 | `starter:studio-platform-textract-starter` |
| `EmbeddingPort` | 임베딩 생성 시 필수 | `studio-platform-ai` AI 어댑터 (예: OpenAI 스타터) |
| `VectorStorePort` | 벡터 저장 시 필수 | 벡터 DB 어댑터 (예: pgvector, Qdrant 스타터) |
| `RagPipelineService` | RAG 인덱싱/검색 시 필수 | RAG 파이프라인 스타터 |
| `ChunkingOrchestrator` | RAG chunking 확장 시 선택 | `starter:studio-platform-starter-chunking` |
| `TextractNormalizedDocumentAdapter` | 구조화 RAG 색인 시 선택 | `starter:studio-platform-starter-chunking` |

빈이 없는 경우 해당 엔드포인트는 `501 NOT_IMPLEMENTED`를 반환한다.
구조화 색인에 필요한 선택 빈이 부족하면 기존 `RagPipelineService.index()` fallback이 사용된다.

## REST 엔드포인트

컨트롤러는 `studio.features.attachment.web.mgmt-base-path`(기본 `/api/mgmt/attachments`) 경로에 등록된다.
따라서 attachment-service의 `web.enabled=true`와 `mgmt-base-path` 설정을 공유한다.
`content-embedding-pipeline`은 auto-configuration으로 `AttachmentRagIndexService`,
`AttachmentRagIndexJobSourceExecutor`, 기본 구조화 색인 adapter를 등록한다.
컨트롤러는 attachment web endpoint 설정과 같은 base path를 사용하며, 기존 attachment endpoint component scan
또는 애플리케이션 component scan으로 등록된다.

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
  "debug": true,
  "keywords": ["spring", "java"],
  "useLlmKeywordExtraction": false,
  "metadata": {
    "category": "tech"
  }
}
```

서버 `studio.ai.endpoints.rag.diagnostics.allow-client-debug=true`와 요청 `debug=true`가 모두 설정되면
`202 Accepted` 응답 헤더에 안전한 색인 diagnostics를 추가한다.
`RagIndexJobService`가 있으면 기존 응답 body 없이 `X-RAG-Job-Id` 헤더도 함께 반환해
`starter-ai-web`의 RAG job management API에서 상태와 로그를 추적할 수 있다.
`starter-ai-web`의 `POST /api/mgmt/ai/rag/jobs`에서도 `sourceType=attachment`, `objectType=attachment`,
`objectId=<attachmentId>`를 보내면 동일한 attachment RAG 색인 흐름을 비동기 job으로 실행한다.
헤더는 `X-RAG-Index-Path`, `X-RAG-Index-Structured`, `X-RAG-Index-Fallback-Reason`,
`X-RAG-Index-Parsed-Block-Count`, `X-RAG-Index-Chunk-Count`, `X-RAG-Index-Vector-Count`,
`X-RAG-Job-Id`만 사용한다.
본문 텍스트, snippet, embedding vector, 사용자 metadata 값은 diagnostics 헤더에 포함하지 않는다.

첨부 RAG 인덱싱은 metadata에 아래 값을 `putIfAbsent`로 보강한다. 요청 metadata에 같은 key가 있으면 요청 값을 유지한다.
공통 RAG metadata key의 표준 의미와 `chunkIndex`/`chunkOrder` 호환 기준은
[`studio-platform-ai` RAG metadata key reference](../../studio-platform-ai/README.md#rag-metadata-key-reference)를 따른다.

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
  "topK": 5,
  "objectType": "attachment",
  "objectId": "101"
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
    implementation(project(":starter:studio-platform-starter-chunking"))
    // 텍스트 추출기
    implementation(project(":starter:studio-platform-textract-starter"))
    // AI 어댑터 (EmbeddingPort + VectorStorePort 제공)
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
