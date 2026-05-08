# Studio Platform Starter MyBatis

Spring Boot 3.5 / Java 17 기준 MyBatis starter와 Studio mapper convention을 제공한다.

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
  # 표준 MyBatis Boot 설정을 직접 지정하면 studio.mybatis 기본값보다 우선한다.
  mapper-locations:
    - classpath*:mybatis/**/*.xml
```

`persistence=jdbc` alias 처리와 기존 SqlQuery 구현 전환은 후속 phase에서 진행한다.

## Legacy SqlQuery Coexistence

`starter:studio-platform-starter`는 아직 기존 SqlQuery 자동 구성을 포함한다. 전환 기간에 base starter와
MyBatis starter를 함께 사용하는 애플리케이션은 두 mapper 체계가 동시에 기동할 수 있다.

MyBatis만 사용하려면 후속 phase에서 legacy 경로가 제거되기 전까지 다음 설정으로 SqlQuery를 끈다.

```yaml
studio:
  persistence:
    jdbc:
      sql-query:
        enabled: false
```
