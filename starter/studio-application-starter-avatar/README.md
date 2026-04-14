# studio-application-starter-avatar

사용자 아바타 이미지 서비스를 자동으로 구성하는 스타터이다.
`studio-application-modules:avatar-service` 모듈의 서비스/리포지토리 빈을 등록하고,
파일 시스템 레플리카 캐시와 REST 엔드포인트를 자동으로 활성화한다.

## 1) 의존성 추가

```kotlin
dependencies {
    implementation(project(":starter:studio-application-starter-avatar"))
    // REST 엔드포인트를 사용할 때
    implementation("org.springframework.boot:spring-boot-starter-web")
    // JPA 영속성 사용 시
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
```

## 2) 기능 활성화

```yaml
studio:
  features:
    avatar-image:
      enabled: true
```

## 3) 설정

`studio.features.avatar-image.*` 속성으로 동작을 제어한다.

```yaml
studio:
  features:
    avatar-image:
      enabled: true                      # 필수: 모듈 활성화
      persistence: jpa                   # jpa | jdbc (기본: 전역 studio.persistence.type 또는 jpa)
      replica:
        base-dir: ""                     # 로컬 레플리카 파일 저장 경로 (비우면 repository 홈/avatars 사용)
        ensure-dirs: true                # 시작 시 디렉터리 자동 생성
        cleanup:
          enabled: true                  # 오래된 레플리카 자동 정리
          cron: "0 0 3 * * *"            # 정리 스케줄 (매일 03:00)
          ttl-days: 30                   # 보관 기간(일)
      web:
        base-path: /api/users            # PublicAvatarController 경로
        mgmt-base-path: /api/mgmt/users  # AvatarController 경로
        self-base: /api/me/avatar        # MeAvatarController 경로
```

## 4) 자동 구성되는 주요 빈

| 빈 | 클래스 | 설명 |
|----|--------|------|
| `AvatarImageService` (내부 delegate) | `AvatarImageServiceImpl` | DB 저장/조회 담당 |
| `AvatarImageService` (primary) | `AvatarImageFilesystemReplicaService` | 파일 레플리카 캐시 데코레이터. 읽기 시 캐시 우선, 쓰기 시 Write-Through |
| `FileReplicaStore` | `FileReplicaStore` | 로컬 파일 레플리카 저장소 |
| `AvatarImageRepository` | `AvatarImageJpaRepository` (JPA) 또는 `AvatarImageJdbcRepository` (JDBC) | 메타데이터 저장소 |
| `AvatarImageDataRepository` | `AvatarImageDataJpaRepository` (JPA) 또는 `AvatarImageDataJdbcRepository` (JDBC) | 바이너리 데이터 저장소 |
| `AvatarController` | `AvatarController` | 관리자 아바타 CRUD |
| `MeAvatarController` | `MeAvatarController` | 로그인 사용자 본인 아바타 관리 |
| `PublicAvatarController` | `PublicAvatarController` | 공개 아바타 이미지 다운로드 |

- JPA 사용 시 `EntityManagerFactory`가 필요하다.
- JDBC 사용 시 `NamedParameterJdbcTemplate` 빈이 필요하다.
- `IdentityService` 빈이 컨텍스트에 있으면 username 기반 조회(`findPrimaryByUsername`)를 지원한다.

## 5) REST 엔드포인트

### 관리 API (`/api/mgmt/users`) — AvatarController

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `GET` | `/{userId}/avatars` | 사용자 아바타 메타 목록 | `features:avatar-image/read` |
| `POST` | `/{userId}/avatars` | 아바타 업로드 (multipart: `file`, `primary`) | `features:avatar-image/write` |
| `GET` | `/{userId}/avatars/exists` | 아바타 존재 여부 및 개수 | — |
| `GET` | `/{userId}/avatars/primary` | 대표 아바타 이미지 다운로드 | `features:avatar-image/read` |
| `PUT` | `/{userId}/avatars/{avatarImageId}/primary` | 대표 이미지 지정 | `features:avatar-image/write` |
| `DELETE` | `/{userId}/avatars/{avatarImageId}` | 아바타 삭제 | `features:avatar-image/write` |
| `POST` | `/{userId}/avatars/{avatarImageId}/resize` | 이미지 리사이즈 | `features:avatar-image/write` |
| `POST` | `/{userId}/avatars/{avatarImageId}/crop` | 이미지 크롭 | `features:avatar-image/write` |
| `PUT` | `/{userId}/avatars/{avatarImageId}/meta` | 메타데이터 수정 (파일명, primary 여부) | `features:avatar-image/write` |

### ME API (`/api/me/avatar`) — MeAvatarController

모든 엔드포인트는 `isAuthenticated()` 조건이 적용된다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/` | 본인 아바타 목록 |
| `POST` | `/` | 본인 아바타 업로드 (multipart: `file`, `primary`) |
| `GET` | `/exists` | 아바타 존재 여부 |
| `GET` | `/primary` | 본인 대표 아바타 이미지 다운로드 |
| `PUT` | `/{avatarImageId}/primary` | 대표 이미지 지정 |
| `DELETE` | `/{avatarImageId}` | 아바타 삭제 |
| `POST` | `/{avatarImageId}/resize` | 이미지 리사이즈 |
| `POST` | `/{avatarImageId}/crop` | 이미지 크롭 |
| `PUT` | `/{avatarImageId}/meta` | 메타데이터 수정 |

### 공개 API (`/api/users`) — PublicAvatarController

인증 없이 접근 가능하다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/{username}/avatar` | username으로 대표 아바타 이미지 다운로드 |
| `GET` | `/{userId}/avatar?byId` | userId로 대표 아바타 이미지 다운로드 |

이미지 다운로드 엔드포인트는 `width`, `height` 쿼리 파라미터로 리사이즈를 지원한다(0이면 원본 크기).
업로드 최대 크기는 5 MB로 제한된다.

## 6) avatar-service 모듈과의 관계

이 스타터는 `studio-application-modules:avatar-service` 모듈에 `api` 의존성으로 연결된다.
avatar-service 모듈은 도메인 모델(`AvatarImage`, `AvatarImageData`)과 서비스/리포지토리 인터페이스를 제공하며,
스타터가 실제 구현 빈과 파일 레플리카 캐시를 자동 구성한다.

## 7) 참고 사항

- `studio.features.avatar-image.enabled=false`로 전체 비활성화할 수 있다.
- `replica.base-dir`을 지정하지 않으면 `Repository` 빈의 홈 디렉터리 하위 `avatars` 폴더를 사용한다.
- DB 저장소는 공유 PK 방식(1:1 관계)으로 `TB_APPLICATION_AVATAR_IMAGE` + `TB_APPLICATION_AVATAR_IMAGE_DATA` 두 테이블을 사용한다.
- 스키마 마이그레이션 파일은 `avatar-service/src/main/resources/schema/avatar/{db}/V700__create_avatar_tables.sql` 에 위치한다.
