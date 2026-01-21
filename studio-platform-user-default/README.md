# studio-platform-user-default

`studio-platform-user`의 기본 구현(직접 사용자 엔터티/리포지토리/서비스/컨트롤러)을 제공하는 모듈이다.
사용자 시스템을 교체하려면 이 모듈 대신 커스텀 구현 모듈을 붙이면 된다.

## 포함 범위
- 엔터티: `ApplicationUser`
- 리포지토리: `ApplicationUserRepository` + JPA/JDBC 구현
- 서비스 구현: `ApplicationUserServiceImpl`, `ApplicationUserMutator`, `ApplicationIdentityService`
- 웹 계층: `UserController`, `PublicUserController`, `MeController` (각각 `*ControllerApi` 구현)
- DTO 매퍼: `ApplicationUserMapper`

## 컨트롤러 제약
기본 컨트롤러는 `ApplicationUserMapper`와 기본 엔터티 구조를 전제로 한다.
따라서 **커스텀 사용자 구현을 사용하는 경우 기본 컨트롤러를 비활성화**하고
커스텀 컨트롤러를 제공해야 한다. 기본 컨트롤러 자동 등록은
`UserControllerApi`/`PublicUserControllerApi`/`MeControllerApi` 빈 유무로
판단하므로, 커스텀 컨트롤러는 해당 인터페이스를 구현하는 것을 권장한다.

또한 기본 `ApplicationIdentityService` 구현도 default 모듈에 포함되므로,
커스텀 사용자 구현을 사용할 때는 IdentityService도 교체 구현을 제공해야 한다.

## 스키마
PostgreSQL 기준 스키마는 아래에 포함되어 있다.
- `studio-platform-user-default/src/main/resources/schema/postgres/V0.1.0__create_user_tables.sql`

## 사용 방법
보통 `starter:studio-platform-starter-user`를 통해 자동 구성된다.
직접 사용할 경우 아래 의존성을 추가한다.

```kotlin
dependencies {
    implementation(project(":studio-platform-user-default"))
}
```

## 교체 포인트
- 사용자 엔터티/리포지토리/서비스를 커스텀으로 교체하려면
  이 모듈 대신 별도 모듈을 제공하고 `studio-platform-user` 계약만 유지한다.
