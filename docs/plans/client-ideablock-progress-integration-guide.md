# 클라이언트 IdeaBlock 진행 현황 연동 가이드

## 목적

파일 상세 화면에서 `blockify` 또는 IdeaBlock 기반 청킹이 실제로 적용되었는지 사용자가 확인할 수 있도록 서버의 pipeline progress 응답을 표시한다.

이번 서버 변경으로 Markdown pipeline progress 응답에 `chunking` 집계 정보가 추가된다. 클라이언트는 SQL 조회나 chunk 상세 조회 없이 이 값을 사용해 IdeaBlock 생성 수, fallback 수, source block coverage를 보여줄 수 있다.

## 대상 API

기존 pipeline progress API를 그대로 사용한다.

```http
GET /api/markdown-documents/{markdownDocumentId}/pipeline
```

응답 예시:

```json
{
  "pipeline": {
    "revisionId": "mrev-...",
    "status": "RUNNING",
    "currentStage": "RAG_INDEX",
    "lastCompletedStage": "CHUNKING",
    "errorCode": null,
    "errorMessage": null
  },
  "chunking": {
    "chunkCount": 128,
    "ideaBlockCount": 110,
    "fallbackCount": 18,
    "fallbackReasonCounts": {
      "TABLE_SECTION": 7,
      "ANSWER_TOO_SHORT": 5,
      "COVERAGE_GAP": 6
    },
    "sourceBlockTargetCount": 128,
    "sourceBlockCoveredCount": 128,
    "sourceBlockCoverage": 1.0,
    "averageConfidence": 0.82
  },
  "rag": {
    "jobId": "rag-...",
    "status": "RUNNING",
    "currentStep": "EMBEDDING",
    "chunkCount": 128,
    "embeddedCount": 80,
    "indexedCount": 60,
    "warningCount": 0,
    "errorMessage": null
  }
}
```

`chunking`은 아직 청킹이 실행되지 않았거나 stage 저장소에 데이터가 없으면 `null`일 수 있다.

## 파일 상세 UI 변경

### 1. Pipeline 카드에 Chunking 집계 표시

기존 단계 표시 영역에 다음 값을 추가한다.

| 항목 | 표시값 |
|---|---|
| 전체 Chunk | `chunking.chunkCount` |
| IdeaBlock | `chunking.ideaBlockCount` |
| Fallback | `chunking.fallbackCount` |
| Source Block Coverage | `chunking.sourceBlockCoverage * 100` |
| 평균 Confidence | `chunking.averageConfidence` |

권장 표시:

```text
Chunking
- 전체 Chunk: 128
- IdeaBlock: 110
- Fallback: 18
- Coverage: 100.0%
- 평균 신뢰도: 0.82
```

### 2. Blockify 적용 여부 판단

클라이언트는 다음 기준으로 표시한다.

```ts
const chunking = progress.chunking;
const blockifyApplied =
  !!chunking &&
  chunking.chunkCount > 0 &&
  (chunking.ideaBlockCount > 0 || chunking.fallbackCount > 0);

const blockifyEffective =
  !!chunking &&
  chunking.ideaBlockCount > 0;
```

표시 문구:

- `blockifyEffective=true`: `IdeaBlock 생성됨`
- `blockifyApplied=true`이고 `ideaBlockCount=0`: `전체 Fallback 처리됨`
- `chunking=null`: `청킹 결과 대기 중`
- `chunking.chunkCount=0`: `청킹 결과 없음`

### 3. Fallback 사유 표시

`fallbackCount > 0`이면 접을 수 있는 상세 영역에 `fallbackReasonCounts`를 표시한다.

예:

```text
Fallback 18건
- TABLE_SECTION: 7
- ANSWER_TOO_SHORT: 5
- COVERAGE_GAP: 6
```

사용자에게 보여줄 권장 문구:

```text
일부 source block은 IdeaBlock 생성 조건을 만족하지 않아 structure-based chunk로 보존되었습니다.
```

### 4. Coverage 경고

`sourceBlockCoverage` 기준으로 경고 수준을 나눈다.

| 조건 | UI 상태 | 문구 |
|---|---|---|
| `>= 0.95` | 정상 | `Source block coverage 양호` |
| `>= 0.8 && < 0.95` | 주의 | `일부 source block이 fallback 또는 누락되었을 수 있습니다.` |
| `< 0.8` | 경고 | `IdeaBlock coverage가 낮습니다. structure-based 또는 hybrid 검색을 권장합니다.` |

`sourceBlockTargetCount=0`이면 coverage 판단을 하지 않는다.

### 5. RAG 진행률과 분리 표시

`chunking`은 청킹 결과 품질이고, `rag`는 embedding/indexing 진행률이다. 두 값을 섞어서 하나의 진행률로 계산하지 않는다.

권장 구성:

```text
Markdown: COMPLETED
Chunking: COMPLETED
  - IdeaBlock 110 / Fallback 18 / Coverage 100%
RAG Indexing: RUNNING
  - Embedded 80 / 128
  - Indexed 60 / 128
Skill Extraction: PENDING
```

## 재시도/재추출 버튼 정책

### Reindex

Markdown은 유지하고 청킹/RAG만 다시 실행할 때 사용한다.

권장 조건:

- 같은 Markdown에 대해 `chunkingStrategy`, chunk size, embedding model만 바꾸는 경우
- `chunking.fallbackCount`가 너무 많아 chunking option을 바꾸는 경우

### Reextract

Markdown 추출부터 다시 해야 할 때 사용한다.

권장 조건:

- 원본 파일 내용이 바뀐 경우
- Markdown 자체가 비어 있거나 깨진 경우
- PDF/OCR 추출 옵션을 바꾸는 경우

## 클라이언트 요청 옵션

Blockify 실행 요청 시 기존 옵션을 유지한다.

```json
{
  "runChunking": true,
  "runRagIndex": true,
  "runSkillExtraction": false,
  "chunkingStrategy": "blockify",
  "chunkMaxSize": 800,
  "chunkOverlap": 100,
  "chunkUnit": "token",
  "embeddingProfileId": "retrieval-ko-kure",
  "blockifyLlmProvider": "google-ai-gemini",
  "blockifyLlmModel": "gemini-2.5-flash",
  "blockifyPiiMaskingEnabled": true
}
```

`blockifyPiiMaskingEnabled`의 기본 UI 값은 `true`로 둔다.

## 오류 처리

서버가 `Invalid blockify chunk metadata` 또는 `Blockify chunking produced no chunks`를 반환하면 클라이언트는 일반 실패가 아니라 Blockify 생성 검증 실패로 표시한다.

권장 문구:

```text
Blockify 결과 검증에 실패했습니다. IdeaBlock 필수 metadata가 생성되지 않아 잘못된 성공 저장을 차단했습니다.
```

사용자 액션:

- `structure-based`로 재시도
- `hybrid` 검색 전략 사용
- Blockify LLM 모델 또는 PII masking 설정 확인

## 완료 기준

- 파일 상세에서 IdeaBlock count와 fallback count를 확인할 수 있다.
- fallback reason을 사용자가 볼 수 있다.
- Coverage가 낮을 때 경고가 표시된다.
- RAG embedding 진행률과 IdeaBlock 생성 품질이 분리되어 표시된다.
- `chunking=null` 상태를 오류로 처리하지 않는다.
- Blockify 실패 시 잘못된 성공으로 표시하지 않는다.
