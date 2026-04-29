# studio-platform-thumbnail-starter

`studio-platform-thumbnail`의 renderer와 `ThumbnailGenerationService`를 자동 구성하는 스타터다.

PDF renderer는 보안상 기본 비활성화이며, 사용하려면 애플리케이션 런타임에 PDFBox 의존성을 직접 추가하고 설정을 켠다.

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-thumbnail-starter"))
    implementation("org.apache.pdfbox:pdfbox")
}
```

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
```

- `ImageThumbnailRenderer`는 기본 등록된다.
- `PdfThumbnailRenderer`는 PDFBox가 classpath에 있고 `studio.thumbnail.renderers.pdf.enabled=true`를 명시했을 때만 등록된다. PDF는 복잡한 외부 입력을 파싱/렌더링하므로 기본값은 false다.
- `studio.attachment.thumbnail.default-size/default-format`와 `studio.features.attachment.thumbnail.default-size/default-format`는 migration window 동안 `studio.thumbnail.default-size/default-format`의 fallback으로만 읽고 WARN을 출력한다.
- attachment 모듈은 endpoint와 저장소 계약을 유지하고, 실제 생성은 이 스타터가 제공하는 `ThumbnailGenerationService`를 사용한다.
