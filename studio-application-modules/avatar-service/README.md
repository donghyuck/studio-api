# Avatar Service

사용자 아바타 이미지 관리 모듈이다.
아바타 메타데이터와 바이너리 데이터를 분리 저장하고, 파일 시스템 레플리카 캐시를 통해 빠른 이미지 서빙을 지원한다.
직접 의존하거나 `:starter:studio-application-starter-avatar`를 통해 자동 구성할 수 있다.

## 사용 요약

- 기능 활성화: `studio.features.avatar-image.enabled=true`
- 영속성: `studio.features.avatar-image.persistence=jpa|jdbc`
- 파일 복제 경로: `studio.features.avatar-image.replica.base-dir`
- 대응 스타터: `:starter:studio-application-starter-avatar`

## 컴포넌트 구조

| 컴포넌트 | 클래스 | 역할 |
|----------|--------|------|
| 서비스 인터페이스 | `AvatarImageService` | 아바타 업로드/조회/삭제/대표 이미지 지정 계약 |
| 서비스 구현체 | `AvatarImageServiceImpl` | DB 저장/조회 담당. `AvatarImageRepository` + `AvatarImageDataRepository` 사용 |
| 레플리카 데코레이터 | `AvatarImageFilesystemReplicaService` | `AvatarImageService`를 감싸는 파일 캐시 계층. 읽기는 캐시 우선(Cache-Aside), 쓰기는 Write-Through |
| 레플리카 저장소 | `FileReplicaStore` | 로컬 파일 시스템 기반 이미지 파일 관리. 디렉터리 구조: `{baseDir}/{userId}/{avatarImageId}/` |
| 메타데이터 리포지토리 | `AvatarImageJpaRepository` / `AvatarImageJdbcRepository` | `TB_APPLICATION_AVATAR_IMAGE` 테이블 접근 |
| 바이너리 리포지토리 | `AvatarImageDataJpaRepository` / `AvatarImageDataJdbcRepository` | `TB_APPLICATION_AVATAR_IMAGE_DATA` 테이블 접근 |

### 레플리카 캐시 동작

- **읽기**: 캐시 파일이 있으면 파일에서 반환, 없으면 DB에서 읽어 캐시 생성 후 반환.
- **쓰기**: DB 저장 후 즉시 파일 캐시 동기화(Write-Through).
- **삭제**: DB 삭제 후 해당 사용자/이미지 캐시 디렉터리 삭제.
- **자동 정리**: `@Scheduled(cron = "0 15 3 * * *")`로 오래된 레플리카 파일을 주기적으로 정리한다.

## 설정 속성

```yaml
studio:
  features:
    avatar-image:
      enabled: true
      persistence: jpa                   # jpa | jdbc
      replica:
        base-dir: /var/lib/app/avatars   # 비우면 Repository 빈 홈/avatars 사용
        ensure-dirs: true
        cleanup:
          enabled: true
          cron: "0 0 3 * * *"
          ttl-days: 30
      web:
        base-path: /api/users            # PublicAvatarController
        mgmt-base-path: /api/mgmt/users  # AvatarController
        self-base: /api/me/avatar        # MeAvatarController
```

## REST 엔드포인트 목록

### 관리 API (`/api/mgmt/users`) — AvatarController

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `GET` | `/{userId}/avatars` | 아바타 목록 | `features:avatar-image/read` |
| `POST` | `/{userId}/avatars` | 아바타 업로드 (multipart: `file`, `primary`) | `features:avatar-image/write` |
| `GET` | `/{userId}/avatars/exists` | 아바타 존재 여부 + 개수 | — |
| `GET` | `/{userId}/avatars/primary` | 대표 이미지 다운로드 | `features:avatar-image/read` |
| `PUT` | `/{userId}/avatars/{avatarImageId}/primary` | 대표 이미지 지정 | `features:avatar-image/write` |
| `DELETE` | `/{userId}/avatars/{avatarImageId}` | 아바타 삭제 | `features:avatar-image/write` |
| `POST` | `/{userId}/avatars/{avatarImageId}/resize` | 리사이즈 (`width`, `height`, `fit`) | `features:avatar-image/write` |
| `POST` | `/{userId}/avatars/{avatarImageId}/crop` | 크롭 (`width`, `height`) | `features:avatar-image/write` |
| `PUT` | `/{userId}/avatars/{avatarImageId}/meta` | 메타데이터 수정 (파일명, primary 여부) | `features:avatar-image/write` |

### ME API (`/api/me/avatar`) — MeAvatarController

모든 엔드포인트는 `isAuthenticated()` 조건이 적용되며, 인증 사용자 본인 아바타만 접근 가능하다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/` | 본인 아바타 목록 |
| `POST` | `/` | 본인 아바타 업로드 (multipart: `file`, `primary`) |
| `GET` | `/exists` | 아바타 존재 여부 |
| `GET` | `/primary` | 본인 대표 이미지 다운로드 |
| `PUT` | `/{avatarImageId}/primary` | 대표 이미지 지정 |
| `DELETE` | `/{avatarImageId}` | 아바타 삭제 |
| `POST` | `/{avatarImageId}/resize` | 리사이즈 |
| `POST` | `/{avatarImageId}/crop` | 크롭 |
| `PUT` | `/{avatarImageId}/meta` | 메타데이터 수정 |

### 공개 API (`/api/users`) — PublicAvatarController

인증 없이 접근 가능하다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/{username}/avatar` | username으로 대표 아바타 이미지 다운로드 |
| `GET` | `/{userId}/avatar?byId` | userId로 대표 아바타 이미지 다운로드 |

이미지 다운로드 엔드포인트는 `width`, `height` 쿼리 파라미터로 리사이즈를 지원한다.
업로드 최대 크기는 5 MB로 제한된다.

## 데이터 모델

### TB_APPLICATION_AVATAR_IMAGE (메타데이터)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `AVATAR_IMAGE_ID` | BIGSERIAL (PK) | 아바타 이미지 ID |
| `USER_ID` | BIGINT (NOT NULL) | 사용자 ID. 인덱스(`TB_APPLICATION_AVATAR_IMAGE_IDX1`) 설정 |
| `PRIMARY_IMAGE` | BOOLEAN (NOT NULL) | 대표 이미지 여부 (기본 true) |
| `FILE_NAME` | VARCHAR(255) | 파일명 |
| `FILE_SIZE` | BIGINT | 파일 크기(바이트) |
| `CONTENT_TYPE` | VARCHAR(50) | MIME 타입 |
| `CREATION_DATE` | TIMESTAMPTZ | 생성 일시 |
| `MODIFIED_DATE` | TIMESTAMPTZ | 수정 일시 |

### TB_APPLICATION_AVATAR_IMAGE_DATA (바이너리)

공유 PK 방식(`@MapsId`)으로 `TB_APPLICATION_AVATAR_IMAGE`와 1:1 연결된다. 이미지 바이너리(BLOB)를 저장한다.

## 스키마 위치

```
src/main/resources/schema/avatar/
  ├── postgres/V700__create_avatar_tables.sql
  ├── mariadb/V700__create_avatar_tables.sql
  └── mysql/V700__create_avatar_tables.sql
```

## 제공 기능

- 사용자별 아바타 업로드와 교체
- 대표 이미지(primary) 지정
- 파일 시스템 레플리카 캐시로 빠른 이미지 서빙
- username 또는 userId 기반 대표 이미지 공개 조회
- 리사이즈/크롭 변환 지원
