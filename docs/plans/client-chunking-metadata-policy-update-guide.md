# 클라이언트 청킹 Metadata 정책 반영 가이드

## 목적

서버 청킹 결과 metadata에 요청 전략, 실제 전략, fallback 상태, 품질 상태가 공통으로 추가되었다.

클라이언트는 기존 `strategy`, `chunkType`, `validationStatus` 표시를 유지하면서 새 metadata를 보조 진단 정보로 표시해야 한다. DB schema, API endpoint, request shape는 변경하지 않는다.

## 적용 대상

- 파일 상세의 RAG/Chunk 목록
- Markdown pipeline 또는 RAG 색인 결과 상세
- RAG Chat debug chunk 목록
- 운영자용 chunk 진단 화면

일반 사용자 답변 화면은 기존 `ragReferences` 표시를 유지한다. 새 metadata는 상세/개발자/운영 진단 화면에서 우선 사용한다.

## 새 Metadata Key

| Key | 값 예시 | 의미 |
|---|---|---|
| `requestedChunkingStrategy` | `structure-based`, `fixed-size`, `recursive`, `blockify` | 사용자가 요청했거나 상위 전략이 요청한 전략 |
| `actualChunkingStrategy` | `structure-based`, `recursive`, `fixed-size`, `blockify` | 실제 chunk를 생성한 전략 |
| `fallbackStatus` | `NOT_REQUIRED`, `APPLIED` | full-strategy fallback 적용 여부 |
| `fallbackFrom` | `structure-based`, `recursive` | fallback 시작 전략 |
| `fallbackTo` | `recursive`, `fixed-size` | fallback 대상 전략 |
| `fallbackReason` | `plain-text-context`, `missing-structure`, `invalid-structure-chunks` | fallback 사유 |
| `chunkQualityStatus` | `VALID`, `REVIEW_REQUIRED` | 색인된 chunk의 품질 검증 상태 |
| `chunkQualityIssues` | `["MISSING_PROVENANCE"]` | 품질 이슈 목록 |

`FAILED` chunk는 색인되지 않는다. 클라이언트는 chunk 목록에서 `FAILED` 상태를 기대하지 말고, 실패는 pipeline/job 오류 또는 진단 메시지에서 처리한다.

## 표시 우선순위

### Strategy 표시

기존 `strategy`는 검색 filter 호환용 persisted strategy로 유지된다.

UI에서 전략을 보여줄 때는 다음 순서를 사용한다.

```ts
const requested = metadata.requestedChunkingStrategy;
const actual = metadata.actualChunkingStrategy ?? metadata.strategy;
```

표시 규칙:

- `requested`가 없거나 `requested === actual`: `actual`
- `requested !== actual`: `${requested} -> ${actual}`

예:

| Metadata | UI |
|---|---|
| `requested=structure-based`, `actual=structure-based` | `Structure-based` |
| `requested=structure-based`, `actual=recursive` | `Structure-based -> Recursive` |
| `requested=structure-based`, `actual=fixed-size` | `Structure-based -> Fixed` |
| `requested=blockify`, `actual=structure-based` | `Blockify -> Structure-based` |

### Fallback Badge

`fallbackStatus=APPLIED`이면 fallback badge를 표시한다.

권장 표시:

```text
Fallback: Structure-based -> Recursive
Reason: plain-text-context
```

`fallbackStatus=NOT_REQUIRED`이거나 값이 없으면 fallback badge를 표시하지 않는다.

Blockify 기존 fallback과 구분한다.

- 공통 fallback: `fallbackStatus=APPLIED`
- Blockify validation fallback: `validationStatus=FALLBACK`

둘 다 있을 수 있으므로 UI에서는 별도 badge로 표시한다.

```text
Strategy Fallback
Blockify Fallback
```

### Quality Badge

`chunkQualityStatus` 기준으로 표시한다.

| 값 | UI 상태 | 문구 |
|---|---|---|
| `VALID` | 정상 | `Valid` |
| `REVIEW_REQUIRED` | 주의 | `Review required` |
| 없음 | 중립 | 표시하지 않음 |

`chunkQualityIssues`가 있으면 tooltip 또는 상세 영역에 그대로 표시한다.

현재 서버가 내려줄 수 있는 공통 이슈:

| Issue | UI 설명 |
|---|---|
| `MISSING_PROVENANCE` | 원문 위치 정보가 부족합니다. 검색은 가능하지만 원문 복원 품질 확인이 필요합니다. |
| `EMPTY_CONTENT` | 본문이 비어 있습니다. 색인 대상에서는 제외되어야 합니다. |
| `MAX_SIZE_EXCEEDED` | 최대 chunk 크기를 초과했습니다. fallback 또는 재청킹 확인이 필요합니다. |

색인된 chunk에는 일반적으로 `EMPTY_CONTENT`, `MAX_SIZE_EXCEEDED`가 없어야 한다. 보이면 서버 오류 또는 legacy 데이터로 취급하고 운영 진단에 노출한다.

## 화면별 반영 지시

### 파일 상세 RAG/Chunk 탭

chunk 목록 컬럼 또는 상세 panel에 다음 항목을 추가한다.

| 항목 | 값 |
|---|---|
| 요청 전략 | `requestedChunkingStrategy` |
| 실제 전략 | `actualChunkingStrategy ?? strategy` |
| Fallback | `fallbackStatus`, `fallbackFrom`, `fallbackTo`, `fallbackReason` |
| 품질 | `chunkQualityStatus`, `chunkQualityIssues` |
| Provenance | 기존 `sourceRef`, `sourceRefs`, `page`, `slide`, `blockIds`, `startOffset`, `endOffset` |

Provenance는 `startOffset/endOffset`만 요구하지 않는다. 다음 중 하나라도 있으면 원문 위치 정보가 있는 것으로 표시한다.

- `sourceRef`
- `sourceRefs`
- `page`
- `slide`
- `blockIds`
- `startOffset/endOffset`

### Pipeline Progress 화면

기존 progress API 응답 shape는 변경하지 않는다.

공통 metadata 변경은 개별 chunk metadata에 저장되므로, progress aggregate에 새 필드가 없더라도 오류로 처리하지 않는다.

권장:

- `chunking.fallbackCount`는 기존 Blockify/IdeaBlock 집계로 유지
- 공통 `fallbackStatus=APPLIED` 개수는 chunk 상세 조회가 있을 때만 별도 계산
- progress 화면에서 공통 fallback 집계가 없으면 `상세 조회 필요`로 표시

### RAG Chat Debug 화면

debug chunk metadata가 있으면 다음을 추가 표시한다.

```ts
{
  strategy: metadata.strategy,
  requestedChunkingStrategy: metadata.requestedChunkingStrategy,
  actualChunkingStrategy: metadata.actualChunkingStrategy,
  fallbackStatus: metadata.fallbackStatus,
  chunkQualityStatus: metadata.chunkQualityStatus
}
```

일반 답변 화면에는 노출하지 않는다.

## Request 옵션 안내

기본값은 서버가 계속 `recursive + character`로 처리한다.

클라이언트에서 별도 설정 UI가 있는 경우:

| UI | 요청값 | 설명 |
|---|---|---|
| Recursive | `chunkingStrategy=recursive` | 기본 의미 경계 기반 분할 |
| Structure-based | `chunkingStrategy=structure-based` | 구조화 입력 우선 전략 |
| Fixed | `chunkingStrategy=fixed-size` | 균등 분할 및 최종 fallback 전략 |
| Unit: Character | `chunkUnit=character` | 기본값 |
| Unit: Token | `chunkUnit=token` | 명시 선택 시에만 사용 |

Fixed는 token 전용이 아니다. `fixed-size + character`, `fixed-size + token` 모두 허용한다.

## 예시

### 정상 Structure-based Chunk

```json
{
  "strategy": "structure-based",
  "requestedChunkingStrategy": "structure-based",
  "actualChunkingStrategy": "structure-based",
  "fallbackStatus": "NOT_REQUIRED",
  "chunkQualityStatus": "VALID",
  "chunkQualityIssues": [],
  "sourceRef": "page[1]/p[3]"
}
```

표시:

```text
Structure-based
Valid
```

### Structure-based에서 Recursive로 Fallback

```json
{
  "strategy": "recursive",
  "requestedChunkingStrategy": "structure-based",
  "actualChunkingStrategy": "recursive",
  "fallbackStatus": "APPLIED",
  "fallbackFrom": "structure-based",
  "fallbackTo": "recursive",
  "fallbackReason": "missing-structure",
  "chunkQualityStatus": "VALID",
  "chunkQualityIssues": []
}
```

표시:

```text
Structure-based -> Recursive
Strategy Fallback
Reason: missing-structure
Valid
```

### Provenance 부족

```json
{
  "strategy": "structure-based",
  "requestedChunkingStrategy": "structure-based",
  "actualChunkingStrategy": "structure-based",
  "fallbackStatus": "NOT_REQUIRED",
  "chunkQualityStatus": "REVIEW_REQUIRED",
  "chunkQualityIssues": ["MISSING_PROVENANCE"]
}
```

표시:

```text
Structure-based
Review required
Issue: MISSING_PROVENANCE
```

이 경우 fallback이 아니다. 클라이언트는 chunk를 숨기지 말고 주의 상태로 표시한다.

## 완료 기준

- chunk 상세에서 요청 전략과 실제 전략을 구분해 볼 수 있다.
- `fallbackStatus=APPLIED`를 기존 `validationStatus=FALLBACK`과 별도로 표시한다.
- `chunkQualityStatus=REVIEW_REQUIRED` chunk를 오류로 숨기지 않는다.
- provenance 판단이 offset 전용으로 고정되지 않는다.
- `fixed-size + token` 결과가 `recursive`로 잘못 표시되지 않는다.
- 기존 `ragReferences`와 일반 RAG 답변 화면은 변경 없이 동작한다.
