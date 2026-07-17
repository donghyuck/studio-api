# 클라이언트 Markdown OCR 옵션 반영 지시문

## 목적

Markdown 생성 시 OCR과 math OCR은 비용이 큰 작업이므로 기본 자동 실행하지 않는다.

클라이언트는 사용자가 명시적으로 OCR을 요청한 경우에만 서버에 `ocrMode=FORCE`를 전달한다. 서버는 analyzer 추천은 metadata로 남기지만, 실제 OCR/Pix2Text/Mathpix 실행은 클라이언트 opt-in 요청이 있을 때만 수행한다.

## 서버 계약

기존 API는 그대로 사용한다.

| 목적 | API |
|---|---|
| Markdown 생성 시작 | `POST /api/markdown-documents/from-attachment` |
| 재추출 | `POST /api/markdown-documents/{documentId}/reextract` |
| 재개 | `POST /api/markdown-documents/{documentId}/resume` |
| Resource/metadata 조회 | `GET /api/markdown-documents/{documentId}/resources` |

### 새 요청 옵션

`ocrMode`를 optional field로 추가한다.

| 값 | 의미 | 사용 시점 |
|---|---|---|
| `AUTO` 또는 미지정 | 저부하 기본 경로. analyzer 추천은 metadata로만 기록 | 기본값 |
| `FORCE` | OCR/math OCR 실행을 클라이언트가 명시 요청 | 사용자가 OCR 적용을 켠 경우 |
| `DISABLED` | OCR 금지. analyzer가 OCR을 추천해도 적용하지 않음 | 사용자가 OCR 제외를 선택한 경우 |

기존 `ocrRequired=true`는 backward compatibility 때문에 `FORCE`처럼 동작한다. 신규 클라이언트는 `ocrRequired` 대신 `ocrMode`를 우선 사용한다.

### 요청 예시

기본 저부하 Markdown 생성:

```json
{
  "attachmentId": 6,
  "force": true,
  "ocrMode": "AUTO"
}
```

OCR/math OCR 강제 적용:

```json
{
  "attachmentId": 6,
  "force": true,
  "ocrMode": "FORCE",
  "ocrLanguage": "kor+eng"
}
```

OCR 명시 제외:

```json
{
  "attachmentId": 6,
  "force": true,
  "ocrMode": "DISABLED"
}
```

재추출도 같은 field를 사용한다.

```json
{
  "runChunking": true,
  "runRagIndex": true,
  "ocrMode": "FORCE",
  "ocrLanguage": "kor+eng"
}
```

## 클라이언트 UI 지시

### Markdown 생성 폼

기본 버튼은 기존처럼 `Markdown 생성`을 유지한다.

PDF 첨부파일인 경우에만 OCR 옵션을 노출한다.

| UI | 기본값 | 요청값 |
|---|---|---|
| OCR 적용 안 함 | 기본 선택 | `ocrMode: "AUTO"` 또는 미전송 |
| OCR 적용 | 사용자가 선택 | `ocrMode: "FORCE"` |
| OCR 제외 | 고급 옵션 | `ocrMode: "DISABLED"` |

수학 교재, 스캔 PDF, 한글 이미지 PDF는 사용자가 `OCR 적용`을 켤 수 있게 안내한다. 단, 일반 PDF에서는 기본으로 켜지 않는다.

권장 문구:

| 상황 | 문구 |
|---|---|
| 기본 | `기본 추출` |
| FORCE | `OCR 적용` |
| DISABLED | `OCR 제외` |
| 설명 | `OCR은 처리 시간이 길 수 있습니다. 스캔 PDF, 이미지 PDF, 수학 교재에서만 선택하세요.` |

### 재추출 버튼

기존 Markdown 결과가 품질 이슈를 갖고 있거나 analyzer가 OCR을 추천한 경우 다음 보조 액션을 제공한다.

| 조건 | 액션 |
|---|---|
| `ocrDecisionReason === "OCR_REQUIRES_CLIENT_FORCE"` | `OCR 적용 후 다시 추출` |
| `recommendedRoute === "MATH_DOCUMENT"` and `actualRoute !== "MATH_DOCUMENT"` | `수식 OCR 적용 후 다시 추출` |
| `markdownQualityStatus === "REVIEW_REQUIRED"` | `품질 이슈 확인` |

`OCR 적용 후 다시 추출`은 `POST /reextract`에 `ocrMode: "FORCE"`를 포함한다.

## Metadata 표시

정규화 resource 또는 metadata 응답에서 다음 key를 optional로 읽는다.

| Key | 예시 | 의미 |
|---|---|---|
| `ocrMode` | `AUTO`, `FORCE`, `DISABLED` | 클라이언트 요청 모드 |
| `ocrRequestedBy` | `NONE`, `CLIENT`, `CLIENT_DISABLED`, `ANALYZER_RECOMMENDED` | OCR 요청/추천 주체 |
| `ocrDecisionReason` | `OCR_REQUIRES_CLIENT_FORCE` | 서버 판단 사유 |
| `ocrApplied` | `true`, `false` | 실제 OCR 적용 여부 |
| `ocrLanguage` | `kor+eng` | OCR 언어 |
| `recommendedRoute` / `pdfRecommendedRoute` | `OCR`, `MATH_DOCUMENT` | 서버 추천 경로 |
| `actualRoute` / `pdfActualRoute` | `PYMUPDF4LLM`, `MATH_DOCUMENT` | 실제 처리 경로 |

표시 규칙:

| 조건 | 표시 |
|---|---|
| `ocrApplied === true` | `OCR 적용됨` |
| `ocrMode === "FORCE"` and `ocrApplied !== true` | `OCR 요청됨, 적용 여부 확인 필요` |
| `ocrRequestedBy === "ANALYZER_RECOMMENDED"` | `OCR 권장됨` |
| `ocrDecisionReason === "OCR_REQUIRES_CLIENT_FORCE"` | `OCR 권장됨: 다시 추출 시 OCR 적용 가능` |
| `ocrMode === "DISABLED"` | `OCR 제외됨` |

일반 사용자 화면에는 `OCR 적용됨`, `OCR 권장됨`, `OCR 제외됨` 정도만 표시한다. `ocrDecisionReason`과 route 상세는 metadata/debug 탭에 표시한다.

## TypeScript 타입 권장

```ts
type OcrMode = "AUTO" | "FORCE" | "DISABLED";

type MarkdownExtractionRequest = {
  attachmentId: number;
  force?: boolean;
  runChunking?: boolean;
  runRagIndex?: boolean;
  runSkillExtraction?: boolean;
  ocrMode?: OcrMode;
  ocrLanguage?: string;
};

type MarkdownOcrMetadata = {
  ocrMode?: OcrMode;
  ocrRequired?: boolean;
  ocrRequestedBy?: "NONE" | "CLIENT" | "CLIENT_DISABLED" | "ANALYZER_RECOMMENDED";
  ocrDecisionReason?: string;
  ocrApplied?: boolean;
  ocrLanguage?: string;
  recommendedRoute?: string;
  actualRoute?: string;
  pdfRecommendedRoute?: string;
  pdfActualRoute?: string;
};
```

Helper를 공통화한다.

```ts
function shouldSuggestOcrReextract(metadata: MarkdownOcrMetadata): boolean {
  return metadata.ocrDecisionReason === "OCR_REQUIRES_CLIENT_FORCE"
    || metadata.ocrRequestedBy === "ANALYZER_RECOMMENDED";
}

function ocrBadgeLabel(metadata: MarkdownOcrMetadata): string {
  if (metadata.ocrApplied) return "OCR 적용됨";
  if (metadata.ocrMode === "DISABLED") return "OCR 제외됨";
  if (shouldSuggestOcrReextract(metadata)) return "OCR 권장됨";
  return "기본 추출";
}
```

## 주의 사항

- 신규 클라이언트는 `ocrRequired`를 보내지 않는다. `ocrMode`를 사용한다.
- `ocrRequired=true`가 기존 화면에 남아 있으면 서버는 `FORCE`로 처리하므로 비용이 큰 경로가 실행될 수 있다.
- `ocrMode=AUTO`는 “서버가 필요하면 OCR 실행”이 아니다. 현재 정책에서는 analyzer 추천만 남기고 실제 OCR은 적용하지 않는다.
- `ocrLanguage`는 `ocrMode=FORCE`일 때만 의미가 있다. 한글 PDF 기본 권장값은 `kor+eng`이다.
- 수식 OCR도 OCR opt-in에 포함한다. `MATH_DOCUMENT` 추천만으로 Pix2Text/Mathpix를 호출하지 않는다.

## 테스트 시나리오

- 기본 Markdown 생성 요청에 `ocrMode` 미지정 또는 `AUTO`를 보내면 OCR이 적용되지 않는다.
- 스캔 PDF에서 metadata에 `ocrRequestedBy=ANALYZER_RECOMMENDED`와 `ocrDecisionReason=OCR_REQUIRES_CLIENT_FORCE`가 표시된다.
- `OCR 적용 후 다시 추출` 버튼은 `ocrMode=FORCE`, `ocrLanguage=kor+eng`으로 재추출 요청을 보낸다.
- `ocrMode=FORCE` 요청 후 metadata에 `ocrMode=FORCE`와 `ocrRequestedBy=CLIENT`가 표시된다.
- `ocrMode=DISABLED` 요청 후 OCR 권장 문서에서도 OCR 적용 버튼 또는 자동 재시도가 실행되지 않는다.
- 기존 `ocrRequired=true`를 사용하는 화면이 남아 있으면 `ocrMode=FORCE`로 마이그레이션한다.
