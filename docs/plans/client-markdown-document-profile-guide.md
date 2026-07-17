# 클라이언트 Markdown 문서 프로필 적용 지시문

## 목표

Markdown 생성/재추출 화면에서 사용자가 문서 종류만 선택해도 서버 권장 OCR, 수식 보정, 청킹 설정이
적용되게 한다. 기존 세부 옵션은 "고급 설정"에서 선택적으로 override한다.

## API 계약

### 1. 프로필 목록

- `GET /api/markdown-documents/profiles`
- 권한: `features:markdown/read`
- `data[]`의 `id`, `displayName`, `description`, `costTier`, `supportedFormats`와 기본 옵션을 사용한다.
- 클라이언트에 프로필 목록과 기본값을 하드코딩하지 않는다.

### 2. 처리 계획 미리보기

- `POST /api/markdown-documents/processing-plan`
- 생성 요청과 동일한 body를 보내되 실제 작업은 시작하지 않는다.
- 응답의 `data.effectiveOptions`를 최종 적용 예정값으로 표시한다.
- `requestedDocumentProfile`, `resolvedDocumentProfile`, `resolutionReason`, `costTier`를 함께 표시한다.

### 3. 생성 및 재추출

- 생성: `POST /api/markdown-documents/from-attachment`
- 재추출: `POST /api/markdown-documents/{documentId}/reextract`
- 두 요청 모두 선택한 `documentProfile`을 추가한다.

```json
{
  "attachmentId": 6,
  "documentProfile": "MATH_TEXTBOOK",
  "runChunking": true,
  "runRagIndex": true,
  "runSkillExtraction": false,
  "force": false,
  "ocrRequired": null,
  "ocrLanguage": null,
  "ocrMode": null,
  "mathVisionCorrection": null,
  "chunkingStrategy": null,
  "chunkMaxSize": null,
  "chunkOverlap": null,
  "chunkUnit": null
}
```

## 화면 구성

1. "문서 종류" 선택을 생성 폼의 첫 번째 설정으로 둔다. 기본 선택은 `AUTO`다.
2. 옵션 목록은 서버 응답 순서를 사용하고 각 항목에 설명, 지원 형식, 비용 등급을 표시한다.
3. 일반 화면에는 문서 종류, 청킹/RAG 실행 여부만 노출한다.
4. OCR, vision, 청크 크기, 임베딩, skill 옵션은 접힌 "고급 설정"에 둔다.
5. 프로필 변경 시 고급 옵션의 사용자 override를 초기화하고 processing-plan API를 다시 호출한다.
6. 사용자가 고급 옵션을 직접 바꾼 경우에만 해당 필드를 request에 명시한다.
7. 처리 계획 요약에는 실제 적용 OCR 모드/언어, vision 여부, 청킹 전략/크기, 예상 비용 등급을 표시한다.
8. `HIGH` 비용 프로필 또는 vision 활성 plan은 요청 전 확인 문구를 표시하되 실행을 막지는 않는다.

## 상태 모델

- 프로필 기본값을 사용하는 필드는 `null`로 유지한다.
- 명시적 비활성화는 `false` 또는 `DISABLED`로 전송한다.
- 토글은 `inherit | enabled | disabled`의 3상태로 관리한다. UI에서 `inherit`은 "문서 종류 설정 사용"으로 표시한다.
- processing-plan 응답값을 폼의 override 상태에 다시 복사하지 않는다. 화면의 "적용 예정값"으로만 표시한다.
- 기존 저장 화면에서 `documentProfile`이 없으면 `AUTO`로 추정해 덮어쓰지 말고 "기존 설정"으로 표시한다.

## 오류 및 비동기 처리

- profile/plan API 실패 시 기존 수동 설정 화면을 사용할 수 있게 한다.
- 생성/재추출의 `202 Accepted` 이후 기존 pipeline progress polling을 그대로 사용한다.
- 알 수 없는 profile로 `400`이 오면 목록을 새로 조회하고 사용자에게 다시 선택하도록 한다.
- profile 목록은 세션 단위로 캐시하되 배포 후 갱신될 수 있으므로 영구 저장하지 않는다.

## 완료 조건

- 전문 서적, 교과서, 수학 교과서, 스캔 문서, 프레젠테이션을 한 번의 선택으로 요청할 수 있다.
- `MATH_TEXTBOOK` 선택 후 override가 없으면 plan에 OCR `FORCE`, `kor+eng`, vision 활성화가 보인다.
- 같은 프로필에서 OCR을 명시적으로 끄면 plan과 실제 요청에 비활성화가 반영된다.
- profile 변경 시 오래된 override가 남지 않는다.
- 생성과 재추출이 동일한 profile/override 조합을 사용한다.
- profile이 없는 레거시 문서의 결과 보기, metadata, 다운로드 기능이 회귀하지 않는다.
