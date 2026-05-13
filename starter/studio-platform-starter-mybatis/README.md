# Studio Platform Starter MyBatis

Spring Boot 2.7 / Java 11 기준 MyBatis starter와 Studio mapper convention을 제공한다.

## Dependency

```kotlin
dependencies {
    implementation("studio.one.starter:studio-platform-starter-mybatis")
}
```

## Mapper Convention

- Java mapper는 `@Mapper` 또는 `@MapperScan`으로 등록한다.
- XML mapper는 `src/main/resources/mybatis/**/*.xml`에 둔다.
- XML namespace는 Java mapper FQCN과 일치시킨다.
- 기본 mapper location은 `classpath*:mybatis/**/*.xml`이다.

## Configuration

```yaml
studio:
  mybatis:
    mapper-locations:
      - classpath*:mybatis/**/*.xml
    map-underscore-to-camel-case: true

mybatis:
  # 표준 MyBatis Boot 설정을 직접 지정해도 studio.mybatis mapper-locations와 병합된다.
  mapper-locations:
    - classpath*:custom-mybatis/**/*.xml
```

`persistence=jdbc` alias는 legacy 직접 JDBC 구현 호환용으로만 유지한다. SQL mapper는 MyBatis
convention을 사용하고, XML mapper는 `classpath*:mybatis/**/*.xml`에서 로드한다.
`mybatis.mapper-locations`를 직접 지정해도 Studio starter가 제공하는 mapper XML이 누락되지 않도록
`studio.mybatis.mapper-locations`와 병합된다. type alias/type handler package는 표준
`mybatis.*` 설정이 있으면 해당 설정을 우선한다.
