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

## 사용법

기존 텍스트 추출 계약은 유지한다.

```java
FileContentExtractionService service = new FileContentExtractionService(parserFactory);
String text = service.extractText(contentType, filename, inputStream);
```

RAG 색인처럼 문단, 표, 이미지, 경고를 함께 다뤄야 하면 구조화 결과를 사용한다.

```java
ParsedFile parsed = service.parseStructured(contentType, filename, inputStream);
String plainText = parsed.plainText();
List<ParsedBlock> blocks = parsed.blocks();
```

`FileParser.parse(byte[], String, String)`은 하위 호환을 위해 계속 `String`을 반환한다.
새 parser는 `StructuredFileParser.parseStructured(...)`를 구현하고, `parse(...)`는 `ParsedFile.plainText()`를 반환하도록 구성한다.

## 지원 포맷

| 포맷 | 추출 범위 | 주요 의존성 |
| --- | --- | --- |
| TXT/CSV/LOG | 전체 텍스트 | JDK |
| HTML | Jsoup 기반 텍스트 | `org.jsoup:jsoup` |
| PDF | PDFBox 기반 텍스트 | `org.apache.pdfbox:pdfbox` |
| DOCX | 문단, 표, header/footer 텍스트 | `org.apache.poi:poi-ooxml` |
| PPTX | slide text shape 텍스트 | `org.apache.poi:poi-ooxml` |
| Image | Tesseract OCR 텍스트, 이미지 metadata | `net.sourceforge.tess4j:tess4j` |
| HWPX | 문단, 표, 이미지 BinData 참조 | JDK ZIP/XML |
| HWP | BodyText 문단, BinData 이미지 목록 | `org.apache.poi:poi` |

## HWP/HWPX 지원 범위

HWP/HWPX parser는 `rhwp`의 파싱 흐름을 Java 서비스용 경량 추출기로 옮긴 것이다.
렌더링/편집 목적이 아니라 RAG 색인과 텍스트 검색을 위한 구조화 추출을 목표로 한다.

- HWPX는 ZIP 패키지의 `content.hpf`, section XML, `BinData`를 읽는다.
- HWPX는 문단 텍스트, 표 셀 텍스트, 표 Markdown, 이미지 참조를 추출한다.
- HWP는 OLE2/POIFS 컨테이너의 `FileHeader`, `DocInfo`, `BodyText/SectionN`, `BinData`를 읽는다.
- HWP는 `BodyText` 문단 텍스트와 `BinData` 이미지 목록을 추출한다.
- HWP 암호화 문서와 배포용 `ViewText` 복호화는 지원하지 않고 `ParseWarning`에 기록한다.
- HWP의 표 셀/행/열 재구성, 그림 컨트롤 위치와 BinData의 정확한 문단 매핑은 후속 개선 대상이다.

## 호환성

기존 `studio-platform-data`의 `studio.one.platform.text.*` 타입은 deprecated wrapper로 남아 있다.
신규 코드는 `studio.one.platform.textract.*` 타입을 직접 사용한다.

## 스타터

자동설정은 `starter/studio-platform-textract-starter`가 제공한다.
설정 prefix는 기존과 같은 `studio.features.text`를 사용한다.

```yaml
studio:
  features:
    text:
      enabled: true
      max-extract-bytes: 10485760
      tesseract:
        datapath: /usr/share/tesseract-ocr/4.00/tessdata
        language: kor+eng
```

포맷별 parser는 관련 라이브러리가 classpath에 있을 때만 등록된다.
예를 들어 HWP parser는 `org.apache.poi.poifs.filesystem.POIFSFileSystem`이 있어야 자동 구성된다.
