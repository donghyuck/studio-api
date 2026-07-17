# 클라이언트 Normalized Markdown Pipeline 반영 지시문

## 목적

서버의 Markdown 생성 흐름이 내부적으로 `원본 -> 추출 -> 정규화 -> Markdown 생성 -> 청킹 -> 임베딩 -> 벡터 저장` 구조로 정리되었다.

클라이언트는 기존 Markdown 생성 API와 request shape를 유지하면서, 사용자와 운영자가 처리 상태를 이해할 수 있도록 정규화 결과와 fallback 상태를 선택적으로 표시한다. 새 endpoint를 전제로 하지 않는다.

## 적용 대상

- 첨부파일 상세의 Markdown 생성 영역
- Markdown pipeline progress 화면
- Markdown revision 상세 또는 preview 화면
- RAG/Chunk 상세 및 운영 진단 화면

일반 사용자에게는 복잡한 내부 metadata를 기본 노출하지 않는다. 정규화와 fallback 정보는 상세, 운영, debug 화면 중심으로 표시한다.

## 서버 계약

### 기존 API 유지

클라이언트는 기존 API를 그대로 사용한다.

| 목적 | API |
|---|---|
| Markdown 생성 시작 | `POST /api/markdown-documents/from-attachment` |
| 첨부파일 기준 문서 조회 | `GET /api/markdown-documents/by-attachment/{attachmentId}` |
| Revision 목록 조회 | `GET /api/markdown-documents/{id}/revisions` |
| Pipeline 상태 조회 | `GET /api/markdown-documents/{id}/pipeline` |
| Pipeline progress 조회 | `GET /api/markdown-documents/{id}/pipeline/progress` |
| Locator 조회 | `GET /api/markdown-documents/{id}/locators` |
| Resource 조회 | `GET /api/markdown-documents/{id}/resources` |
| 현재 Markdown 결과 보기 | `GET /api/markdown-documents/{id}/markdown` |
| 특정 Revision Markdown 결과 보기 | `GET /api/markdown-documents/{id}/revisions/{revisionId}/markdown` |
| 재추출 | `POST /api/markdown-documents/{id}/reextract` |
| RAG 재색인 | `POST /api/markdown-documents/{id}/rag/reindex` |

`MarkdownRevision.markdownText()`는 서버 내부 호환성을 위해 계속 최종 Markdown 본문이다. 클라이언트의 Markdown preview, copy, download는 전용 Markdown 결과 API를 우선 사용한다.

상세 지시문은 `docs/plans/client-markdown-result-view-guide.md`를 따른다.

### 정규화 Snapshot Resource

서버는 정규화 결과를 `MarkdownResource`로 저장할 수 있다.

| Field | 값 |
|---|---|
| `resourceType` | `NORMALIZED_DOCUMENT` |
| `name` | `normalized-document.json` |
| `metadataJson.schemaVersion` | `normalized-document-v1` |

클라이언트는 `GET /api/markdown-documents/{id}/resources` 결과에서 `resourceType === "NORMALIZED_DOCUMENT"`이고 `metadataJson.schemaVersion === "normalized-document-v1"`인 resource를 선택적으로 해석한다.

해당 resource가 없으면 legacy 문서 또는 fallback 문서로 보고 오류 처리하지 않는다.

## 새 Metadata 해석

### Normalization Metadata

`NORMALIZED_DOCUMENT` resource의 `metadataJson`에는 다음 값을 기대할 수 있다.

| Key | 값 예시 | 의미 |
|---|---|---|
| `schemaVersion` | `normalized-document-v1` | snapshot schema version |
| `normalizationStatus` | `VALID`, `REVIEW_REQUIRED` | 정규화 품질 상태 |
| `normalizationIssues` | `["EMPTY_BLOCKS_FALLBACK"]` | 정규화 이슈 목록 |
| `normalizationSource` | `NATIVE_PARSED_FILE`, `PANDOC_MARKDOWN`, `MARKDOWN_FALLBACK` | 정규화 입력 경로 |
| `blockCount` | `120` | 정규화 block 수 |
| `tableCount` | `3` | 표 block 수 |
| `imageCount` | `5` | 이미지 block 수 |
| `pageCount` | `12` | page 정보 수 |

`normalizationStatus=REVIEW_REQUIRED`는 실패가 아니다. Markdown 생성과 후속 청킹은 계속 진행될 수 있으므로 warning 상태로 표시한다.

### Chunk Metadata

청킹 결과 metadata에는 정규화 snapshot 사용 여부가 additive로 포함될 수 있다.

| Key | 값 예시 | 의미 |
|---|---|---|
| `normalizedSnapshotUsed` | `true`, `false` | 청킹 입력으로 normalized blocks를 사용했는지 |
| `normalizationStatus` | `VALID`, `REVIEW_REQUIRED` | 청킹 입력으로 사용한 snapshot 품질 상태 |
| `normalizationIssues` | `["MISSING_SOURCE_REF"]` | 정규화 이슈 목록 |
| `normalizationSource` | `NATIVE_PARSED_FILE`, `PANDOC_MARKDOWN`, `MARKDOWN_FALLBACK` | 정규화 입력 경로 |

`normalizedSnapshotUsed=false`이면 서버가 기존 Markdown/locator 기반 청킹으로 fallback한 것이다. 검색 실패로 취급하지 말고 운영 진단 badge로 표시한다.

## 화면 지시

### Markdown 생성 버튼

기본 CTA는 기존처럼 `Markdown 생성`을 유지해도 된다.

다만 버튼 아래 또는 progress 영역에는 내부 단계를 다음처럼 표시한다.

```text
원본
추출
정규화
Markdown
청킹
임베딩
벡터 저장
```

현재 서버 API가 정규화만의 독립 실행 상태를 노출하지 않으므로, 클라이언트는 정규화를 별도 blocking 단계로 polling하지 않는다. Revision 완료 후 `NORMALIZED_DOCUMENT` resource가 있으면 정규화 결과를 표시한다.

### Pipeline Progress

기존 `/pipeline`과 `/pipeline/progress` 응답을 주 상태로 사용한다.

정규화 표시는 다음 규칙으로 파생한다.

| 조건 | 표시 |
|---|---|
| Revision `RUNNING` | `추출/Markdown 생성 중` |
| Revision `COMPLETED` + normalized resource 있음 | `정규화 완료` 또는 `정규화 검토 필요` |
| Revision `COMPLETED` + normalized resource 없음 | `정규화 정보 없음` |
| Revision `FAILED` | 기존 실패 상태 표시 |

`정규화 정보 없음`은 legacy 또는 fallback 가능성이 있으므로 오류 색상이 아니라 중립 상태로 표시한다.

### Revision 상세

Revision 상세에는 다음 요약을 추가한다.

| 항목 | 값 |
|---|---|
| Markdown 상태 | 기존 revision status |
| 정규화 상태 | `normalizationStatus` |
| 정규화 경로 | `normalizationSource` |
| Block 수 | `blockCount` |
| Table 수 | `tableCount` |
| Image 수 | `imageCount` |
| Page 수 | `pageCount` |

`normalizationIssues`가 있으면 접을 수 있는 상세 영역에 표시한다.

### RAG/Chunk 상세

기존 청킹 metadata 표시 지침에 다음 항목을 추가한다.

| 항목 | 표시 조건 |
|---|---|
| Normalized input | `normalizedSnapshotUsed === true` |
| Markdown fallback input | `normalizedSnapshotUsed === false` |
| Normalization review | `normalizationStatus === "REVIEW_REQUIRED"` |
| Normalization source | `normalizationSource` 있음 |

일반 RAG 답변 화면에는 이 값을 표시하지 않는다. RAG debug 또는 운영 화면에서만 표시한다.

## TypeScript 타입 권장

클라이언트에는 optional 타입으로 추가한다.

```ts
type NormalizationStatus = "VALID" | "REVIEW_REQUIRED";

type NormalizationSource =
  | "NATIVE_PARSED_FILE"
  | "PANDOC_MARKDOWN"
  | "MARKDOWN_FALLBACK";

type NormalizedDocumentResourceMetadata = {
  schemaVersion?: "normalized-document-v1";
  normalizationStatus?: NormalizationStatus;
  normalizationIssues?: string[];
  normalizationSource?: NormalizationSource;
  blockCount?: number;
  tableCount?: number;
  imageCount?: number;
  pageCount?: number;
};

type ChunkNormalizationMetadata = {
  normalizedSnapshotUsed?: boolean;
  normalizationStatus?: NormalizationStatus;
  normalizationIssues?: string[];
  normalizationSource?: NormalizationSource;
};
```

파생 로직은 화면 컴포넌트에 직접 흩어두지 말고 공통 helper로 분리한다.

```ts
findNormalizedDocumentResource(resources)
getNormalizationBadge(resource)
getNormalizationSourceLabel(source)
getChunkNormalizationBadge(metadata)
hasNormalizedChunkInput(metadata)
```

## 표시 문구 권장

| 상태 | 문구 |
|---|---|
| `VALID` | `정규화 완료` |
| `REVIEW_REQUIRED` | `정규화 검토 필요` |
| resource 없음 | `정규화 정보 없음` |
| `NATIVE_PARSED_FILE` | `Native extraction` |
| `PANDOC_MARKDOWN` | `Pandoc markdown` |
| `MARKDOWN_FALLBACK` | `Markdown fallback` |
| `normalizedSnapshotUsed=true` | `Normalized blocks` |
| `normalizedSnapshotUsed=false` | `Markdown fallback` |

## Format별 경로 표시

서버 설정에 따라 변환 경로는 달라질 수 있으므로 클라이언트가 format별 동작을 hard-code하지 않는다.

현재 운영 기준의 설명 문구는 다음 정도로 제한한다.

| 입력 | 설명 |
|---|---|
| `docx`, `html` | Pandoc 변환 결과를 정규화한 뒤 Markdown을 생성할 수 있다. |
| 그 외 format | Native extraction 결과를 정규화한 뒤 Markdown을 생성할 수 있다. |
| Pandoc 실패 | 서버 설정에 따라 native extraction fallback이 적용될 수 있다. |

실제 표시 우선순위는 format 추정이 아니라 `normalizationSource` 값이다.

## 오류 처리

- `NORMALIZED_DOCUMENT` resource가 없어도 화면을 깨지 않는다.
- `metadataJson` parsing 실패는 client-side warning log 정도로 처리하고 기존 Markdown preview를 유지한다.
- `normalizationStatus=REVIEW_REQUIRED`는 실패가 아니다.
- `normalizedSnapshotUsed=false`는 검색 실패가 아니다.
- `normalizationIssues`에 알 수 없는 값이 있어도 원문 문자열을 그대로 표시한다.

## 완료 기준

- Markdown 생성 CTA는 유지하되, 상세 progress에서 `원본 -> 추출 -> 정규화 -> Markdown -> 청킹 -> 임베딩 -> 벡터 저장` 흐름을 이해할 수 있다.
- Revision 완료 후 normalized resource가 있으면 정규화 상태, source, block/table/image/page count를 볼 수 있다.
- normalized resource가 없는 legacy 문서도 기존처럼 Markdown preview가 동작한다.
- RAG/Chunk 상세에서 normalized blocks 사용 여부와 Markdown fallback 여부를 확인할 수 있다.
- `REVIEW_REQUIRED`는 warning으로 표시되고 실패로 오인되지 않는다.
- 새 metadata가 없는 응답에서도 기존 화면이 깨지지 않는다.

## 테스트 시나리오

- DOCX 또는 HTML 입력에서 `normalizationSource=PANDOC_MARKDOWN` resource를 표시한다.
- Native extraction 입력에서 `normalizationSource=NATIVE_PARSED_FILE` resource를 표시한다.
- `normalizationStatus=REVIEW_REQUIRED`와 issue list를 warning으로 표시한다.
- normalized resource가 없는 문서에서 기존 Markdown preview와 pipeline 상태가 유지된다.
- chunk metadata의 `normalizedSnapshotUsed=true`를 `Normalized blocks`로 표시한다.
- chunk metadata의 `normalizedSnapshotUsed=false`를 `Markdown fallback`으로 표시한다.
- 알 수 없는 `normalizationIssues` 값이 있어도 화면이 깨지지 않는다.
