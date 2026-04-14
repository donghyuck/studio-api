# studio-platform-starter

플랫폼 전체의 기반이 되는 스타터이다. `studio-platform`, `studio-platform-data`,
`studio-platform-autoconfigure` 세 핵심 모듈을 하나의 의존성으로 묶어 제공하고,
Spring Web, JPA, Security, Validation 스타터와 함께 사용할 수 있도록 구성된다.
다른 모든 플랫폼 스타터(`studio-platform-starter-security`, `studio-platform-starter-user` 등)는
이 스타터를 기반으로 동작한다.

## 1) 의존성 추가
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter"))
    // REST API 사용 시
    implementation("org.springframework.boot:spring-boot-starter-web")
    // JPA 사용 시
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Spring Security 연동 시
    implementation("org.springframework.boot:spring-boot-starter-security")
}
```

## 2) 기능 활성화
별도의 feature 플래그 없이 의존성 추가만으로 플랫폼 공통 자동 구성이 활성화된다.
전역 영속성 타입 및 개별 기능은 아래 설정으로 제어한다.

```yaml
studio:
  persistence:
    type: jpa   # jpa | jdbc  (기본값: jpa)
```

## 3) 설정
이 스타터 자체의 설정 속성은 없다. 하위 스타터에서 공통으로 사용하는
`studio.persistence.*` 속성을 전역 기본값으로 사용한다.

```yaml
studio:
  persistence:
    type: jpa   # 전역 영속성 타입. 하위 기능별 설정이 없으면 이 값을 사용
```

## 4) 자동 구성되는 주요 빈
`studio-platform-autoconfigure` 모듈에 포함된 자동 구성 클래스에 의해 등록된다.

- `I18n` — 플랫폼 공통 국제화(다국어) 지원 서비스
- `PersistenceProperties` — 전역 영속성 타입 설정 바인딩
- JDBC 공통 인프라 (`JdbcTemplate`, `NamedParameterJdbcTemplate`) — Spring JDBC 스타터가 있을 때
- `studio-platform-data` 제공 데이터 접근 추상화 계층

## 5) 참고 사항
- `studio-platform-starter`는 `api` 의존성으로 세 핵심 모듈을 전이(transitive) 노출한다.
  의존하는 모듈에서 별도로 core 모듈을 다시 선언할 필요가 없다.
- Spring Web, JPA, Security는 `compileOnly`로 선언되어 있으므로 실제 애플리케이션에서
  필요한 스타터를 직접 추가해야 한다.
- 이 스타터에는 실행 가능한 `BootJar`가 포함되지 않는다(`bootJar` 비활성화).
- 다른 플랫폼 스타터와 함께 사용할 때는 이 스타터를 중복 선언하지 않아도 된다.
  각 하위 스타터가 내부적으로 이 스타터에 의존하고 있다.
