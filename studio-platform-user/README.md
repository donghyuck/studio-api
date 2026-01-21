# studio-platform-user

사용자 도메인(유저/그룹/권한/회사)을 위한 핵심 모델과 서비스 인터페이스를 제공하는 모듈이다.
직접 구현(기본 엔티티/리포지토리/서비스/컨트롤러)은 `studio-platform-user-default`로 분리된다.

## 구성 패키지
- `studio.one.base.user.domain.entity`  
  JPA 엔티티 집합 (ApplicationGroup, ApplicationRole, ApplicationCompany 등)
- `studio.one.base.user.domain.model`  
  도메인 모델/Enum 및 JSON 직렬화 보조
- `studio.one.base.user.domain.event`  
  사용자 상태 변경 이벤트 (활성/비활성/비밀번호 초기화 등)
- `studio.one.base.user.domain.event.listener`  
  캐시 무효화 리스너
- `studio.one.base.user.service`  
  서비스 인터페이스 (User/Group/Role/Company)
- `studio.one.base.user.persistence`  
  리포지토리 인터페이스
- `studio.one.base.user.web.dto`  
  요청/응답 DTO
- `studio.one.base.user.exception`  
  도메인 예외
- `studio.one.base.user.constant`  
  캐시명, 엔티티명 상수

## 영속성 구현
`studio-platform-user-default`에서 JPA/JDBC 구현을 제공한다.

## 주요 서비스
- `ApplicationUserService`: 사용자 CRUD, 상태 변경, 비밀번호 초기화 등
- `ApplicationGroupService`: 그룹 관리 및 멤버십 처리
- `ApplicationRoleService`: 권한/역할 관리
- `ApplicationCompanyService`: 회사(테넌트) 관리
- `UserMutator`: 사용자 변경 훅 (기본 구현 제공)

## 이벤트와 캐시
사용자 상태 변경 이벤트를 발행하며, `UserCacheEvictListener`가 캐시를 정리한다.
캐시 키는 `studio.one.base.user.constant.CacheNames`에 정의되어 있다.

## 웹 계층
컨트롤러/매퍼는 `studio-platform-user-default`에서 제공한다. REST 노출 여부는 스타터 설정에서 제어한다.

### 공개용 사용자 조회
사용자 모듈 직접 의존성을 줄이기 위해 공개용 기본 정보 API를 제공한다.
이 엔드포인트는 `nameVisible`, `emailVisible` 플래그를 반영해 공개 가능한 값만 반환하며,
`USER_ENABLED = true`인 사용자만 조회한다. 공개 API의 `name` 파라미터는 내부적으로
`username` 의미로 처리한다.

- `GET /api/users/{name}` → `UserPublicDto` (name == username)
- `GET /api/users/{id}?byId` → `UserPublicDto` (enabled 사용자만)

관리자/내부용 기본 조회는 기존 관리 엔드포인트에서 제공한다.

- `GET /api/mgmt/users/basic/{id}` → `UserBasicDto`
- `GET /api/mgmt/users/basic` → `Page<UserBasicDto>`

## ERD (개념)
```text
TB_APPLICATION_USER (USER_ID) ──< TB_APPLICATION_USER_ROLES >── (ROLE_ID) TB_APPLICATION_ROLE
        │
        └──< TB_APPLICATION_USER_PROPERTY

TB_APPLICATION_GROUP (GROUP_ID) ──< TB_APPLICATION_GROUP_MEMBERS >── (USER_ID) TB_APPLICATION_USER
        │
        └──< TB_APPLICATION_GROUP_PROPERTY

TB_APPLICATION_GROUP (GROUP_ID) ──< TB_APPLICATION_GROUP_ROLES >── (ROLE_ID) TB_APPLICATION_ROLE

TB_APPLICATION_COMPANY (COMPANY_ID) ──< TB_APPLICATION_COMPANY_PROPERTY
```

## 개발자 가이드
### 엔티티/테이블 매핑 요약
- `ApplicationUser` → `TB_APPLICATION_USER`
- `ApplicationGroup` → `TB_APPLICATION_GROUP`
- `ApplicationRole` → `TB_APPLICATION_ROLE`
- `ApplicationCompany` → `TB_APPLICATION_COMPANY`
- `ApplicationUserRole` → `TB_APPLICATION_USER_ROLES`
- `ApplicationGroupMembership` → `TB_APPLICATION_GROUP_MEMBERS`
- `ApplicationGroupRole` → `TB_APPLICATION_GROUP_ROLES`
- `..._PROPERTY` 테이블은 각 엔티티의 `properties` 맵을 저장한다.

### 주요 흐름
- 사용자 생성/수정은 `ApplicationUserService`가 담당한다.
- 역할 부여는 `ApplicationUserRole` 또는 `ApplicationGroupRole`을 통해 조인 테이블에 반영된다.
- 그룹 가입은 `ApplicationGroupMembership`에 기록된다.

### 이벤트와 캐시
- 사용자 상태 변경 시 도메인 이벤트가 발행된다.
- 캐시 사용 시 `UserCacheEvictListener`가 갱신을 처리한다.

### 확장 포인트
- `UserMutator`로 사용자 생성/수정 로직을 확장할 수 있다.
- 리포지토리 스캔 패키지는 스타터 프로퍼티로 변경 가능하다.

### 주의사항
- `password`는 해시 값이며, 평문을 저장하지 않는다.
- 계정 잠금/실패 횟수는 `ApplicationUser`에 저장된다.

## 사용 방법
이 모듈은 보통 `studio-platform-starter-user`를 통해 사용한다.
직접 사용 시에는 필요한 구현(JPA/JDBC)과 의존성(Web/Security 등)을 함께 추가한다.

## 개발자 사용 가이드
### 1) 의존성 추가
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-user"))
}
```

### 2) 스키마 준비
PostgreSQL 기준 스키마는 아래에 포함되어 있다.
- `studio-platform-user-default/src/main/resources/schema/postgres/V0.1.0__create_user_tables.sql`

JPA를 사용한다면 마이그레이션 도구(Flyway 등)에 등록해 초기 테이블을 생성한다.

### 3) 기능 활성화 및 영속성 선택
```yaml
studio:
  features:
    user:
      enabled: true
      persistence: jpa   # 또는 jdbc
```

전역으로 영속성 타입을 지정하려면 다음을 사용한다.
```yaml
studio:
  persistence:
    type: jpa
```

### 4) 서비스 사용 예시
```java
@Service
public class UserFacade {
    private final ApplicationUserService userService;

    public UserFacade(ApplicationUserService userService) {
        this.userService = userService;
    }

    public ApplicationUser getUser(Long userId) {
        return userService.get(userId);
    }
}
```

### 5) REST 엔드포인트(선택)
스타터에서 web 토글을 켜면 기본 컨트롤러가 노출된다.
자세한 설정은 `starter/studio-platform-starter-user/README.md`를 참고한다.
