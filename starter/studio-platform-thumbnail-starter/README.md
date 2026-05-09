# studio-platform-thumbnail-starter

`studio-platform-thumbnail`의 renderer와 `ThumbnailGenerationService`를 자동 구성하는 스타터다.

PDF/PPTX/DOCX/HWP/HWPX renderer는 보안상 기본 비활성화이며, 사용하려면 애플리케이션 런타임에 필요한 의존성과 설정을 준비한다.

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-thumbnail-starter"))
    // PDF renderer에 필요
    implementation("org.apache.pdfbox:pdfbox")
    // PPTX renderer에 필요
    implementation("org.apache.poi:poi-ooxml")
    // DOCX/HWP/HWPX preview renderer에 필요
    implementation(project(":starter:studio-platform-textract-starter"))
}
```

PPTX renderer는 Apache POI로 slide를 직접 그린다. DOCX/HWP/HWPX renderer는 textract의 구조화 추출 결과를 preview 이미지로 그리므로, 문서 레이아웃의 정확한 rasterize가 아니라 검색/관리 화면용 대표 preview다.

```yaml
studio:
  features:
    thumbnail:
      enabled: true
  thumbnail:
    default-size: 128
    default-format: png
    min-size: 16
    max-size: 512
    max-source-size: 50MB
    max-source-pixels: 25000000
    renderers:
      image:
        enabled: true
      pdf:
        enabled: false
        page: 0
      pptx:
        enabled: false
        slide: 0
      docx:
        enabled: false
      hwp:
        enabled: false
      hwpx:
        enabled: false
```

- `ImageThumbnailRenderer`는 기본 등록된다.
- `PdfThumbnailRenderer`는 PDFBox가 classpath에 있고 `studio.thumbnail.renderers.pdf.enabled=true`를 명시했을 때만 등록된다. PDF는 복잡한 외부 입력을 파싱/렌더링하므로 기본값은 false다.
- `PptxThumbnailRenderer`는 POI OOXML이 classpath에 있고 `studio.thumbnail.renderers.pptx.enabled=true`일 때 등록된다.
- DOCX/HWP/HWPX preview renderer는 `FileContentExtractionService` bean이 있고 각 renderer를 명시적으로 enabled 했을 때 등록된다. 지원 renderer가 없거나 추출할 수 없는 문서는 attachment `/thumbnail`에서 204를 반환한다.
- DOCX/HWP/HWPX preview renderer는 textract parser 표면을 함께 사용하므로 필요한 경우에만 켜고, `studio.textract.max-extract-size`를 운영 환경에 맞게 보수적으로 유지한다.
- `studio.attachment.thumbnail.default-size/default-format`와 `studio.features.attachment.thumbnail.default-size/default-format`는 migration window 동안 `studio.thumbnail.default-size/default-format`의 fallback으로만 읽고 WARN을 출력한다.
- attachment 모듈은 endpoint와 저장소 계약을 유지하고, 실제 생성은 이 스타터가 제공하는 `ThumbnailGenerationService`를 사용한다.
