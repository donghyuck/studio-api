# RAG Chat Retrieval Strategy 개선 작업 계획

## Summary

파일 기반 RAG 인덱싱이 `structure-based`, `blockify/IdeaBlock`, `hybrid` 등 여러 검색 표현을 가질 수 있도록 확장한다.

RAG 챗봇은 단순 Top-K 벡터 검색 대신 `retrievalStrategy`에 따라 검색 후보를 선택, 병합, rerank하여 최종 context를 구성한다.

초기 운영 기본값은 `structure`로 유지하고, `hybrid`는 실험/검증 후 기본 승격 여부를 결정한다.

## Goals

- RAG Chat API에서 검색 전략을 명시적으로 받을 수 있다.
- 기존 structure-based RAG 동작은 깨지지 않는다.
- IdeaBlock/Blockify 결과를 별도 후보군으로 검색할 수 있다.
- Hybrid 검색에서 structure chunk와 ideaBlock chunk를 병합할 수 있다.
- 응답 debug 정보에 어떤 전략과 chunk가 사용됐는지 드러난다.
- 향후 평가/벤치마크에서 전략별 품질 비교가 가능하다.

## Non-Goals

- Blockify 원 알고리즘 전체 구현은 이번 범위가 아니다.
- 신규 `IdeaBlock` 전용 DB 모델은 이번 범위에서 필수로 만들지 않는다.
- LLM reranker 도입은 선택 기능으로 두고, 1차는 score 기반 merge를 구현한다.
- 기존 RAG Chat API 경로는 변경하지 않는다.

## Current Problem

현재 RAG Chat 흐름은 다음과 같다.

```text
chat request
  -> ragQuery
  -> vector search
  -> topK chunks
  -> LLM context
  -> answer
```

이 구조에서는 다음 문제가 있다.

- chunking/indexing 전략을 검색 시 구분하기 어렵다.
- `blockify`와 `structure-based`를 같은 방식으로만 검색한다.
- Hybrid retrieval 실험이 어렵다.
- 클라이언트가 "어떤 chunk가 왜 선택됐는지" 확인하기 어렵다.
- Blockify가 보조 지식 인덱스로 발전해도 챗봇에서 활용하기 어렵다.

## Target Architecture

```text
chat request
  -> ragQuery
  -> retrievalStrategy 결정
  -> strategy별 candidate search
  -> merge / dedupe / rerank
  -> final context 구성
  -> LLM answer
```

## Retrieval Strategies

### 1. structure

기존 structure-based chunk만 검색한다.

```text
filter:
  strategy = structure-based
  OR chunkType != ideaBlock
```

용도:

- 운영 기본값
- 조항형 문서
- 높은 coverage가 필요한 질의

### 2. ideaBlock

Blockify/IdeaBlock chunk만 검색한다.

```text
filter:
  chunkType = ideaBlock
  OR actualChunkingStrategy = blockify
```

용도:

- FAQ형 검색
- 자연어 질문 매칭
- 실험/벤치마크

### 3. hybrid

structure와 ideaBlock을 각각 검색한 뒤 병합한다.

```text
structureTopK = N
ideaBlockTopK = M
merge
dedupe
finalTopK = K
```

용도:

- 향후 기본 후보
- coverage와 precision을 동시에 노리는 검색

### 4. auto

서버가 질문 유형과 문서 metadata를 보고 전략을 선택한다.

1차에서는 API 계약만 열어두고 내부 동작은 `structure` 또는 `hybrid`로 매핑한다.

## API Contract

### Request 변경

대상:

```text
POST /api/ai/chat/rag
```

추가 필드:

```json
{
  "retrievalStrategy": "structure | ideaBlock | hybrid | auto",
  "retrievalOptions": {
    "structureTopK": 5,
    "ideaBlockTopK": 5,
    "finalTopK": 5,
    "minScore": 0.6,
    "rerank": false,
    "dedupe": true,
    "includeDebugChunks": false
  }
}
```

기존 필드와의 관계:

```text
topK / ragTopK
  -> finalTopK 기본값으로 사용

minScore
  -> retrievalOptions.minScore 기본값으로 사용

embeddingProfileId
  -> 기존과 동일하게 query embedding에 사용
```

기본값:

```yaml
retrievalStrategy: structure
retrievalOptions:
  structureTopK: topK 또는 5
  ideaBlockTopK: topK 또는 5
  finalTopK: topK 또는 5
  dedupe: true
  rerank: false
```

### Response Debug 확장

`debug=true`일 때 다음 정보를 포함한다.

```json
{
  "retrieval": {
    "strategy": "hybrid",
    "structureCandidateCount": 5,
    "ideaBlockCandidateCount": 5,
    "finalContextCount": 5,
    "chunks": [
      {
        "chunkId": "123",
        "objectType": "attachment",
        "objectId": "1",
        "chunkIndex": 12,
        "score": 0.82,
        "strategy": "structure-based",
        "chunkType": "child",
        "actualChunkingStrategy": null,
        "markdownDocumentId": "...",
        "markdownRevisionId": "...",
        "sectionTitle": "제2조(연차유급휴가)",
        "sourceEvidence": null
      }
    ]
  }
}
```

## Server Implementation Plan

### Phase 1. DTO 및 옵션 모델 추가

- `RagChatRequest` 또는 관련 chat/rag request DTO에 필드 추가
  - `retrievalStrategy`
  - `retrievalOptions`
- enum 추가
  - `RagRetrievalStrategy`
    - `STRUCTURE`
    - `IDEA_BLOCK`
    - `HYBRID`
    - `AUTO`
- options record/class 추가
  - `RagRetrievalOptions`

Acceptance:

- 기존 요청은 변경 없이 동작한다.
- strategy 생략 시 `structure`로 처리된다.

### Phase 2. Metadata Filter 확장

기존 vector search에서 metadata 조건으로 다음을 필터링할 수 있어야 한다.

- `strategy`
- `chunkType`
- `actualChunkingStrategy`
- `contentFormat`
- `markdownDocumentId`
- `markdownRevisionId`
- `objectType`
- `objectId`

필요 시 `MetadataFilter` 또는 RAG search request에 equals/in 조건을 확장한다.

Acceptance:

- structure chunk만 검색 가능
- blockify/ideaBlock chunk만 검색 가능
- attachment scope와 strategy filter가 함께 적용 가능

### Phase 3. Retriever 계층 도입

신규 컴포넌트:

```text
RagRetrievalService
RagRetrievalPlanner
RagCandidateMerger
```

역할:

```text
RagRetrievalPlanner
  - request와 strategy를 보고 검색 계획 생성

RagRetrievalService
  - 계획에 따라 RagPipelineService.search 호출
  - structure/ideaBlock 각각 검색

RagCandidateMerger
  - 중복 제거
  - score 정규화
  - finalTopK 선택
```

1차 dedupe key:

```text
chunkId
또는
markdownRevisionId + sourceEvidence/sourceRef
또는
objectType + objectId + chunkIndex
```

Acceptance:

- `structure`는 기존 검색 결과와 동일해야 한다.
- `ideaBlock`은 blockify chunk만 반환해야 한다.
- `hybrid`는 두 후보군을 합쳐 finalTopK를 반환해야 한다.

### Phase 4. RAG Chat 연결

RAG Chat context 생성부를 수정한다.

기존:

```text
ragPipeline.search(request)
```

변경:

```text
ragRetrievalService.retrieve(request)
```

Context builder는 다음 metadata를 보존한다.

- `chunkType`
- `strategy`
- `actualChunkingStrategy`
- `sourceEvidence`
- `sectionTitle`
- `markdownDocumentId`
- `markdownRevisionId`

Acceptance:

- LLM context에는 최종 merge된 chunk만 들어간다.
- debug 응답에는 candidate와 final context 정보가 포함된다.

### Phase 5. 설정 추가

```yaml
studio:
  ai:
    rag:
      retrieval:
        default-strategy: structure
        hybrid:
          structure-top-k: 5
          idea-block-top-k: 5
          final-top-k: 5
          dedupe: true
          rerank: false
```

Acceptance:

- yml 설정으로 기본 strategy를 변경할 수 있다.
- 운영 기본은 `structure`다.

### Phase 6. 테스트

#### Unit Test

- strategy enum parsing
- request defaulting
- retrieval option merge
- metadata filter 생성
- candidate dedupe
- hybrid finalTopK 적용

#### Integration Test

- 기존 `/api/ai/chat/rag` 요청이 그대로 동작
- `retrievalStrategy=structure`는 structure chunk만 검색
- `retrievalStrategy=ideaBlock`은 blockify/ideaBlock chunk만 검색
- `retrievalStrategy=hybrid`는 양쪽 후보를 병합
- debug 응답에 strategy/chunk metadata 포함
- object scope filter와 strategy filter 동시 적용

#### Regression Test

- 기존 RAG search API
- 기존 attachment RAG indexing
- 기존 markdown pipeline
- 기존 chat memory

검증 명령:

```bash
./gradlew :studio-platform-ai:test
./gradlew :starter:studio-platform-starter-ai:test
./gradlew :starter:studio-platform-starter-ai-web:test
./gradlew :studio-application-modules:content-embedding-pipeline:test
git diff --check
```

## Client Implementation Guide

### 파일 상세 RAG 옵션

기존 chunking strategy 선택과 별도로, 챗봇 검색 전략을 설정할 수 있게 한다.

```text
검색 전략
- Structure 기반
- IdeaBlock 기반
- Hybrid
- Auto
```

초기 기본값:

```text
Structure 기반
```

### Chat 요청

기존 요청에 다음을 추가한다.

```json
{
  "retrievalStrategy": "hybrid",
  "retrievalOptions": {
    "structureTopK": 5,
    "ideaBlockTopK": 5,
    "finalTopK": 5,
    "dedupe": true,
    "rerank": false
  }
}
```

### Debug UI

`debug=true`일 때 다음을 표시한다.

```text
검색 전략: hybrid
Structure 후보: 5개
IdeaBlock 후보: 5개
최종 context: 5개
```

각 chunk 표시:

```text
- chunkType
- strategy
- actualChunkingStrategy
- score
- sectionTitle
- sourceEvidence
```

## Evaluation Plan

기준 데이터:

```text
Attachment 1: structure-based
Attachment 2: blockify PoC
```

평가 세트:

- 전체 문서 범위 72문항
- Blockify 친화 26문항
- 원문에 없는 질문 10문항
- 숫자/기간/예외 조건 질문 20문항

측정 지표:

- Hit@5
- MRR
- Top-1 accuracy
- Answer correctness
- Evidence faithfulness
- 평균 context token
- 평균 latency
- p-value 또는 bootstrap CI

승격 기준:

```text
hybrid가 structure 대비:
- Hit@5 유지 또는 상승
- MRR +5% 이상
- Answer correctness +5% 이상
- hallucination 증가 없음
- context token 과도 증가 없음
```

## Risks

### Risk 1. Hybrid가 context를 과도하게 늘릴 수 있음

대응:

- finalTopK 제한
- chunk text 길이 제한
- score threshold 적용

### Risk 2. IdeaBlock이 틀린 답변을 강화할 수 있음

대응:

- trustedAnswer evidence 검증
- sourceEvidence 없는 IdeaBlock 제외
- debug에서 source 표시

### Risk 3. 기존 RAG 검색 회귀

대응:

- 기본값 `structure`
- 기존 요청은 기존 동작 유지
- regression test 추가

### Risk 4. score scale 차이

structure chunk와 ideaBlock chunk의 길이와 표현이 달라 score 분포가 다를 수 있다.

대응:

- 1차는 단순 score merge
- 2차에서 z-score/percentile normalize 또는 reranker 도입

## Completion Criteria

- 기존 RAG Chat 요청이 깨지지 않는다.
- `retrievalStrategy=structure`가 동작한다.
- `retrievalStrategy=ideaBlock`이 동작한다.
- `retrievalStrategy=hybrid`가 동작한다.
- debug 응답에서 선택된 chunk의 strategy와 metadata를 확인할 수 있다.
- 동일 평가셋으로 structure, ideaBlock, hybrid 성능을 비교할 수 있다.
- 운영 기본값은 `structure`로 유지된다.
