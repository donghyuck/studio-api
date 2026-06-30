# 클라이언트 수정 지시문: IdeaBlock Merge Preview / Apply / Reindex

## Summary

서버는 IdeaBlock 후보 cluster를 조회하고, LLM preview로 검증한 뒤, 사용자가 승인한 plan만 stage에 반영할 수 있다.

클라이언트는 파일 상세 화면에서 다음 흐름을 제공한다.

```text
IdeaBlock Summary
→ Merge Preview
→ 사용자 승인
→ Merge Apply
→ 선택적 RAG 재색인
→ 진행 상태 polling
```

기본 원칙:

- `merge-preview`는 읽기 전용이다.
- `merge-apply`는 `planFingerprint`가 일치하고 `applicable=true`인 plan만 적용한다.
- `merge-apply`만 호출하면 stage만 바뀐다.
- `runRagIndex=true`를 같이 보내면 서버가 기존 pipeline resume 경로로 RAG 재색인을 시작한다.
- `merge-undo`는 merged stage의 snapshot을 사용해 원본 IdeaBlock stage를 복원한다.
- `merge-apply-batch`는 여러 plan을 순차 적용하고 성공/실패를 분리해 반환한다.

## 1. 화면 진입 조건

파일 상세에서 다음 조건을 만족할 때 IdeaBlock merge UI를 표시한다.

- Markdown revision이 `COMPLETED`
- chunking strategy가 `blockify`
- IdeaBlock summary API가 하나 이상의 merge candidate cluster를 반환

사용할 API:

```http
GET /api/markdown-documents/{documentId}/revisions/{revisionId}/ideablocks/summary
```

주요 표시값:

- `coverage`
- `ideaBlockCount`
- `fallbackCount`
- `mergeCandidateCount`
- `similarityClusterCount`
- `embeddingMergeCandidateCount`
- `embeddingSimilarityClusterCount`
- `mergeCandidateClusters`
- `embeddingCandidateClusters`
- `samples`

권장 UI:

- “병합 후보” 탭
- “Embedding 유사 후보”와 “Lexical 후보” 구분
- cluster별 `chunkIds`, `size`, `maxScore`, `policy` 표시

## 2. Merge Preview

사용자가 cluster를 선택하면 preview API를 호출한다.

```http
POST /api/markdown-documents/{documentId}/revisions/{revisionId}/ideablocks/merge-preview
```

요청 예:

```json
{
  "clusterId": "sim-1",
  "preferEmbeddingClusters": false,
  "llmProvider": "google-ai-gemini",
  "llmModel": "gemini-2.5-flash",
  "maxClusters": 1
}
```

응답에서 반드시 확인할 필드:

```json
{
  "clusters": [
    {
      "clusterId": "sim-1",
      "clusterType": "lexical",
      "status": "PREVIEW",
      "reason": "LLM_PREVIEW",
      "criticalQuestion": "...",
      "trustedAnswer": "...",
      "sourceEvidence": [],
      "sourceBlockRanges": [],
      "previewText": "...",
      "planId": "merge-plan:lexical:sim-1",
      "planFingerprint": "sha256:...",
      "applicable": true,
      "validationWarnings": [],
      "mergedFromChunkIds": ["chunk-1", "chunk-2"]
    }
  ]
}
```

클라이언트 처리:

- `applicable=true`인 경우에만 “병합 적용” 버튼 활성화
- `applicable=false`이면 버튼 비활성화
- `validationWarnings`를 사용자에게 표시
- `LLM_PREVIEW_INVALID`인 경우 warning 목록을 그대로 표시
- `planFingerprint`를 숨은 승인 토큰처럼 보관
- 사용자가 preview를 다시 생성하면 이전 `planFingerprint`는 폐기

주의:

- deterministic preview 또는 LLM 미설정 preview는 보통 `applicable=false`다.
- LLM preview라도 필수 섹션이 없거나, `REJECT`가 포함되거나, 숫자/날짜/조건 token이 누락되면 `applicable=false`다.
- 서버는 apply 시 preview를 다시 계산하므로 오래된 `planFingerprint`는 실패할 수 있다.

## 3. Merge Apply

사용자가 preview를 승인하면 apply API를 호출한다.

```http
POST /api/markdown-documents/{documentId}/revisions/{revisionId}/ideablocks/merge-apply
```

stage만 병합하는 요청:

```json
{
  "clusterId": "sim-1",
  "preferEmbeddingClusters": false,
  "llmProvider": "google-ai-gemini",
  "llmModel": "gemini-2.5-flash",
  "maxClusters": 1,
  "planFingerprint": "sha256:..."
}
```

병합 후 RAG 재색인까지 시작하는 요청:

```json
{
  "clusterId": "sim-1",
  "preferEmbeddingClusters": false,
  "llmProvider": "google-ai-gemini",
  "llmModel": "gemini-2.5-flash",
  "maxClusters": 1,
  "planFingerprint": "sha256:...",
  "runRagIndex": true,
  "runSkillExtraction": false,
  "embeddingProfileId": "retrieval-ko-kure",
  "useLlmKeywordExtraction": true
}
```

응답 예:

```json
{
  "documentId": "mdoc-...",
  "revisionId": "mrev-...",
  "planId": "merge-plan:lexical:sim-1",
  "planFingerprint": "sha256:...",
  "mergedChunkId": "ideablock-merged-...",
  "mergedFromChunkIds": ["chunk-1", "chunk-2"],
  "beforeChunkCount": 12,
  "afterChunkCount": 11,
  "pipelineResult": {
    "resumedPhase": "PIPELINE",
    "resumedFrom": "RAG_INDEX"
  }
}
```

클라이언트 처리:

- `mergedChunkId`와 `mergedFromChunkIds`를 병합 완료 UI에 표시
- `beforeChunkCount`와 `afterChunkCount` 차이를 표시
- `pipelineResult`가 있으면 pipeline 진행 상태 polling 시작
- `pipelineResult`가 없으면 “병합 적용됨, 재색인은 아직 실행하지 않음” 상태로 표시

## 4. 진행 상태 Polling

`runRagIndex=true`로 apply한 경우 다음 API를 polling한다.

```http
GET /api/markdown-documents/{documentId}/pipeline/progress
```

표시 권장:

- `execution.status`
- `execution.currentStage`
- `ragProgress.status`
- `ragProgress.currentStep`
- `ragProgress.chunkCount`
- `ragProgress.embeddedCount`
- `ragProgress.indexedCount`
- `ragProgress.errorMessage`

종료 조건:

- `execution.status=COMPLETED`
- 또는 `execution.status=FAILED`
- 또는 `ragProgress.status=SUCCEEDED|FAILED|WARNING`

실패 시:

- `errorMessage`를 표시
- “RAG 재시도” 버튼은 기존 pipeline resume API를 사용
- merge 자체를 되돌리는 공개 API는 아직 없으므로 “병합 되돌리기” 버튼은 노출하지 않는다
- 다만 서버는 merged stage metadata에 `ideaBlockDistillationSourceChunks` snapshot을 저장하므로, 상세/진단 화면에서는 원본 chunk 목록을 표시할 수 있다

## 5. Merge Undo

병합 결과를 되돌릴 때는 undo API를 사용한다.

```http
POST /api/markdown-documents/{documentId}/revisions/{revisionId}/ideablocks/merge-undo
```

요청 예:

```json
{
  "mergedChunkId": "ideablock-merged-...",
  "planFingerprint": "sha256:...",
  "runRagIndex": true,
  "embeddingProfileId": "retrieval-ko-kure"
}
```

응답 예:

```json
{
  "documentId": "mdoc-...",
  "revisionId": "mrev-...",
  "mergedChunkId": "ideablock-merged-...",
  "planFingerprint": "sha256:...",
  "restoredChunkIds": ["chunk-1", "chunk-2"],
  "beforeChunkCount": 11,
  "afterChunkCount": 12,
  "pipelineResult": {
    "resumedPhase": "PIPELINE",
    "resumedFrom": "RAG_INDEX"
  }
}
```

UI 처리:

- merged chunk 상세에서만 “병합 되돌리기” 버튼을 노출한다.
- `ideaBlockDistillationSourceChunks`가 없는 chunk에는 undo 버튼을 노출하지 않는다.
- undo 후 `pipelineResult`가 있으면 pipeline progress polling을 시작한다.

## 6. Batch Merge Apply

여러 cluster를 한 번에 적용할 때는 batch API를 사용한다.

```http
POST /api/markdown-documents/{documentId}/revisions/{revisionId}/ideablocks/merge-apply-batch
```

요청 예:

```json
{
  "items": [
    {
      "clusterId": "sim-1",
      "preferEmbeddingClusters": false,
      "llmProvider": "google-ai-gemini",
      "llmModel": "gemini-2.5-flash",
      "maxClusters": 1,
      "planFingerprint": "sha256:..."
    }
  ],
  "runRagIndex": true,
  "embeddingProfileId": "retrieval-ko-kure"
}
```

응답은 성공/실패가 분리된다.

```json
{
  "applied": [
    {
      "planId": "merge-plan:lexical:sim-1",
      "mergedChunkId": "ideablock-merged-...",
      "mergedFromChunkIds": ["chunk-1", "chunk-2"]
    }
  ],
  "failed": [
    {
      "planFingerprint": "sha256:...",
      "errorMessage": "IdeaBlock merge plan was not found or is stale"
    }
  ],
  "pipelineResult": {
    "resumedPhase": "PIPELINE",
    "resumedFrom": "RAG_INDEX"
  }
}
```

UI 처리:

- batch 적용 전 선택된 cluster 목록과 source evidence를 확인 dialog에 표시한다.
- `failed`가 있어도 `applied`가 있으면 summary와 progress를 재조회한다.
- 동일 source chunk가 여러 plan에 걸쳐 있으면 일부 plan은 stale 처리될 수 있으므로 실패 항목을 사용자에게 표시한다.

## 6.1 Auto Merge Apply

원 Blockify에 가까운 자동 distillation 흐름은 auto apply API를 사용한다.

```http
POST /api/markdown-documents/{documentId}/revisions/{revisionId}/ideablocks/merge-auto-apply
```

요청 예:

```json
{
  "preferEmbeddingClusters": true,
  "llmProvider": "google-ai-gemini",
  "llmModel": "gemini-2.5-flash",
  "maxClusters": 5,
  "runRagIndex": true,
  "embeddingProfileId": "retrieval-ko-kure"
}
```

서버 동작:

- 내부적으로 merge preview를 먼저 생성한다.
- `applicable=true`이고 `validationWarnings=[]`인 plan만 자동 적용한다.
- 검증 실패 cluster는 `failed`에 사유를 담아 반환한다.
- 적용된 plan이 하나 이상이고 `runRagIndex=true`이면 RAG 재색인을 시작한다.

클라이언트 처리:

- “자동 병합 적용” 버튼은 고급/실험 기능으로 표시한다.
- 적용 전 summary의 `embeddingCandidateClusters` 또는 `mergeCandidateClusters` 개수를 보여준다.
- 응답의 `applied`와 `failed`를 분리 표시한다.
- `pipelineResult`가 있으면 pipeline progress polling을 시작한다.
- 적용 후 summary를 다시 조회한다.

주의:

- Auto apply는 LLM preview가 검증을 통과한 plan만 적용한다.
- LLM 미설정, fact token mismatch, source evidence 부족, 필수 섹션 누락은 자동 적용되지 않는다.
- 자동 적용 후 되돌리려면 개별 merged chunk 기준으로 `merge-undo`를 사용한다.

## 7. 품질 검증 표시

`merge-preview` 응답의 `validationWarnings`는 다음처럼 표시한다.

| warning | 의미 | UI 처리 |
|---|---|---|
| `LLM_NOT_CONFIGURED` | LLM preview를 만들 수 없음 | provider/model 설정 안내 |
| `LLM_REJECTED_MERGE` | LLM이 병합 불가로 판단 | 적용 버튼 비활성화 |
| `MISSING_CRITICAL_QUESTION_SECTION` | preview에 Critical Question 섹션 없음 | 적용 버튼 비활성화 |
| `MISSING_TRUSTED_ANSWER_SECTION` | preview에 Trusted Answer 섹션 없음 | 적용 버튼 비활성화 |
| `FACT_TOKEN_MISSING:*` | 숫자/날짜/기간/조건 token 누락 | 적용 버튼 비활성화, 원문 대조 안내 |

권장 표시:

```text
이 병합안은 적용할 수 없습니다.
- Trusted Answer 섹션이 없습니다.
- 원문 수치 "30일"이 병합 답변에 없습니다.
```

## 8. 검색/평가 API

병합 전후 성능 비교는 수동 SQL 대신 기존 평가 API를 사용한다.

```http
POST /api/ai/chat/rag/evaluations
```

요청 예:

```json
{
  "strategies": ["structure", "ideaBlock", "hybrid"],
  "objectType": "attachment",
  "objectId": "1",
  "embeddingProfileId": "retrieval-ko-kure",
  "topK": 5,
  "minScore": 0.6,
  "retrievalOptions": {
    "structureTopK": 5,
    "ideaBlockTopK": 5,
    "finalTopK": 5,
    "dedupe": true,
    "queryExpansionEnabled": false
  },
  "questions": [
    {
      "query": "여름 휴가 규정이 있는가",
      "expectedContentContains": ["휴가"]
    }
  ]
}
```

응답은 서버 run history에 저장되며 `runId`를 반환한다.

```json
{
  "runId": "reval-...",
  "createdAt": "2026-06-27T00:00:00Z",
  "objectType": "attachment",
  "objectId": "1",
  "embeddingProfileId": "retrieval-ko-kure",
  "topK": 5,
  "minScore": 0.6,
  "strategies": []
}
```

응답에서 strategy별 `hitRate`, `mrr`, `averageElapsedMs`를 비교한다.

평가 이력 조회:

```http
GET /api/ai/chat/rag/evaluations
GET /api/ai/chat/rag/evaluations/{runId}
```

병합 전후 비교:

```http
POST /api/ai/chat/rag/evaluations/compare
```

```json
{
  "beforeRunId": "reval-before",
  "afterRunId": "reval-after"
}
```

비교 응답의 `hitRateDelta`, `mrrDelta`, `averageElapsedMsDelta`를 이용해 개선/악화를 표시한다.

주의:

- JDBC/Flyway가 활성화된 서버에서는 평가 이력이 `tb_ai_rag_retrieval_evaluation`에 저장되어 서버 재시작 후에도 조회 가능하다.
- JDBC가 없는 테스트/경량 구동 환경에서는 서버 런타임 bounded history로 fallback된다.

distilled IdeaBlock이 검색되면 result metadata preview에 다음 값이 포함될 수 있다.

- `ideaBlockDistilled`
- `ideaBlockDistillationFingerprint`
- `ideaBlockDistilledFromChunkIds`

distilled IdeaBlock 우선순위 실험이 필요하면 `retrievalOptions.distilledScoreBoost`를 사용한다.

```json
{
  "retrievalStrategy": "hybrid",
  "retrievalOptions": {
    "structureTopK": 5,
    "ideaBlockTopK": 5,
    "finalTopK": 5,
    "dedupe": true,
    "distilledScoreBoost": 0.05,
    "queryExpansionEnabled": false
  }
}
```

처리 방식:

- 서버는 원본 `score` 값을 바꾸지 않는다.
- 병합 정렬 시 `ideaBlockDistilled=true`인 결과에만 정렬용 boost를 더한다.
- `0.03`, `0.05`, `0.1` 정도를 비교 실험하고, 일반 운영 기본값은 `0` 또는 낮은 값으로 둔다.

## 8.1 평가 대시보드 권장 구성

평가 화면은 다음 카드로 구성한다.

| 카드 | 표시값 |
|---|---|
| Run Summary | `runId`, 생성 시각, object scope, embedding profile, topK, minScore |
| Strategy Comparison | strategy별 `hitRate`, `mrr`, `averageElapsedMs` |
| Before/After Delta | `hitRateDelta`, `mrrDelta`, `averageElapsedMsDelta` |
| Question Detail | 질문별 hit 여부, 첫 정답 rank, 반환 chunk 목록 |
| Distilled Impact | `ideaBlockDistilled=true` 결과 개수, boost 적용 실험값 |

권장 UX:

- merge apply 전 run을 “Before”로 저장한다.
- merge apply + RAG 재색인 완료 후 run을 “After”로 저장한다.
- `compare` API로 delta를 계산하고, 악화된 strategy/question을 접어서 보여준다.
- `distilledScoreBoost` 값별 run을 별도 저장해 A/B/C 비교한다.

## 9. 권장 UX

### 9.1 기본 버튼 구성

IdeaBlock 병합 후보 카드:

```text
[Preview 생성]
[병합 적용]
[병합 적용 후 RAG 재색인]
```

권장 기본:

- 일반 사용자는 `[병합 적용 후 RAG 재색인]`을 사용
- 개발/검증 모드에서는 `[병합 적용]`만 제공해 stage 변경만 확인 가능

### 9.2 상태 배지

| 조건 | 표시 |
|---|---|
| `applicable=true` | 적용 가능 |
| `applicable=false` | 적용 불가 |
| `validationWarnings` 존재 | 검증 경고 |
| `pipelineResult.resumedPhase=PIPELINE` | 재색인 시작됨 |
| `pipelineResult` 없음 | 재색인 미실행 |

### 9.3 안전장치

- apply 전 확인 dialog 표시
- `mergedFromChunkIds` 개수와 source evidence를 dialog에 표시
- 적용 후 상세에는 `mergedChunkId`, `mergedFromChunkIds`, `ideaBlockDistillationSourceChunks` 기반 원본 chunk 요약을 표시
- 적용 후에는 동일 cluster의 버튼을 비활성화하고 summary를 재조회
- 재색인 중에는 같은 revision의 추가 apply를 막거나 경고한다

## 10. 오류 처리

| 서버 오류 | 클라이언트 처리 |
|---|---|
| `planFingerprint is required` | preview를 다시 생성하도록 안내 |
| `plan was not found or is stale` | preview가 오래되었으므로 다시 생성 |
| `plan is not applicable` | `validationWarnings` 표시 |
| `source chunks are missing` | summary 재조회 후 다시 시도 |
| pipeline 실패 | pipeline progress의 error message 표시 후 RAG 재시도 제공 |
| `Merged IdeaBlock stage was not found` | 이미 undo 되었거나 stage가 재생성됨. summary 재조회 |
| `source chunk snapshots` 없음 | undo 불가. 재추출 또는 재색인 안내 |

## 11. 테스트 시나리오

### 11.1 정상 흐름

1. Blockify로 문서 재추출 및 RAG 색인
2. summary 조회
3. merge candidate cluster 선택
4. preview 생성
5. `applicable=true` 확인
6. `merge-apply` + `runRagIndex=true`
7. pipeline progress polling
8. RAG chat에서 `retrievalStrategy=hybrid`로 질의
9. merged IdeaBlock이 검색 결과에 포함되는지 확인

### 11.2 stale fingerprint

1. preview 생성
2. 다른 사용자가 먼저 apply
3. 기존 fingerprint로 apply
4. 서버 오류 표시
5. preview 재생성 유도

### 11.3 LLM 미설정

1. LLM provider/model 없이 preview
2. `applicable=false`
3. `validationWarnings=["LLM_NOT_CONFIGURED"]`
4. apply 버튼 비활성화

### 11.4 재색인 미실행

1. `merge-apply` 호출 시 `runRagIndex` 생략
2. `pipelineResult=null`
3. UI에 “병합 적용됨, 재색인 필요” 표시
4. 별도 “RAG 재색인” 버튼 제공

### 11.5 검증 실패 preview

1. LLM preview가 `applicable=false` 반환
2. `validationWarnings` 표시
3. apply 버튼 비활성화
4. preview 재생성 또는 LLM 설정 변경 유도

### 11.6 undo

1. merge apply 수행
2. merged chunk 상세에서 undo 버튼 노출
3. undo 호출
4. 원본 chunk id들이 복원되는지 확인
5. `runRagIndex=true`이면 pipeline progress polling

### 11.7 batch apply

1. 여러 applicable cluster 선택
2. batch apply 호출
3. `applied`와 `failed`를 각각 표시
4. summary 재조회
5. RAG 평가 API로 병합 전후 비교

## 12. 완료 기준

- 파일 상세에서 IdeaBlock summary를 볼 수 있다.
- cluster별 merge preview를 생성할 수 있다.
- `applicable=false`인 preview는 적용할 수 없다.
- `planFingerprint` 기반 apply 요청을 보낸다.
- apply 후 stage 병합 결과가 표시된다.
- `runRagIndex=true`이면 pipeline progress polling이 시작된다.
- 재색인 완료 후 RAG chat에서 hybrid 검색으로 검증할 수 있다.
- undo API를 호출해 원본 chunk를 복원할 수 있다.
- batch apply 결과의 성공/실패를 분리 표시할 수 있다.
- 평가 API로 structure, ideaBlock, hybrid를 비교할 수 있다.

## 13. RAG 검색 평가 Job API

질문 수가 많거나 `structure`, `ideaBlock`, `hybrid`를 모두 비교하는 경우 동기 평가 API는 브라우저 요청 시간이 길어진다. 클라이언트는 긴 평가에 비동기 Job API를 사용한다.

Job 생성:

```http
POST /api/ai/chat/rag/evaluations/jobs
```

요청 body는 기존 동기 평가 API와 동일하다.

```json
{
  "strategies": ["structure", "ideaBlock", "hybrid"],
  "questions": [
    {
      "query": "여름 휴가 규정이 있는가",
      "expectedContentContains": ["휴가"]
    }
  ],
  "topK": 5,
  "minScore": 0.6,
  "objectType": "attachment",
  "objectId": "2",
  "embeddingProfileId": "retrieval-ko-kure"
}
```

생성 응답은 `202 Accepted`이며 `jobId`를 반환한다.

```json
{
  "jobId": "reval-job-...",
  "status": "PENDING",
  "totalQuestions": 45,
  "completedQuestions": 0,
  "totalStrategies": 3,
  "completedStrategies": 0
}
```

진행 상태 조회:

```http
GET /api/ai/chat/rag/evaluations/jobs/{jobId}
```

표시 권장:

- `status`: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`
- `completedQuestions / totalQuestions`: 질문 수 × 전략 수 기준의 전체 평가 진행률
- `completedStrategies / totalStrategies`
- `currentStrategy`
- `currentQuestion`
- `runId`
- `errorMessage`

완료 후:

- `status=COMPLETED`이면 `runId`로 기존 결과 조회 API를 호출한다.
- JDBC가 활성화된 서버에서는 Job 상태가 `tb_ai_rag_retrieval_evaluation_job`에 저장된다.
- 서버 재시작 중 `PENDING` 또는 `RUNNING`이던 Job은 `FAILED`로 정리되며, 같은 요청으로 재실행해야 한다.

```http
GET /api/ai/chat/rag/evaluations/{runId}
```

CSV export:

```http
GET /api/ai/chat/rag/evaluations/{runId}/export.csv
```

용도:

- 전략별 결과를 스프레드시트로 분석
- 질문별 Top-K 결과 수동 검토
- Hit/MRR 외 상세 rank, score, metadataPreview 확인

질문 세트 저장:

```http
POST /api/ai/chat/rag/evaluations/question-sets
GET /api/ai/chat/rag/evaluations/question-sets
GET /api/ai/chat/rag/evaluations/question-sets/{questionSetId}
GET /api/ai/chat/rag/evaluations/question-sets/{questionSetId}/runs
GET /api/ai/chat/rag/evaluations/question-sets/{questionSetId}/recommendation
```

생성 요청:

```json
{
  "name": "취업규칙 평가 세트",
  "description": "structure, ideaBlock, hybrid 비교용",
  "questions": [
    {
      "query": "여름 휴가 규정이 있는가",
      "expectedContentContains": ["휴가"]
    }
  ]
}
```

질문 세트 기반 평가 Job 실행:

```http
POST /api/ai/chat/rag/evaluations/question-sets/{questionSetId}/jobs
```

요청:

```json
{
  "strategies": ["structure", "ideaBlock", "hybrid"],
  "topK": 5,
  "minScore": 0.6,
  "objectType": "attachment",
  "objectId": "2",
  "embeddingProfileId": "retrieval-ko-kure"
}
```

문서군 반복 Benchmark 실행:

```http
POST /api/ai/chat/rag/evaluations/question-sets/{questionSetId}/benchmark
```

요청:

```json
{
  "objects": [
    {
      "objectType": "attachment",
      "objectId": "1",
      "label": "structure-based 대상"
    },
    {
      "objectType": "attachment",
      "objectId": "2",
      "label": "Blockify 대상"
    }
  ],
  "strategies": ["structure", "ideaBlock", "hybrid"],
  "repetitions": 3,
  "topK": 5,
  "minScore": 0.6,
  "embeddingProfileId": "retrieval-ko-kure",
  "retrievalOptions": {
    "queryExpansionEnabled": false
  }
}
```

응답:

- `aggregateRecommendation`: 전체 실행 기준 추천 전략
- `aggregateAnalysis`: 전체 실행 기준 Hit/MRR/응답시간 분석
- `objects[]`: object별 run 목록, 추천 전략, 분석 결과
- 각 run은 기존 evaluation run으로 저장되므로 `/question-sets/{questionSetId}/runs`와 `/{runId}/export.csv`를 그대로 사용할 수 있다.

운영 반영 주의:

- 같은 question set을 여러 attachment에 반복 실행할 수 있으므로, 정책 반영은 반드시 `objectType/objectId`별 최신 또는 선택 run 기준으로 표시한다.
- 서버의 `apply-recommendation`은 대상 object의 평가 run만 정책 계산에 사용한다.
- `minScore=0.6`은 KURE 기반 한국어/영어 혼합 질의에서 정상 후보를 모두 잘라낼 수 있다. Benchmark의 기본 비교는 `minScore=0.0` 또는 낮은 값으로 시작하고, score 분포를 본 뒤 운영 threshold를 올린다.
- 품질 기준 미달로 `RETRIEVAL_RECOMMENDATION_QUALITY_TOO_LOW`가 반환되면 정책을 자동 반영하지 말고 “추천 보류” 상태로 표시한다.

클라이언트 처리:

- 평가 질문은 question set으로 저장하고 재사용한다.
- 동일 question set으로 attachment/전략/embedding profile만 바꿔 반복 실행한다.
- 여러 attachment 또는 문서 유형을 비교할 때는 `/benchmark`를 사용해 같은 질문 세트를 동일 조건으로 반복 실행한다.
- 질문 세트 기반 실행으로 생성된 run은 `questionSetId`를 포함한다.
- 평가 이력 화면에는 `questionSetId`, `runId`, 전략, attachment, embedding profile을 함께 표시한다.
- 질문 세트 상세 화면에서는 `/question-sets/{questionSetId}/runs`로 해당 세트의 run 목록을 표시한다.
- `/question-sets/{questionSetId}/recommendation`으로 현재 저장된 run 기준 추천 전략을 표시한다.
- question set 수정은 새 세트 생성으로 처리한다. 기존 run의 재현성을 위해 기존 세트를 덮어쓰지 않는다.

추천 응답 예:

```json
{
  "questionSetId": "reqs-...",
  "recommendedStrategy": "hybrid",
  "reason": "Selected by weighted score: hitRate 60%, MRR 35%, latency 5%",
  "runCount": 3,
  "strategies": [
    {
      "strategy": "hybrid",
      "runCount": 1,
      "questionCount": 15,
      "hitRate": 0.86,
      "mrr": 0.72,
      "averageElapsedMs": 320,
      "score": 0.78,
      "runIds": ["reval-..."]
    }
  ]
}
```

UI 처리:

- 추천 전략을 “현재 평가 기준 추천”으로 표시한다.
- `reason`과 전략별 `score`, `hitRate`, `mrr`, `averageElapsedMs`를 함께 표시한다.
- 추천은 저장된 run 기반이므로, 새 평가 Job 완료 후 recommendation을 다시 조회한다.
- 운영 기본 전략으로 적용하기 전 사용자가 확인할 수 있게 한다.

비교:

```http
POST /api/ai/chat/rag/evaluations/compare
```

클라이언트 처리 기준:

- 5문항 이하 단일 전략 smoke test는 기존 동기 API를 사용해도 된다.
- 10문항 이상이거나 2개 이상 전략 비교는 Job API를 기본으로 사용한다.
- polling 간격은 2초부터 시작하고, 30초 이상 실행되면 5초로 늘린다.
- `FAILED`이면 `errorMessage`를 표시하고 같은 요청으로 재실행 버튼을 제공한다.
