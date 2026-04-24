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
| HTML | Jsoup 기반 semantic block, 표, 이미지 src/alt | `org.jsoup:jsoup` |
| PDF | PDFBox 기반 page/paragraph, 표 후보, 실제 content stream 이미지 | `org.apache.pdfbox:pdfbox` |
| DOCX | 문단, 표, header/footer/footnote/list, 내장 이미지/caption | `org.apache.poi:poi-ooxml` |
| PPTX | slide title/body/footer, picture shape, caption 후보 | `org.apache.poi:poi-ooxml` |
| Image | Tesseract OCR line/word, confidence, bbox, warning metadata | `net.sourceforge.tess4j:tess4j` |
| HWPX | 문단, 표, 이미지 BinData 참조 | JDK ZIP/XML |
| HWP | BodyText 문단, BinData 이미지 목록 | `org.apache.poi:poi` |

## 구조화 결과 계약

`ParsedFile`은 벡터화 파이프라인의 원천 입력으로 사용할 수 있는 구조화 결과를 제공한다.
이 모듈은 chunking, embedding, vector indexing을 수행하지 않는다.

- `plainText`: 기존 문자열 추출 호환용 fallback 텍스트
- `blocks`: `TITLE`, `HEADING`, `PARAGRAPH`, `LIST_ITEM`, `TABLE`, `IMAGE_CAPTION`, `HEADER`, `FOOTER`, `FOOTNOTE`, `OCR_TEXT` 등 구조 block
- `pages`: PDF page, PPTX slide처럼 page/slide 단위 provenance가 필요한 block
- `tables`: 표 markdown, cell 목록, `vectorText`, `headerRowCount`, sourceRef/format metadata
- `images`: 이미지 sourceRef, sourceRefs, binDataRef, packageId, caption, src/altText, OCR metadata
- `warnings`: `canonicalCode`, `severity`, `sourceRef`, `blockRef`, `partialParse` 기반 warning/error

### 표 vector text

표는 `ExtractedTable.markdown()`을 유지하면서, 벡터화 입력에는 `ExtractedTable.vectorText()` 사용을 권장한다.
`vectorText`는 header row가 있으면 data cell을 `header: value` 형태로 정규화한다.
HTML table은 `rowspan`/`colspan`을 고려해 logical column provenance를 계산한다.

```java
for (ExtractedTable table : parsed.tables()) {
    String textForEmbedding = table.vectorText();
    String sourceRef = table.sourceRef();
    int headerRows = table.headerRowCount();
}
```

각 `ExtractedTableCell`은 `row`, `col`, `rowSpan`, `colSpan`, `sourceRef`, `order`, `header`를 제공한다.

### 이미지와 OCR metadata

이미지는 포맷별로 가능한 provenance를 보존한다.

- HTML: `src`, `altText`
- DOCX/PPTX: embedded image filename/content type/size, caption 후보, sourceRef
- PDF: page content stream에서 실제 draw된 image sourceRef
- HWP/HWPX: BinData 경로, packageId, sourceRefs
- OCR image: line 단위 `OCR_TEXT` block, word metadata, line/word bbox, confidence

OCR confidence가 낮으면 `OCR_LOW_CONFIDENCE` warning이 생성된다.
word-level minimum confidence를 사용하므로 line 평균 confidence가 threshold보다 높아도 낮은 confidence word를 감지할 수 있다.

### warning/failure 구분

부분 지원 또는 손실 가능성은 `ParseWarning`으로 표현한다.
complete failure는 `FileParseException`으로 실패한다.

주요 warning code 예:

- `HWP_ENCRYPTED`
- `HWPX_SECTION_MISSING`
- `TABLE_RECONSTRUCTION_PARTIAL`
- `IMAGE_MAPPING_PARTIAL`
- `PPTX_LINKED_IMAGE_PARTIAL`
- `OCR_LOW_CONFIDENCE`

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

## OCR 설정

이미지 OCR은 `net.sourceforge.tess4j:tess4j` 의존성만으로 바로 동작하지 않는다.
`tess4j`는 Java wrapper이고, 실제 OCR 엔진은 외부 `Tesseract` 바이너리와 `tessdata` 언어 데이터가 담당한다.

OCR을 사용하려면 다음이 모두 준비되어야 한다.

- `Tesseract` 엔진 설치
- `tessdata` 언어 데이터 설치
- `studio.features.text.tesseract.datapath`가 `tessdata` 디렉터리를 가리키도록 설정
- 필요 시 native library 검색 경로(`jna.library.path`) 설정

### macOS (Homebrew) 설치 예시

```bash
brew install tesseract
brew install tesseract-lang
```

Apple Silicon(macOS on arm64) 환경에서는 Homebrew 기본 경로가 `/opt/homebrew` 이므로,
JNA가 native library를 찾지 못하면 다음 JVM 옵션이 필요할 수 있다.

```bash
-Djna.library.path=/opt/homebrew/lib
```

예를 들어 shell 환경 변수로는 다음처럼 줄 수 있다.

```bash
export JAVA_TOOL_OPTIONS="-Djna.library.path=/opt/homebrew/lib"
```

`JAVA_TOOL_OPTIONS`는 해당 shell에서 실행되는 모든 JVM 프로세스에 적용된다.
Gradle, Maven, IDE 실행에도 영향을 줄 수 있으므로 운영 환경에서는 애플리케이션 기동 스크립트나 서비스 설정에
`-Djna.library.path=/opt/homebrew/lib`를 직접 추가하는 방식을 우선 검토한다.

### application.yml 예시

```yaml
studio:
  features:
    text:
      enabled: true
      max-extract-bytes: 10485760
      tesseract:
        # macOS(Apple Silicon): /opt/homebrew/share/tessdata
        # Linux: /usr/share/tessdata 또는 /usr/share/tesseract-ocr/4.00/tessdata
        # Windows: C:\Program Files\Tesseract-OCR\tessdata
        datapath: /opt/homebrew/share/tessdata
        language: kor+eng
```

설정 값 설명:

- `datapath`: `kor.traineddata`, `eng.traineddata` 같은 언어 파일이 들어 있는 `tessdata` 디렉터리 경로
- `language`: Tesseract 언어 코드. 여러 언어는 `kor+eng`처럼 `+`로 연결

Linux 계열에서는 `datapath`가 `/usr/share/tesseract-ocr/4.00/tessdata` 또는 `/usr/share/tessdata`일 수 있다.
설치 배포판에 따라 실제 경로를 확인해서 맞춰야 한다.

Windows에서는 Tesseract 설치 경로에 따라 `datapath`가
`C:\Program Files\Tesseract-OCR\tessdata`일 수 있다.
native library 로딩 오류가 나면 `tesseract.exe`가 있는 디렉터리를 `PATH`에 추가하거나,
애플리케이션 JVM 옵션에 `-Djna.library.path=C:\Program Files\Tesseract-OCR`를 지정한다.

### 자주 발생하는 오류

```text
Unable to load library 'tesseract'
```

- `Tesseract` 엔진이 설치되지 않았거나
- JNA가 native library 경로를 찾지 못했거나
- Apple Silicon에서 `/opt/homebrew/lib`가 `jna.library.path`에 포함되지 않은 경우다.

우선 `tesseract --version`으로 설치 여부를 확인하고, 필요하면 `-Djna.library.path=/opt/homebrew/lib`를 추가한다.

```text
language data not found
```

- `tessdata`가 설치되지 않았거나
- `datapath`가 `tessdata` 상위 또는 잘못된 디렉터리를 가리키는 경우다.

`datapath`에는 언어 파일이 직접 들어 있는 `tessdata` 경로를 넣어야 한다.
예를 들어 `kor.traineddata`가 `/opt/homebrew/share/tessdata/kor.traineddata`에 있으면
`datapath`는 `/opt/homebrew/share/tessdata`여야 한다.

포맷별 parser는 관련 라이브러리가 classpath에 있을 때만 등록된다.
예를 들어 HWP parser는 `org.apache.poi.poifs.filesystem.POIFSFileSystem`이 있어야 자동 구성된다.

## 운영 품질 검증

상용 운영 품질 기준에서는 정상 golden path뿐 아니라 실패/부분 성공/resource bound를 함께 확인한다.

권장 검증 명령:

```bash
./gradlew :studio-platform-textract:test
```

운영 failure matrix는 다음 기준을 지킨다.

- corrupt PDF/DOCX/PPTX/image/HWPX 입력은 complete failure로 `FileParseException`을 발생시킨다.
- HTML은 Jsoup parser 특성상 malformed markup을 best-effort로 복구할 수 있으며, corrupt binary 포맷과 동일한 failure로 취급하지 않는다.
- partial support는 `ParseWarning`의 `canonicalCode`, `severity`, `partialParse`로 구분한다.
- oversized file/InputStream은 parser dispatch 전에 차단되어야 한다.
- 포맷별 golden test는 구조화 결과의 block/table/image/warning contract 회귀를 방지한다.
