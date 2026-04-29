# studio-application-starter-attachment

첨부파일 서비스(파일 메타데이터 + 바이너리 스토리지)를 자동으로 구성하는 스타터이다.
`studio-application-modules:attachment-service` 모듈의 서비스/리포지토리/스토리지 빈을 등록하고,
선택적으로 REST 엔드포인트를 노출한다.
feature gate와 web 노출은 `studio.features.attachment.*`를 유지하고, attachment storage/runtime 통합 설정은 `studio.attachment.*`를 사용한다. 썸네일 생성 기본값은 독립 platform thumbnail 서비스의 `studio.thumbnail.*`를 사용한다. `studio.features.attachment.storage.*`, `studio.features.attachment.thumbnail.*`, `studio.attachment.thumbnail.default-size/default-format`는 migration window 동안만 fallback으로 남는다.

## 1) 의존성 추가

```kotlin
dependencies {
    implementation(project(":starter:studio-application-starter-attachment"))
    // REST 엔드포인트를 사용할 때
    implementation("org.springframework.boot:spring-boot-starter-web")
    // JPA 영속성 사용 시
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // PDF 썸네일을 사용할 때만
    implementation("org.apache.pdfbox:pdfbox")
    // PPTX 썸네일을 사용할 때만
    implementation("org.apache.poi:poi-ooxml")
    // DOCX/HWP/HWPX preview 썸네일을 사용할 때만
    implementation(project(":starter:studio-platform-textract-starter"))
}
```

## 2) 기능 활성화

```yaml
studio:
  features:
    attachment:
      enabled: true
```

## 3) 설정

`studio.features.attachment.*`는 feature gate와 web 노출만 담당한다. attachment binary storage와 thumbnail 저장 경로는 `studio.attachment.*`, 썸네일 생성 정책은 `studio.thumbnail.*`에서 제어한다.

```yaml
studio:
  features:
    attachment:
      enabled: true                 # 필수: 모듈 활성화
      persistence: jpa              # jpa | jdbc (기본: 전역 studio.persistence.type 또는 jpa)
      web:
        enabled: false              # REST 엔드포인트 노출 여부 (기본 false)
        base-path: /api/attachments
        mgmt-base-path: /api/mgmt/attachments
        self-base: /api/me/attachments
  attachment:
    storage:
      type: filesystem            # filesystem | database
      base-dir: ""                # 비우면 repository 홈 또는 tmp/attachments 사용
      ensure-dirs: true           # 시작 시 디렉터리 자동 생성
      cache-enabled: false        # database 타입 사용 시 로컬 파일 캐시 on/off
    thumbnail:
      enabled: true               # 썸네일 기능 활성화 여부
      base-dir: ""                # 비우면 attachments/thumbnails 사용
      ensure-dirs: true
  thumbnail:
    default-size: 128             # 기본 썸네일 크기(px)
    default-format: png           # 저장 포맷 (현재 png 지원)
    min-size: 16
    max-size: 512
    max-source-size: 50MB         # 10M 같은 축약형도 허용
    max-source-pixels: 25000000   # decoded image/PDF page pixel 상한
    renderers:
      image:
        enabled: true
      pdf:
        enabled: false            # PDFBox classpath가 있고 명시적으로 true일 때만 등록
        page: 0
      pptx:
        enabled: false            # POI 기반 opt-in renderer
        slide: 0
      docx:
        enabled: false            # textract preview 기반 opt-in renderer
      hwp:
        enabled: false            # textract preview 기반 opt-in renderer
      hwpx:
        enabled: false            # textract preview 기반 opt-in renderer
```

### 스토리지 타입

| 타입 | 설명 |
|------|------|
| `filesystem` | 로컬 파일시스템에 바이너리 저장 (`LocalFileStore`). `base-dir` 경로가 기준이다. |
| `database` | 선택한 persistence(JPA → `JpaFileStore`, JDBC → `JdbcFileStore`)에 바이너리 저장. `cache-enabled=true` 시 로컬 캐시(`CachedFileStore`) 사용. |

## 4) 자동 구성되는 주요 빈

| 빈 | 클래스 | 조건 |
|----|--------|------|
| `AttachmentService` | `AttachmentServiceImpl` | `enabled=true` |
| `FileStorage` | `LocalFileStore` / `JpaFileStore` / `JdbcFileStore` / `CachedFileStore` | 스토리지 타입 및 persistence 설정에 따라 결정 |
| `AttachmentRepository` | `AttachmentJpaRepository` (JPA) 또는 `JdbcAttachmentRepository` (JDBC) | persistence 설정에 따라 결정 |
| `ThumbnailStorage` | `LocalThumbnailStore` | `thumbnail.enabled=true` (기본) |
| `ThumbnailGenerationService` | platform thumbnail service | `studio.features.thumbnail.enabled=true` (기본) |
| `ThumbnailService` | `ThumbnailServiceImpl` | `thumbnail.enabled=true` + `ThumbnailGenerationService` 존재 |
| `AttachmentController`<br>`AttachmentMgmtController`<br>`MeAttachmentController` | 각 컨트롤러 | `web.enabled=true` |

- JPA 사용 시 `EntityManagerFactory`가 필요하다.
- JDBC 사용 시 `NamedParameterJdbcTemplate` 빈이 필요하다.
- `ObjectTypeRuntimeService` 빈이 컨텍스트에 있으면 업로드 시 정책(용량/확장자/MIME) 검증을 수행한다. 없으면 검증을 생략한다.

## 5) REST 엔드포인트

`studio.features.attachment.web.enabled=true` 설정 시 아래 엔드포인트가 등록된다.

### 관리 API (`/api/mgmt/attachments`)

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `POST` | `/` | 파일 업로드 (multipart: `objectType`, `objectId`, `file`) | `features:attachment/upload` |
| `GET` | `/{attachmentId}` | 메타데이터 조회 | `features:attachment/read` |
| `GET` | `/{attachmentId}/text` | 텍스트 추출 (`FileContentExtractionService` 필요) | `features:attachment/read` |
| `GET` | `/{attachmentId}/download` | 스트리밍 다운로드 | `features:attachment/download` |
| `GET` | `/` | 페이지 목록 (`objectType`, `objectId`, `keyword` 선택) | — |
| `GET` | `/objects/{objectType}/{objectId}` | 객체별 전체 목록 | `features:attachment/read` |
| `DELETE` | `/{attachmentId}` | 메타데이터 및 바이너리 삭제 | `features:attachment/delete` |

### 서비스 API (`/api/attachments`)

서비스 간 호출을 위한 경량 API. 권한 스코프는 `features:attachment/service-*` 를 사용한다.
`GET /{attachmentId}/thumbnail`은 원본 파생 콘텐츠를 반환하므로 `features:attachment/service-download` 권한을 사용한다.

### ME API (`/api/me/attachments`)

인증된 사용자 전용. 모든 엔드포인트는 `isAuthenticated()` 조건이 적용된다.

자세한 엔드포인트 목록은 `studio-application-modules/attachment-service/README.md`를 참고한다.

## 6) attachment-service 모듈과의 관계

이 스타터는 `studio-application-modules:attachment-service` 모듈에 `api` 의존성으로 연결된다.
attachment-service 모듈은 도메인 모델(`ApplicationAttachment`, `ApplicationAttachmentData`)과
서비스/리포지토리 인터페이스를 제공하며, 스타터가 실제 구현 빈을 자동 구성한다.

attachment-service를 직접 사용하는 경우에는 스타터 없이 모듈만 의존성으로 추가하고
서비스/리포지토리를 직접 구성할 수 있다. 직접 `ThumbnailServiceImpl`를 구성하는 기존 코드는 deprecated 생성자 호환성을 유지하지만, 신규 구성은 `ThumbnailGenerationService`를 함께 주입하는 생성자를 사용한다.

## 7) 참고 사항

- `studio.features.attachment.enabled=false`로 전체 비활성화할 수 있다.
- 운영 환경에서는 `studio.attachment.storage.base-dir`와 `studio.attachment.thumbnail.base-dir`를 애플리케이션 전용 private 경로로 명시하고 쓰기 권한을 확인한다. 경로를 비우면 tmp 하위 기본 경로를 사용한다.
- PDF/PPTX/DOCX/HWP/HWPX 썸네일은 `studio.thumbnail.renderers.<format>.enabled=true`를 명시했을 때만 생성된다. PDF는 PDFBox, PPTX는 POI, DOCX/HWP/HWPX는 textract `FileContentExtractionService`가 필요하며, 조건이 없으면 image renderer만 등록되거나 해당 source를 지원하지 않는 것으로 처리된다. 저장된 썸네일이 없으면 `/thumbnail`은 `X-Thumbnail-Status: pending` 헤더와 함께 placeholder 이미지를 즉시 반환하고, starter가 등록한 `attachmentThumbnailExecutor`에서 실제 생성을 수행한다. 직접 `ThumbnailServiceImpl`를 구성할 때도 비동기 동작이 필요하면 executor를 받는 생성자를 사용한다. 변환 불가 문서는 bounded TTL 실패 상태를 memoize하고 이후 `X-Thumbnail-Status: unavailable` 204를 반환한다. DOCX/HWP/HWPX preview는 textract parser 표면을 사용하므로 필요한 경우에만 켜고, `studio.textract.max-extract-size`는 압축 입력 크기 제한으로 보수적으로 설정한다. DOCX parser는 압축 해제 work에 별도 entry/total budget을 적용한다.
- DB 스토리지 사용 시 `TB_APPLICATION_ATTACHMENT_DATA` 테이블(BLOB 컬럼 포함)이 준비되어 있어야 한다.
- 업로드 최대 크기는 컨트롤러 수준에서 50 MB로 제한된다.
- 권한 스코프(`features:attachment/*`)를 인가 서버 또는 ACL에 등록해야 한다.
- 기본 캐시 이름은 `attachments.byId`이며, 전역 `CacheManager`에 매핑을 추가하면 캐시가 동작한다.
