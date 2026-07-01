# 클라이언트 청킹 품질 UX 개선 지시문

## 목적

클라이언트는 공통 청킹 metadata를 이미 표시한다.

이번 작업은 표시된 metadata를 운영자가 실제로 활용할 수 있도록 품질 요약, 필터, provenance badge, strategy 분포, 재처리 추천, RAG debug 표시를 개선한다.

서버 API shape는 변경하지 않는다. 클라이언트는 기존 chunk 목록과 debug metadata에서 값을 파생한다.

## 선행 문서

먼저 다음 문서를 반영한 상태여야 한다.

- `docs/plans/client-chunking-metadata-policy-update-guide.md`

이 문서의 key 해석 규칙을 그대로 사용한다.

## 1. 파일 상세: Chunk 품질 요약 카드

파일 상세의 RAG/Chunk 탭 상단에 문서 단위 요약을 추가한다.

### 집계 항목

| 항목 | 계산식 |
|---|---|
| 전체 Chunk | `chunks.length` |
| Valid | `chunkQualityStatus === "VALID"` |
| Review Required | `chunkQualityStatus === "REVIEW_REQUIRED"` |
| Strategy Fallback | `fallbackStatus === "APPLIED"` |
| Missing Provenance | `chunkQualityIssues`에 `MISSING_PROVENANCE` 포함 |

`chunkQualityStatus`가 없는 legacy chunk는 `Unknown`으로 집계한다.

### UI 상태

| 조건 | 상태 |
|---|---|
| `Review Required > 0` | 주의 |
| `Strategy Fallback > 0` | 정보 |
| `Missing Provenance > 0` | 주의 |
| 전체가 `Valid` | 정상 |

권장 문구:

```text
Chunk 품질: Valid 120 / Review 8 / Fallback 3 / Missing provenance 2
```

## 2. Chunk 목록 필터와 정렬

Chunk 목록에 필터를 추가한다.

### 필터

| 필터 | 값 |
|---|---|
| Quality | `VALID`, `REVIEW_REQUIRED`, `UNKNOWN` |
| Strategy | `actualChunkingStrategy ?? strategy` |
| Strategy Flow | `requestedChunkingStrategy -> actualChunkingStrategy` |
| Fallback | `APPLIED`, `NOT_REQUIRED`, `UNKNOWN` |
| Issue | `MISSING_PROVENANCE`, `MAX_SIZE_EXCEEDED`, `EMPTY_CONTENT` |
| Provenance | `HAS_PROVENANCE`, `NO_PROVENANCE` |

### 정렬

기본 정렬은 기존 chunk order를 유지한다.

추가 정렬 옵션:

- Review required 우선
- Fallback 우선
- Missing provenance 우선

정렬을 바꿔도 사용자가 원래 순서로 돌아갈 수 있어야 한다.

## 3. Provenance Badge

각 chunk row 또는 상세 panel에 provenance badge를 표시한다.

### Badge 판정

| Badge | 조건 |
|---|---|
| `SourceRef` | `sourceRef` 있음 또는 `sourceRefs.length > 0` |
| `Page` | `page` 있음 |
| `Slide` | `slide` 있음 |
| `Block` | `blockIds.length > 0` |
| `Offset` | `startOffset`와 `endOffset` 있음 |
| `No provenance` | 위 조건이 모두 없음 |

`No provenance`는 `chunkQualityIssues`에 `MISSING_PROVENANCE`가 있으면 주의 색상으로 표시한다.

offset만 provenance로 판단하지 않는다.

## 4. Strategy/Fallback 분포

문서 단위 strategy flow 분포를 표시한다.

### Label 생성 규칙

```ts
const requested = metadata.requestedChunkingStrategy;
const actual = metadata.actualChunkingStrategy ?? metadata.strategy ?? "unknown";
const label = requested && requested !== actual ? `${requested} -> ${actual}` : actual;
```

### 표시 예시

```text
Structure-based: 120
Structure-based -> Recursive: 8
Structure-based -> Fixed-size: 1
Blockify -> Structure-based: 12
Unknown: 3
```

분포 항목 클릭 시 해당 strategy flow로 chunk 목록을 필터링한다.

## 5. 재처리 추천 액션

품질 요약 상태에 따라 안내 문구를 표시한다.

자동으로 재처리를 실행하지 않는다. 기존 재색인, 재청킹, 재추출 버튼으로 연결한다.

### 추천 규칙

| 조건 | 권장 액션 | 문구 |
|---|---|---|
| `MISSING_PROVENANCE` 많음 | Markdown/OCR 재추출 | `원문 위치 정보가 부족합니다. Markdown 또는 OCR 재추출을 검토하세요.` |
| `fallbackStatus=APPLIED` 많음 | `recursive` 또는 `fixed-size`로 재청킹 | `구조 기반 청킹이 일부 fallback되었습니다. recursive 또는 fixed-size 재청킹을 검토하세요.` |
| `MAX_SIZE_EXCEEDED` 있음 | chunk max size 증가 후 재청킹 | `일부 chunk가 최대 크기를 초과했습니다. chunk 크기 설정을 늘려 재청킹하세요.` |
| `REVIEW_REQUIRED` 많음 | 상세 검토 후 재청킹 | `검토가 필요한 chunk가 많습니다. issue 유형을 확인한 뒤 재청킹 설정을 조정하세요.` |

“많음”의 기본 기준은 전체 chunk의 10% 이상이다. 클라이언트 설정으로 쉽게 조정할 수 있게 상수화한다.

## 6. RAG Chat Debug Reference 보강

RAG Chat debug chunk 목록에 다음 값을 표시한다.

| 항목 | 값 |
|---|---|
| Strategy | `strategy` |
| Requested | `requestedChunkingStrategy` |
| Actual | `actualChunkingStrategy ?? strategy` |
| Fallback | `fallbackStatus`, `fallbackReason` |
| Quality | `chunkQualityStatus`, `chunkQualityIssues` |
| Provenance | provenance badge |

일반 답변 화면에는 노출하지 않는다.

debug metadata가 없는 응답은 기존 표시를 유지하고 오류로 처리하지 않는다.

## 7. Blockify 표시와의 관계

기존 Blockify badge는 유지한다.

공통 fallback과 Blockify validation fallback은 별도로 표시한다.

| 유형 | 조건 | Badge |
|---|---|---|
| 공통 strategy fallback | `fallbackStatus === "APPLIED"` | `Strategy Fallback` |
| Blockify validation fallback | `validationStatus === "FALLBACK"` | `Blockify Fallback` |
| Blockify 검증 필요 | `validationStatus === "REVIEW_REQUIRED"` | `Blockify Review` |
| 공통 품질 검토 필요 | `chunkQualityStatus === "REVIEW_REQUIRED"` | `Quality Review` |

둘 이상의 badge가 동시에 표시될 수 있다.

## 8. TypeScript 유틸 권장

metadata 파생 로직은 화면 컴포넌트 안에 흩어놓지 말고 공통 유틸로 분리한다.

권장 함수:

```ts
getActualChunkingStrategy(metadata)
getRequestedChunkingStrategy(metadata)
getStrategyFlowLabel(metadata)
getFallbackBadge(metadata)
getQualityBadge(metadata)
getQualityIssues(metadata)
getProvenanceBadges(metadata)
hasProvenance(metadata)
summarizeChunkQuality(chunks)
recommendChunkingActions(summary)
```

## 완료 기준

- 파일 상세에서 문서 단위 chunk 품질 요약을 볼 수 있다.
- Quality, fallback, issue, provenance 기준으로 chunk를 필터링할 수 있다.
- 각 chunk에서 provenance badge를 볼 수 있다.
- Strategy flow 분포를 볼 수 있고 분포 항목으로 필터링할 수 있다.
- 품질 상태에 따른 재처리 추천 문구가 표시된다.
- RAG Chat debug chunk에서 fallback과 quality 상태를 확인할 수 있다.
- 일반 RAG 답변 화면에는 debug metadata가 노출되지 않는다.
- legacy chunk처럼 새 metadata가 없는 경우에도 화면이 깨지지 않는다.
