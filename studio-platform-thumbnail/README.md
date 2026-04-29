# studio-platform-thumbnail

Attachment와 독립적으로 동작하는 썸네일 생성 SPI 모듈이다. 입력 source와 옵션을 받아 지원 가능한 renderer를 선택하고, 결과 이미지를 반환한다.

## Core API

- `ThumbnailSource`: `contentType`, `filename`, source bytes
- `ThumbnailOptions`: 요청 크기와 출력 포맷
- `ThumbnailResult`: 생성된 bytes, content type, format
- `ThumbnailRenderer`: source 지원 여부와 렌더링 구현
- `ThumbnailRendererFactory`: 등록 renderer 중 첫 지원 renderer 선택
- `ThumbnailGenerationService`: source size 제한, size/format 정규화, renderer dispatch

## 기본 renderer

- `ImageThumbnailRenderer`: ImageIO 기반 image resize
- `PdfThumbnailRenderer`: PDFBox가 classpath에 있을 때 첫 페이지를 image로 렌더링한 뒤 resize

DOCX/PPTX/HWP/HWPX 같은 문서는 후속 renderer 구현으로 확장한다.
