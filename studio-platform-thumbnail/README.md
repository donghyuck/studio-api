# studio-platform-thumbnail

Attachment와 독립적으로 동작하는 썸네일 생성 SPI 모듈이다. 입력 source와 옵션을 받아 지원 가능한 renderer를 선택하고, 결과 이미지를 반환한다.

## 패키지 구조

```
studio.one.platform.thumbnail
├── ThumbnailSource              # 입력: contentType, filename, bytes
├── ThumbnailOptions             # 요청 크기, 포맷, 소스 제한
├── ThumbnailResult              # 결과: bytes, contentType, format
├── ThumbnailGenerationOptions   # 서비스 수준 옵션 (기본값, 최대/최소 크기, 소스 제한)
├── ThumbnailRenderer            # SPI 인터페이스: supports + render
├── ThumbnailRendererFactory     # 등록 renderer 중 첫 지원 renderer 선택
├── ThumbnailGenerationService   # 진입점: 소스 크기 검증, 옵션 정규화, renderer dispatch
├── ThumbnailFormats             # 포맷 정규화 유틸리티 (기본: png)
├── ThumbnailImages              # 이미지 스케일링 + ImageIO 기록 유틸리티
├── ThumbnailRenderLimits        # 소스 pixel 크기 검증 유틸리티
├── ThumbnailGenerationException # 렌더링 실패 예외
├── ThumbnailSourceTooLargeException # 소스 크기 초과 예외
└── renderer/
    ├── ImageThumbnailRenderer   # ImageIO 기반 image resize
    ├── EpubThumbnailRenderer    # EPUB2/EPUB3 cover image 추출 + 기본 아이콘
    ├── PdfThumbnailRenderer     # PDFBox 첫 페이지 → image resize
    ├── PptxThumbnailRenderer    # Apache POI 대표 slide → image resize
    ├── DocxThumbnailRenderer    # DOCX marker 인터페이스 (textract 기반 구현용)
    ├── HwpThumbnailRenderer     # HWP marker 인터페이스 (textract 기반 구현용)
    └── HwpxThumbnailRenderer    # HWPX marker 인터페이스 (textract 기반 구현용)
```

## Core API

### ThumbnailSource

렌더링 입력을 캡슐화하는 불변 record.

```java
public record ThumbnailSource(String contentType, String filename, byte[] bytes)
```

- `contentType`: MIME 타입 (예: `image/png`, `application/pdf`)
- `filename`: 원본 파일명 (확장자 기반 렌더러 선택에 사용)
- `bytes`: 원본 바이너리 데이터 (defensive copy 적용)
- `size()`: bytes 배열 길이 반환

### ThumbnailOptions

개별 렌더링 요청의 옵션을 정의하는 불변 record.

```java
public record ThumbnailOptions(int size, String format, long maxSourcePixels, long maxSourceBytes)
```

- `size`: 출력 썸네일의 최대 크기 (pixel, 정사각형 기준)
- `format`: 출력 포맷 (현재 `png`만 지원, 정규화 자동 적용)
- `maxSourcePixels`: 소스 이미지 최대 pixel 수 (DoS 방어)
- `maxSourceBytes`: 소스 데이터 최대 바이트 수 (기본값: 50MB)

### ThumbnailResult

렌더링 결과를 담는 불변 record.

```java
public record ThumbnailResult(byte[] bytes, String contentType, String format)
```

### ThumbnailGenerationOptions

`ThumbnailGenerationService` 수준의 전역 옵션을 정의하는 불변 record.

```java
public record ThumbnailGenerationOptions(
    int defaultSize,          // 기본 썸네일 크기
    String defaultFormat,     // 기본 출력 포맷
    int minSize,              // 최소 허용 크기
    int maxSize,              // 최대 허용 크기
    long maxSourceBytes,      // 최대 소스 바이트
    long maxSourcePixels      // 최대 소스 pixel 수
)
```

### ThumbnailRenderer (SPI)

썸네일 렌더링 구현체가 구현해야 하는 인터페이스.

```java
public interface ThumbnailRenderer {
    boolean supports(ThumbnailSource source);
    ThumbnailResult render(ThumbnailSource source, ThumbnailOptions options);
}
```

- `supports()`: contentType 또는 filename 확장자로 지원 여부를 판단한다
- `render()`: 소스를 렌더링하여 결과 이미지를 반환한다

### ThumbnailRendererFactory

등록된 renderer 목록에서 source를 지원하는 첫 번째 renderer를 선택한다. renderer는 `@Order` 어노테이션으로 우선순위를 지정할 수 있다.

### ThumbnailGenerationService

썸네일 생성의 진입점. 소스 크기 검증, 옵션 정규화, renderer dispatch를 담당한다.

```java
// InputStream 기반 호출
Optional<ThumbnailResult> generate(String contentType, String filename,
                                   InputStream input, int size, String format);

// ThumbnailSource 기반 호출
Optional<ThumbnailResult> generate(ThumbnailSource source, int size, String format);

// 옵션 해석 (크기 clamp, 포맷 정규화)
ThumbnailOptions resolveOptions(int size, String format);
```

## 유틸리티

| 클래스 | 역할 |
|--------|------|
| `ThumbnailFormats` | 포맷 문자열 정규화, 기본 포맷(`png`) 관리, contentType 매핑 |
| `ThumbnailImages` | `BufferedImage` 비례 스케일링 (`bilinear + antialiasing`), ImageIO 바이트 직렬화 |
| `ThumbnailRenderLimits` | 소스 이미지 pixel 수가 제한을 초과하는지 검증 (overflow-safe) |

## 기본 renderer

| Renderer | 우선순위 | 조건 | 설명 |
|----------|----------|------|------|
| `EpubThumbnailRenderer` | `@Order(50)` | 기본 활성 | EPUB3 `cover-image`, EPUB2 cover metadata, 큰 manifest image 순서로 cover를 찾고 없으면 기본 EPUB 아이콘 생성 |
| `ImageThumbnailRenderer` | `@Order(100)` | 항상 (기본 활성) | ImageIO 기반 image resize. JPEG, PNG, GIF, BMP 등 ImageIO가 지원하는 모든 포맷 |
| `PdfThumbnailRenderer` | `@Order(200)` | classpath에 `pdfbox` + `enabled=true` | 첫 페이지(또는 지정 페이지)를 이미지로 렌더링한 뒤 resize |
| `PptxThumbnailRenderer` | `@Order(300)` | classpath에 `poi-ooxml` + `enabled=true` | 대표 slide를 이미지로 렌더링한 뒤 resize |

문서 preview renderer (DOCX/HWP/HWPX)는 `studio-platform-thumbnail-starter`에서 `FileContentExtractionService` bean이 있을 때 textract 기반으로 등록된다. 텍스트를 추출해 document preview 이미지를 생성하는 방식이다.

### Renderer 계층 구조

```
ThumbnailRenderer (interface)
├── EpubThumbnailRenderer               (직접 구현)
├── ImageThumbnailRenderer              (직접 구현)
├── PdfThumbnailRenderer                (직접 구현)
├── PptxThumbnailRenderer               (직접 구현)
├── DocxThumbnailRenderer               (marker interface)
│   └── TextractDocxPreviewThumbnailRenderer  (starter에서 등록)
├── HwpThumbnailRenderer                (marker interface)
│   └── TextractHwpPreviewThumbnailRenderer   (starter에서 등록)
└── HwpxThumbnailRenderer               (marker interface)
    └── TextractHwpxPreviewThumbnailRenderer  (starter에서 등록)
```

Docx/HWP/HWPX marker 인터페이스는 `ThumbnailRenderer`를 확장하며, 기본 구현 없이 starter에서 textract 연동 구현체를 주입한다.

## 설정 프로퍼티

설정은 `studio-platform-thumbnail-starter`의 `ThumbnailProperties`를 통해 바인딩된다.

```yaml
studio:
  thumbnail:
    default-size: 256           # 기본 썸네일 크기 (px)
    default-format: png         # 기본 출력 포맷
    min-size: 16                # 최소 허용 크기
    max-size: 1024              # 최대 허용 크기
    max-source-size: 50MB       # 소스 및 EPUB 압축 해제 총량 최대 크기
    max-source-pixels: 100000000 # 소스 최대 pixel 수 (100M px)
    renderers:
      epub:
        enabled: true           # EPUB cover renderer (기본 활성)
        fallback-min-width: 300
        fallback-min-height: 300
      image:
        enabled: true           # ImageThumbnailRenderer (기본 활성)
      pdf:
        enabled: false          # PdfThumbnailRenderer (opt-in)
        page: 0                 # 렌더링할 페이지 인덱스
      pptx:
        enabled: false          # PptxThumbnailRenderer (opt-in)
        slide: 0                # 렌더링할 슬라이드 인덱스
```

> 문서 renderer (PDF, PPTX)는 보안상 기본 비활성(`opt-in`)이다. 해당 라이브러리가 classpath에 있고 `enabled: true`로 설정해야 활성화된다.

EPUB renderer는 ZIP entry 수, entry별 크기, 전체 압축 해제 크기를 제한하고 DTD/external entity/XInclude를 비활성화한다. OPF manifest의 외부 URI, 절대 경로와 package root 밖으로 나가는 경로는 거부한다. PNG/JPEG/GIF/BMP cover는 기본 지원하며, SVG cover는 `batik-transcoder`와 `batik-codec`이 런타임 classpath에 모두 있을 때만 rasterize한다.

## 사용 예시

```java
@Autowired
private ThumbnailGenerationService thumbnailService;

// InputStream 기반 호출
try (InputStream input = file.getInputStream()) {
    Optional<ThumbnailResult> result = thumbnailService.generate(
            "application/pdf", "report.pdf", input, 256, "png");
    result.ifPresent(thumbnail -> {
        byte[] bytes = thumbnail.bytes();       // 썸네일 이미지 바이트
        String contentType = thumbnail.contentType(); // "image/png"
    });
}
```

## 확장 포인트

커스텀 renderer를 추가하려면 `ThumbnailRenderer` 인터페이스를 구현하고 Spring bean으로 등록한다. `@Order`로 우선순위를 지정할 수 있다.

```java
@Component
@Order(150)
public class SvgThumbnailRenderer implements ThumbnailRenderer {

    @Override
    public boolean supports(ThumbnailSource source) {
        return "image/svg+xml".equals(source.contentType());
    }

    @Override
    public ThumbnailResult render(ThumbnailSource source, ThumbnailOptions options) {
        // SVG → rasterize → resize → ThumbnailResult 반환
    }
}
```

`ThumbnailRendererFactory`가 `ObjectProvider<ThumbnailRenderer>`로 모든 등록 renderer를 수집하므로, bean 등록만으로 자동 통합된다.

## 관련 모듈

| 모듈 | 관계 | 설명 |
|------|------|------|
| `studio-platform-thumbnail-starter` | Starter | `ThumbnailAutoConfiguration`으로 renderer 및 서비스 빈 자동 등록 |
| `studio-platform-textract` | 선택 의존 | DOCX/HWP/HWPX preview renderer가 `FileContentExtractionService` 사용 |
| `attachment-service` | 소비자 | `ThumbnailServiceImpl`이 이 모듈의 `ThumbnailGenerationService`를 사용 |
| `studio-application-starter-attachment` | 소비자 | `AttachmentAutoConfiguration`이 `ThumbnailAutoConfiguration` 이후 구성 |
