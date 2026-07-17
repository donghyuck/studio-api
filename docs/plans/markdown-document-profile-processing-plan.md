# Markdown Document Profile Processing Plan

## Outcome

클라이언트가 문서 종류를 선택하면 서버가 OCR, 수식 보정, 구조 청킹 기본값을 일관되게 결정하고,
기존 명시 옵션은 override로 유지한다.

## Scope

- `AUTO`, `GENERAL_DOCUMENT`, `PROFESSIONAL_BOOK`, `TEXTBOOK`, `MATH_TEXTBOOK`,
  `SCANNED_DOCUMENT`, `PRESENTATION`, `TECHNICAL_MANUAL` profile을 제공한다.
- create/reextract 요청에 optional `documentProfile`을 추가한다.
- profile 기본값은 요청에서 명시한 nullable 옵션보다 낮은 우선순위로 적용한다.
- profile 목록과 유효 processing plan 미리보기 API를 제공한다.
- resolved profile과 유효 옵션은 기존 revision `optionsJson`에 저장한다.

## Compatibility

- 기존 endpoint, DB schema, Markdown 결과 계약을 변경하지 않는다.
- `documentProfile`이 없으면 기존 옵션 동작을 유지한다.
- 기존 `runChunking`, `runRagIndex`, `runSkillExtraction` 값은 profile이 변경하지 않는다.
- PDF Analyzer와 runtime fallback이 최종 엔진 선택 권한을 유지한다.

## Acceptance Criteria

- profile 목록에서 식별자, 표시명, 설명, 비용 등급과 기본 옵션을 조회할 수 있다.
- `MATH_TEXTBOOK`은 기본적으로 `structure-based`, `1200/150`, `CHARACTER`, OCR `FORCE`,
  `kor+eng`, math vision 활성 plan을 만든다.
- `PRESENTATION`은 slide 구조에 맞는 구조 청킹 기본값을 만든다.
- 요청의 명시적 OCR/청킹 옵션이 profile 기본값을 override한다.
- profile이 없는 기존 요청의 직렬화와 실행 결과가 회귀하지 않는다.
- create/reextract의 `optionsJson`에 requested/resolved profile과 profile version이 남는다.
