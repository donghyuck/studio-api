# Attachment Service

첨부파일을 도메인 객체(`objectType`/`objectId`)에 연결하고, 메타데이터 관리·검색·다운로드를 제공하는 모듈이다. `studio-application-starter-attachment` 자동구성을 통해 서비스/엔드포인트/스토리지 빈을 등록하며, 개발자는 저장소 타입과 노출 경로를 설정 값으로 제어할 수 있다.

## 구성 요소
- **AttachmentService / AttachmentServiceImpl**: 생성, 조회, 목록/검색, 삭제, 스트림 로딩을 담당. ID 단위 캐시(`attachments.byId`) 사용.
- **AttachmentRepository**: 메타데이터 저장소. `JdbcAttachmentRepository`(TB_APPLICATION_ATTACHMENT, TB_APPLICATION_ATTACHMENT_PROPERTY) 또는 `AttachmentJpaRepository`로 동작.
- **FileStorage**: 바이너리 저장소. 기본은 `LocalFileStore`; `JpaFileStore`/`JdbcFileStore`로 DB 저장 가능하며, DB 저장 시 `CachedFileStore`로 로컬 캐시 옵션 지원.
- **엔티티**: `ApplicationAttachment`(메타데이터), `ApplicationAttachmentData`(바이너리), 속성 맵은 TB_APPLICATION_ATTACHMENT_PROPERTY에 저장.
- **REST 컨트롤러**: `AttachmentController`가 업로드/다운로드/조회/검색/삭제와 텍스트 추출 API를 제공.

## 자동구성 및 프로퍼티
`studio.features.attachment.*` 속성으로 활성화와 동작을 제어한다(기본값은 주석 참고).

```yaml
studio:
  features:
    attachment:
      enabled: true                 # 필수: 모듈 활성화
      persistence: jpa              # jpa | jdbc (기본: 전역 persistence.type 또는 jpa)
      storage:
        type: filesystem            # filesystem | database
        base-dir: ""                # 비우면 repository 홈 또는 tmp/attachments 사용
        ensure-dirs: true           # 시작 시 디렉터리 생성
        cache-enabled: false        # database 사용 시 로컬 캐시 on/off
      web:
        enabled: true               # REST 엔드포인트 노출 여부 (기본 false)
        base-path: /api/mgmt/attachments
```

### 동작 방식
- `enabled=true` 일 때 `AttachmentServiceImpl`과 저장소 빈을 등록.
- `persistence`가 `jpa` 면 `AttachmentJpaRepository` + `JpaFileStore`(database 선택 시) 사용, `jdbc` 면 `JdbcAttachmentRepository` + `JdbcFileStore`.
- `storage.type=filesystem` → `LocalFileStore`에 바이너리 저장.
- `storage.type=database` → 선택한 persistence 저장소에 바이너리를 넣고, `cache-enabled=true` 시 `LocalFileStore`로 읽기 캐시.
- `web.enabled=true` 시 `AttachmentController`가 등록되며 `base-path` 로 경로가 결정된다.

## REST API (기본 base-path: `/api/mgmt/attachments`)
- `POST /` (multipart) 업로드: `objectType`, `objectId`, `file` 필수. 권한 `features:attachment/upload`.
- `GET /{attachmentId}`: 메타데이터 조회. 권한 `features:attachment/read`.
- `GET /{attachmentId}/text`: 텍스트 추출. `FileContentExtractionService` 빈이 있을 때만 200, 없으면 501. 권한 `features:attachment/read`.
- `GET /{attachmentId}/download`: 스트리밍 다운로드. 권한 `features:attachment/download`.
- `GET /` : 페이지 목록. `objectType`, `objectId`, `keyword` 선택. (컨트롤러 상 별도 PreAuthorize 없음)
- `GET /objects/{objectType}/{objectId}`: 객체별 전체 목록. 권한 `features:attachment/read`.
- `DELETE /{attachmentId}`: 메타데이터 및 바이너리 삭제. 권한 `features:attachment/delete`.

보안은 `@endpointAuthz.can('features:attachment','<action>')` 스코프를 사용하며, 업로드/다운로드/삭제 등 주요 API에 적용되어 있다.

## 데이터 모델
- **TB_APPLICATION_ATTACHMENT**: `ATTACHMENT_ID`(PK), `OBJECT_TYPE`, `OBJECT_ID`, `FILE_NAME`, `CONTENT_TYPE`, `FILE_SIZE`, `CREATED_BY`, `CREATED_AT`, `UPDATED_AT`.
- **TB_APPLICATION_ATTACHMENT_PROPERTY**: 첨부 속성 맵(`PROPERTY_NAME`/`PROPERTY_VALUE`)을 저장.
- **TB_APPLICATION_ATTACHMENT_DATA**: 바이너리 BLOB 저장(DB 스토리지 선택 시 사용).

## 개발 시 참고사항
- 업로드 시 `SecurityHelper.getUser()`가 존재하면 `createdBy`를 세팅한다.
- 삭제는 메타데이터와 바이너리 모두 제거하며, 캐시 스토리지도 비운다.
- 텍스트 추출은 선택 기능이므로, 빈이 없을 때 501(NOT_IMPLEMENTED)을 반환한다.
- 기본 캐시 이름은 `attachments.byId`이며, 캐시 설정이 필요하면 전역 CacheManager에 매핑을 추가한다.

## 빠른 시작
1. `studio.features.attachment.enabled=true` 와 `studio.features.attachment.web.enabled=true` 설정.
2. 필요 시 `studio.features.attachment.persistence`(jpa/jdbc)와 `studio.features.attachment.storage.*` 조정.
3. 권한 스코프(`features:attachment/*`)를 인가 서버 또는 ACL에 등록.
4. (선택) 파일 시스템을 쓸 경우 `base-dir` 접근 권한을 확인하고, DB 저장을 쓸 경우 BLOB 컬럼을 포함한 테이블을 준비한다.
