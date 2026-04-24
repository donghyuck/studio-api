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
