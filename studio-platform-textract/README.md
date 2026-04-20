# studio-platform-textract

문서 텍스트/구조 추출 모듈이다.

## 요약

- `FileParser` 계약과 `FileParserFactory` dispatcher를 제공한다.
- `FileContentExtractionService`로 파일 또는 `InputStream`에서 텍스트를 추출한다.
- `ParsedFile`로 `plainText`, block 목록, 메타데이터, warning, 표/이미지/OCR 정보를 구조화해 반환한다.
- PDF, DOCX, PPTX, HTML, TXT, 이미지 OCR, HWP/HWPX 파서를 단일 모듈 안에 패키지 수준으로 둔다.
- HWP/HWPX 파서는 `rhwp`의 컨테이너/섹션/문단/컨트롤 흐름을 참고해 HWPX 문단·표·이미지와 HWP 본문·BinData 이미지를 추출한다.

## 패키지

- `studio.one.platform.textract.extractor`: parser 계약, 포맷 판별, dispatcher
- `studio.one.platform.textract.extractor.impl`: 포맷별 parser 구현
- `studio.one.platform.textract.model`: 구조화 추출 결과 모델
- `studio.one.platform.textract.service`: 추출 서비스

## 호환성

기존 `studio-platform-data`의 `studio.one.platform.text.*` 타입은 deprecated wrapper로 남아 있다.
신규 코드는 `studio.one.platform.textract.*` 타입을 직접 사용한다.

## 스타터

자동설정은 `starter/studio-platform-textract-starter`가 제공한다.
설정 prefix는 기존과 같은 `studio.features.text`를 사용한다.
