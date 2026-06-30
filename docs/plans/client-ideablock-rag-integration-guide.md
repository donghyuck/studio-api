# 클라이언트 수정 지시문: IdeaBlock 기반 RAG 검색 전략 연동

## Summary

서버는 이제 Markdown/RAG 파이프라인에서 `blockify` 전략을 선택하면 `IdeaBlock` chunk를 생성하고, RAG Chat에서 `retrievalStrategy`로 `structure`, `ideaBlock`, `hybrid`, `auto` 검색을 선택할 수 있다.

클라이언트는 파일 상세의 지식 파이프라인 실행 옵션과 RAG Chat 요청 옵션을 분리해서 지원해야 한다.

- 인덱싱 단계: `chunkingStrategy=blockify` 선택 시 IdeaBlock chunk 생성
- 검색 단계: `/api/ai/chat/rag` 요청에 `retrievalStrategy`와 `retrievalOptions` 전달
- 표시 단계: chunk metadata의 IdeaBlock 필드와 fallback 상태를 표시

## 1. 파일 상세: 지식 파이프라인 옵션

### 1.1 Chunking 전략 선택

파일 상세의 “지식 파이프라인 생성/재실행” 화면에서 기존 전략 목록에 다음 설명을 반영한다.

| UI Label | 요청값 | 설명 |
|---|---|---|
| Structure-Based | `structure-based` | Markdown heading/block 구조 기반 chunk |
| Blockify / IdeaBlock | `blockify` | source block 단위 질문·답변 IdeaBlock 생성 |

`blockify` 선택 시 안내 문구:

```text
Blockify는 문서의 조항, 항, 호, 표 row, 예외 조건 단위로 IdeaBlock을 생성합니다.
각 IdeaBlock은 critical question, trusted answer, source evidence를 포함합니다.
누락되거나 검증 실패한 source block은 structure-based fallback chunk로 보존됩니다.
```

### 1.2 Blockify LLM 옵션

서버가 지원하는 경우 클라이언트 옵션으로 다음 값을 전달한다.

```json
{
  "chunkingStrategy": "blockify",
  "blockifyLlmProvider": "google-ai-gemini",
  "blockifyLlmModel": "gemini-2.5-flash",
  "blockifyPiiMaskingEnabled": true
}
```

기본값:

- `blockifyPiiMaskingEnabled`: `true`
- provider/model을 사용자가 선택하지 않으면 서버 설정값을 사용하도록 필드를 생략
- 민감 문서에서는 외부 LLM 사용 전 PII masking이 꺼지지 않도록 UI에서 경고 또는 차단

### 1.3 진행 상태 표시

파일 상세에는 다음 상태를 구분해서 표시한다.

- Markdown 생성 상태
- Chunking 상태
- RAG 색인 상태
- Skill extraction 상태
- 선택된 chunking strategy
- 선택된 embedding profile/model

`blockify` 실행 완료 후에는 chunk 통계 영역에 다음 값을 표시한다.

- 전체 chunk 수
- `chunkType=ideaBlock` 수
- fallback chunk 수
- fallback reason별 개수
- source block coverage, 서버가 별도 API로 제공하지 않으면 metadata 샘플 기반으로 “상세 조회 필요”로 표시

## 2. RAG Chat 요청 변경

### 2.1 신규 요청 필드

`POST /api/ai/chat/rag`에 다음 필드를 추가로 보낼 수 있다.

```json
{
  "chat": {
    "provider": "google-ai-gemini",
    "model": "gemini-2.5-flash",
    "messages": [
      {"role": "user", "content": "여름 휴가 규정이 있는가"}
    ],
    "systemPrompt": "제공된 RAG 문서 내용에 근거해서만 답변하세요."
  },
  "ragQuery": "여름 휴가 규정이 있는가",
  "objectType": "attachment",
  "objectId": "1",
  "embeddingProfileId": "retrieval-ko-kure",
  "topK": 5,
  "minScore": 0.6,
  "retrievalStrategy": "hybrid",
  "retrievalOptions": {
    "structureTopK": 5,
    "ideaBlockTopK": 5,
    "finalTopK": 5,
    "minScore": 0.6,
    "dedupe": true,
    "includeDebugChunks": true
  },
  "debug": true
}
```

### 2.2 retrievalStrategy 값

| 값 | 동작 | 권장 UI |
|---|---|---|
| `default` 또는 생략 | 기존 RAG 검색 | 기본 호환 모드 |
| `structure` | `strategy=structure-based` chunk만 후보로 사용 | 구조 기반만 테스트 |
| `ideaBlock` | `chunkType=ideaBlock` 또는 `actualChunkingStrategy=blockify` chunk 후보 사용 | IdeaBlock 단독 비교 |
| `hybrid` | structure 후보와 IdeaBlock 후보를 병합/dedupe | 권장 기본 테스트 모드 |
| `auto` | v1에서는 서버가 `hybrid`로 해석 | 향후 자동 라우팅용 |

권장 기본값:

```text
retrievalStrategy = hybrid
structureTopK = 5
ideaBlockTopK = 5
finalTopK = 5
dedupe = true
```

### 2.3 기존 topK와 우선순위

서버 우선순위:

```text
topK > ragTopK > 서버 기본값
```

`retrievalOptions.finalTopK`가 있으면 hybrid/auto 병합 후 최종 반환 개수에 적용된다.

클라이언트 권장:

- 신규 화면에서는 `ragTopK` 대신 `topK` 사용
- hybrid 사용 시 `retrievalOptions.finalTopK`를 명시
- `minScore`는 기존 request의 `minScore`를 우선 사용하고, 전략별 옵션에서도 같은 값을 보여준다

## 3. 응답 표시

### 3.1 retrieval debug metadata

`debug=true`이고 서버에서 client debug가 허용된 경우 응답 metadata에 `retrieval`이 포함된다.

예상 구조:

```json
{
  "retrieval": {
    "requestedStrategy": "hybrid",
    "resolvedStrategy": "hybrid",
    "finalCount": 5,
    "legs": [
      {"strategy": "structure", "topK": 5, "candidateCount": 5, "returnedCount": 5},
      {"strategy": "ideaBlock.actualChunkingStrategy", "topK": 5, "candidateCount": 3, "returnedCount": 3},
      {"strategy": "ideaBlock.chunkType", "topK": 5, "candidateCount": 3, "returnedCount": 3}
    ],
    "chunks": [
      {
        "chunkId": "mrev-...-blockify-1",
        "score": 0.91,
        "strategy": "blockify",
        "chunkType": "ideaBlock",
        "actualChunkingStrategy": "blockify",
        "markdownDocumentId": "mdoc-...",
        "markdownRevisionId": "mrev-...",
        "sectionTitle": "제10조(휴가)"
      }
    ]
  }
}
```

UI 표시 권장:

- “검색 전략”: requested/resolved strategy
- “검색 후보”: leg별 candidate/returned count
- “최종 컨텍스트”: finalCount
- debug chunk 목록은 개발자 모드에서만 표시

### 3.2 RAG references 표시

기존 `ragReferences`는 계속 사용한다.

추가로 metadata가 포함되는 화면에서는 다음 IdeaBlock 필드를 우선 표시한다.

| Metadata Key | 표시명 |
|---|---|
| `chunkType` | Chunk 유형 |
| `ideaBlockName` 또는 `title` | IdeaBlock 이름 |
| `criticalQuestion` 또는 `question` | 핵심 질문 |
| `trustedAnswer` 또는 `answer` | 검증 답변 |
| `entityName` | 엔티티 |
| `entityType` | 엔티티 유형 |
| `keywords` | 키워드 |
| `tags` | 태그 |
| `sourceEvidence` | 원문 근거 |
| `sourceBlockRange` | 원문 block 범위 |
| `sourceSectionId` | 원문 section |
| `confidence` | 신뢰도 |
| `fallbackReason` | fallback 사유 |

## 4. 파일 상세 Chunk/Embedding 결과 표시

파일 상세의 RAG/Chunk 탭에서 chunk 목록을 보여줄 수 있다면 다음 뱃지를 추가한다.

- `IdeaBlock`: `chunkType=ideaBlock`
- `Fallback`: `actualChunkingStrategy=structure-based` and `requestedChunkingStrategy=blockify`
- `Validated`: `validationStatus=RULE_VALIDATED`
- `Review`: `validationStatus=REVIEW_REQUIRED`

Fallback 사유 설명:

| fallbackReason | UI 설명 |
|---|---|
| `ANSWER_TOO_SHORT` | 생성 답변이 너무 짧음 |
| `ANSWER_HAS_NO_BODY` | 답변 본문 없음 |
| `ANSWER_EQUALS_TITLE` | 답변이 제목만 반복 |
| `HEADING_ONLY` | 제목만 있는 block |
| `EVIDENCE_NOT_FOUND` | 원문 evidence 없음 |
| `TRUSTED_ANSWER_FACT_MISMATCH` | 숫자/날짜/기간/비율이 원문과 불일치 |
| `GENERIC_QUESTION` | 질문이 너무 일반적 |
| `TABLE_SECTION` | 표 섹션 fallback |
| `COVERAGE_GAP` | coverage 누락 보존 fallback |

## 5. 비교 테스트 UI

동일 원본을 비교할 때는 서로 다른 Attachment로 등록해 분리한다.

```text
Attachment A
→ chunkingStrategy=structure-based
→ embeddingProfileId=retrieval-ko-kure
→ retrievalStrategy=structure 또는 hybrid

Attachment B
→ chunkingStrategy=blockify
→ embeddingProfileId=retrieval-ko-kure
→ retrievalStrategy=ideaBlock 또는 hybrid
```

비교 화면에서 동일 질문 세트를 실행하고 다음 지표를 표시한다.

- Hit@5
- 첫 정답 근거 rank
- 평균 score
- 응답 시간
- 사용된 retrievalStrategy
- 최종 context chunk 수
- IdeaBlock 사용 비율
- fallback 사용 비율

주의:

- `ideaBlock` 단독은 coverage가 낮을 수 있으므로 운영 기본값으로 바로 쓰지 않는다.
- 운영 질의는 우선 `hybrid`를 기본 후보로 둔다.
- `structure`와 `ideaBlock` 비교는 평가/튜닝 화면에서만 분리한다.

## 6. 클라이언트 검증 시나리오

### 6.1 기존 호환성

요청에서 `retrievalStrategy`를 생략한다.

기대 결과:

- 기존과 동일하게 응답
- `metadata.retrieval` 없음
- `ragReferences` 유지

### 6.2 Hybrid 검색

`retrievalStrategy=hybrid`, `debug=true`, `includeDebugChunks=true`로 요청한다.

기대 결과:

- `metadata.retrieval.requestedStrategy=hybrid`
- `metadata.retrieval.resolvedStrategy=hybrid`
- `legs`에 structure와 ideaBlock leg가 표시
- `ragReferences` 유지

### 6.3 IdeaBlock 단독 검색

`retrievalStrategy=ideaBlock`로 요청한다.

기대 결과:

- IdeaBlock 색인이 있는 문서에서는 `chunkType=ideaBlock` 후보가 반환
- IdeaBlock 색인이 없는 문서에서는 결과 없음 가능
- 결과 없음은 오류가 아니라 “검색 후보 없음”으로 표시

### 6.4 Blockify 인덱싱 결과 확인

파일 상세에서 `chunkingStrategy=blockify`로 재실행한다.

기대 결과:

- chunk metadata에 `chunkType=ideaBlock` 존재
- 정상 chunk는 `criticalQuestion`, `trustedAnswer`, `sourceEvidence`, `sourceBlockRange` 존재
- 검증 실패 chunk는 fallback metadata 존재

## 7. 권장 기본 UX

- 일반 사용자 RAG Chat 기본값: `retrievalStrategy=hybrid`
- 고급 설정에서만 `structure`, `ideaBlock`, `auto` 선택 노출
- debug metadata는 개발자 모드에서만 표시
- 민감 문서에서는 `blockifyPiiMaskingEnabled=false` 선택을 숨기거나 경고
- IdeaBlock 단독 검색은 “비교/실험용”으로 라벨링
