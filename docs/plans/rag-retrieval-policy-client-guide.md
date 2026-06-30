# RAG Retrieval Policy Client Guide

## 목적

평가 API로 산출한 추천 검색 전략을 Attachment 또는 object scope 단위 운영 정책으로 저장하고, 일반 `/api/ai/chat/rag` 질의에서 별도 전략을 지정하지 않으면 서버가 해당 정책을 자동 적용한다.

## 정책 API

## 반복 평가 분석 API

```http
GET /api/ai/chat/rag/evaluations/question-sets/{questionSetId}/analysis
```

질문 세트 기반 평가 run들을 모아 전략별 성능과 질문별 hit/miss를 반환한다. 반복 테스트 후 보완 대상을 찾는 1차 화면에 사용한다.

```json
{
  "questionSetId": "reqs-...",
  "runCount": 3,
  "questionCount": 25,
  "strategies": [
    {
      "strategy": "hybrid",
      "questionCount": 25,
      "hitCount": 22,
      "hitRate": 0.88,
      "mrr": 0.71,
      "averageElapsedMs": 38.4,
      "failedQuestionCount": 3
    }
  ],
  "questions": [
    {
      "query": "수습 기간은 몇 개월인가",
      "hitStrategies": ["structure"],
      "missedStrategies": ["ideaBlock", "hybrid"],
      "bestStrategy": "structure",
      "bestRank": 1
    }
  ]
}
```

권장 UI:

- 전략별 `hitRate`, `mrr`, `failedQuestionCount` 표시
- `missedStrategies`가 많은 질문을 상단에 표시
- 실패 질문을 CSV/JSON으로 export해 다음 질문 세트 또는 IdeaBlock 보완 작업에 사용

### 정책 목록

```http
GET /api/ai/chat/rag/retrieval-policies
```

### 정책 조회

```http
GET /api/ai/chat/rag/retrieval-policies/{objectType}/{objectId}
```

예:

```http
GET /api/ai/chat/rag/retrieval-policies/attachment/1
```

### 수동 저장

```http
PUT /api/ai/chat/rag/retrieval-policies
Content-Type: application/json
```

```json
{
  "objectType": "attachment",
  "objectId": "1",
  "retrievalStrategy": "hybrid",
  "retrievalOptions": {
    "structureTopK": 5,
    "ideaBlockTopK": 5,
    "finalTopK": 5,
    "minScore": 0.6,
    "dedupe": true,
    "distilledScoreBoost": 0.03
  },
  "questionSetId": "reqs-...",
  "evaluationRunId": "reval-..."
}
```

### 평가 추천 반영

```http
POST /api/ai/chat/rag/retrieval-policies/apply-recommendation
Content-Type: application/json
```

```json
{
  "questionSetId": "reqs-...",
  "objectType": "attachment",
  "objectId": "1"
}
```

서버는 해당 질문 세트의 평가 run들을 기준으로 추천 전략을 다시 계산하고, 가장 높은 점수의 전략을 object scope 정책으로 저장한다.

추천 적용으로 생성된 정책은 서버의 현재 retrieval 기본값을 `retrievalOptions`에 함께 저장한다.

```json
{
  "retrievalStrategy": "hybrid",
  "retrievalOptions": {
    "structureTopK": 5,
    "ideaBlockTopK": 5,
    "finalTopK": 5,
    "dedupe": true,
    "distilledScoreBoost": 0.03
  }
}
```

### 정책 사용 이력

```http
GET /api/ai/chat/rag/retrieval-policies/{objectType}/{objectId}/usage
```

최근 정책 적용 RAG Chat 이력을 반환한다. 원문 질문이나 문서 내용은 포함하지 않는다.

```json
[
  {
    "usageId": "rpu-...",
    "objectType": "attachment",
    "objectId": "1",
    "retrievalStrategy": "hybrid",
    "questionSetId": "reqs-...",
    "evaluationRunId": "reval-...",
    "topK": 5,
    "minScore": 0.6,
    "resultCount": 5,
    "skippedChat": false,
    "elapsedMs": 34,
    "createdAt": "2026-06-27T00:00:00Z"
  }
]
```

### 정책 사용 요약

```http
GET /api/ai/chat/rag/retrieval-policies/{objectType}/{objectId}/usage/summary
```

```json
{
  "objectType": "attachment",
  "objectId": "1",
  "usageCount": 12,
  "averageResultCount": 4.7,
  "averageElapsedMs": 42.5,
  "skippedChatCount": 1,
  "latestStrategy": "hybrid"
}
```

### 정책 변경 이력

```http
GET /api/ai/chat/rag/retrieval-policies/{objectType}/{objectId}/history
```

정책이 수동 저장되거나 추천 전략으로 적용된 이력을 반환한다.

```json
[
  {
    "historyId": "rph-...",
    "objectType": "attachment",
    "objectId": "1",
    "retrievalStrategy": "hybrid",
    "reason": "APPLY_RECOMMENDATION",
    "questionSetId": "reqs-...",
    "evaluationRunId": "reval-...",
    "score": 0.91,
    "createdAt": "2026-06-27T00:00:00Z"
  }
]
```

## 오류 처리

`apply-recommendation` 실패 시 `ProblemDetails` 형식으로 `code`가 내려온다.

| HTTP | code | 의미 |
|---:|---|---|
| 404 | `RETRIEVAL_QUESTION_SET_NOT_FOUND` | 지정한 질문 세트가 없음 |
| 409 | `NO_EVALUATION_RUNS_FOR_QUESTION_SET` | 질문 세트는 있으나 평가 run이 없어 추천 불가 |

클라이언트는 409인 경우 “먼저 이 질문 세트로 평가 Job을 실행하세요” 안내를 표시한다.

## RAG Chat 적용 규칙

일반 질의:

```http
POST /api/ai/chat/rag
```

요청에 `objectType/objectId`가 있고 `retrievalStrategy`가 없으면 서버는 저장된 정책을 조회해 자동 적용한다.

클라이언트가 `retrievalStrategy`를 명시하면 서버 정책보다 클라이언트 요청이 우선한다.

클라이언트가 `retrievalOptions`를 명시하면 서버 정책의 옵션을 덮어쓴다.

정책이 적용된 응답에는 `metadata.retrievalPolicy`가 포함된다.

```json
{
  "metadata": {
    "retrievalPolicy": {
      "applied": true,
      "objectType": "attachment",
      "objectId": "1",
      "retrievalStrategy": "hybrid",
      "questionSetId": "reqs-...",
      "evaluationRunId": "reval-...",
      "score": 0.91
    }
  }
}
```

## 권장 UI 흐름

1. Attachment 상세에서 평가 질문 세트를 실행한다.
2. `GET /api/ai/chat/rag/evaluations/question-sets/{questionSetId}/recommendation`으로 추천 전략을 표시한다.
3. 사용자가 “운영 전략으로 적용”을 누르면 `apply-recommendation`을 호출한다.
4. Attachment 상세에서는 정책 조회 API로 현재 운영 전략을 표시한다.
5. 정책 사용 요약 API로 최근 운영 결과 수, 평균 지연시간, no-context 발생 횟수를 표시한다.
6. 정책 변경 이력 API로 현재 전략이 수동 적용인지 추천 적용인지 표시한다.
7. 일반 RAG Chat은 별도 비교 모드가 아니면 `retrievalStrategy`를 보내지 않는다.

## 비교 모드

전략 비교 UI에서는 기존처럼 `retrievalStrategy=structure|ideaBlock|hybrid|auto`를 명시한다. 이 경우 저장된 운영 정책은 적용되지 않는다.
