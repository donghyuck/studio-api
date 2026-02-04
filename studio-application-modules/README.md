응용프로그램을 위한 추가 모듈 모음. 각 모듈은 starter 의존성을 통해 자동구성이 제공된다.

## Avatar 서비스
- 의존성: `studio-application-modules/avatar-service` 또는 `studio-application-starter-avatar`.
- 활성화: `studio.features.avatar-image.enabled=true`.
- 저장소: `studio.features.avatar-image.persistence=jpa|jdbc`(기본 jpa), 파일 복제 경로는 `studio.features.avatar-image.replica.base-dir` 로 지정.
- 사용법: `AvatarImageService` 로 `upload`, `replaceData`, `setPrimary` 등을 호출. 사용자 식별자는 `userId`/`username`으로 전달.

## Attachment 서비스
- 의존성: `studio-application-modules/attachment-service` 또는 `studio-application-starter-attachment`.
- 활성화: `studio.features.attachment.enabled=true` (+ REST 사용 시 `studio.features.attachment.web.enabled=true`).
- 저장소 옵션:
  - 파일 시스템: `studio.features.attachment.storage.type=filesystem` (기본), 경로는 `studio.features.attachment.storage.base-dir`, 없으면 tmp/attachments.
  - 데이터베이스: `studio.features.attachment.storage.type=database` + `studio.features.attachment.persistence=jpa|jdbc`, BLOB 테이블 `TB_APPLICATION_ATTACHMENT_DATA` 사용.
  - DB + 캐시: `studio.features.attachment.storage.cache-enabled=true` 로 로컬 파일 캐시 활성화.
- API/서비스: `AttachmentService` 로 생성/조회/삭제/스트림 조회, REST 컨트롤러는 `/api/mgmt/attachments`(또는 `studio.features.attachment.web.mgmt-base-path`) 이하 업로드·다운로드·검색 제공.

## Content Embedding Pipeline
- 역할: 첨부파일 텍스트를 추출하고 임베딩을 생성해 벡터 스토어에 업서트하거나 RAG 인덱스를 구축하는 파이프라인 API.
- 의존성: `studio-application-modules/content-embedding-pipeline` + `studio-application-modules/attachment-service` + `studio-platform-ai` (임베딩/벡터/RAG 포트).
- 전제 조건:
  - 텍스트 추출기(`FileContentExtractionService`) 빈
  - 임베딩 프로바이더(`EmbeddingPort`) 빈
  - 벡터 스토어(`VectorStorePort`)가 있어야 저장 가능, 없으면 임베딩만 반환
  - RAG 사용 시 `RagPipelineService` 빈 필요
- 엔드포인트(기본 `studio.features.attachment.web.mgmt-base-path`, 예: `/api/mgmt/attachments`):
  - `GET /{id}/embedding?storeVector=true|false`: 텍스트 추출 후 임베딩 생성, storeVector=true 시 벡터 스토어 업서트.
  - `GET /{id}/embedding/exists`: 해당 첨부 벡터 존재 여부 확인.
  - `POST /{id}/rag/index`: 추출 텍스트를 RAG 인덱스에 등록(메타데이터/키워드 옵션 포함).
  - `POST /rag/search`: RAG 인덱스 검색.
- 권한: 컨트롤러는 attachment 스코프 인가(`features:attachment` write/read)와 동일하게 동작. 빈이 없을 경우 501(NOT_IMPLEMENTED)로 안내 응답.

## Mail 서비스
- 의존성: `studio-application-modules/mail-service` 또는 `studio-application-starter-mail`.
- 활성화: `studio.features.mail.enabled=true`.
- 저장소: `studio.features.mail.persistence=jpa|jdbc` (전역 `studio.persistence.type` 미설정 시 기본 jpa).
- IMAP 설정: `studio.features.mail.imap.*` 로 호스트/포트/계정/동시성/본문·첨부 크기 제한을 지정.
- REST: `studio.features.mail.web.enabled=true` 시 `/api/mgmt/mail` 기본 경로에 컨트롤러 노출(`GET /{mailId}`, `POST /sync`, `GET /sync/logs`, `GET /sync/logs/page`).
- SSE: 동기화 상태 스트림을 분리된 컨트롤러(`/sync/stream`)로 제공하며, `studio.features.mail.web.sse=true|false`(기본 true)로 노출 여부를 제어.
