응용프로그램을 위한 추가 모듈 프로젝트

## Avatar 사용 가이드
- 의존성 추가: `studio-application-modules/avatar-service` 모듈을 포함하거나 starter 사용 시 `studio-application-starter-avatar` 추가.
- 기능 활성화: `features.avatar-image.enabled=true`.
- 저장소 선택: `features.avatar-image.persistence=jpa`(기본) 또는 `jdbc`.
- 파일 저장: DB에 메타/데이터 저장, 필요 시 replica 파일 시스템 사용. 기본 경로는 repo 기반, 직접 지정하려면 `features.avatar-image.replica.base-dir=/var/app/avatars`.
- API/서비스: `AvatarImageService` 빈을 주입받아 `upload`, `replaceData`, `setPrimary` 등 메서드 사용. 사용자 정보는 `ApplicationUserService`를 통해 처리됨.

## Attachment 사용 가이드
- 의존성 추가: `studio-application-modules/attachment-service` 모듈을 포함하거나 starter 사용 시 `studio-application-starter-attachment` 추가.
- 기능 활성화: `features.attachment.enabled=true`.
- 저장소 타입:
  - 파일 시스템: `features.attachment.storage.type=filesystem` (기본), 경로는 `features.attachment.storage.base-dir`로 지정, 없으면 임시 경로.
  - 데이터베이스: `features.attachment.storage.type=database` + `features.attachment.persistence=jpa|jdbc`. DB 선택 시 `TB_APPLICATION_ATTACHMENT_DATA` 테이블 사용.
  - DB 사용 시 파일 캐시: `features.attachment.storage.cache-enabled=true` 설정 후 `storage.base-dir`로 캐시 경로 지정 가능.
- 서비스 사용: `AttachmentService` 빈의 `createAttachment`, `getInputStream`, `removeAttachment` 등을 통해 메타/파일을 관리.
