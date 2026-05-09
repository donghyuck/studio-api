# studio-platform-data

데이터 계층에서 공통으로 쓰는 JDBC 유틸리티, DB 기반 애플리케이션 프로퍼티 저장소, JPA 진단 유틸을 제공한다.

## 요약
- `PagingJdbcTemplate`와 `PaginationDialectResolver`로 DB별 페이징 SQL을 표준화한다.
- `JpaApplicationProperties`/`JdbcApplicationProperties`로 `TB_APPLICATION_PROPERTY` 기반 설정 저장소를 제공한다.
- `EntityLogger`로 JPA persistence unit에 등록된 엔티티와 테이블명을 진단한다.
- 기존 `studio.one.platform.text.*` 추출 API는 deprecated wrapper이며 새 코드는 `studio-platform-textract`를 사용한다.
- SQL mapper 표준은 `studio-platform-data-mybatis`와 `starter:studio-platform-starter-mybatis`의 MyBatis convention을 사용한다.

## 설계
- JDBC 페이징은 DB dialect별로 자동 적용한다.
- 애플리케이션 프로퍼티는 DB 테이블을 `Map`처럼 노출한다.
- 파일 텍스트 추출 구현은 `studio-platform-textract`에 둔다.
- 동적 SQL/XML mapper는 MyBatis로 통일한다.

## 사용법
- `PagingJdbcTemplate`로 DB별 페이징을 자동 적용한다.
- `JpaApplicationProperties`/`JdbcApplicationProperties`로 DB 프로퍼티 저장소를 등록한다.
- SQL mapper가 필요하면 `studio-platform-data-mybatis` 또는 `starter:studio-platform-starter-mybatis`를 추가한다.

## 확장 포인트
- 신규 DB dialect 추가: `PaginationDialect`
- 프로퍼티 저장소 구현 교체: `ApplicationProperties`
- 신규 파일 파서 추가: `studio-platform-textract`의 `FileParser`

## 설정
- DB 프로퍼티 테이블: `TB_APPLICATION_PROPERTY`
- OCR 사용 시 `studio-platform-textract-starter`에서 Tesseract 데이터 경로/언어 설정 필요
- MyBatis mapper location 기본값: `classpath*:mybatis/**/*.xml`

## 스키마
마이그레이션 파일 위치: `src/main/resources/schema/data/{postgres,mysql,mariadb}/V100__create_property_tables.sql`

Flyway 버전 범위는 `docs/flyway-versioning.md`의 data 범위(V100-V199)를 따른다.

## 빠른 사용법

```kotlin
dependencies {
    implementation(project(":studio-platform-data"))
}
```

### PagingJdbcTemplate

```java
PagingJdbcTemplate jdbc = new PagingJdbcTemplate(dataSource);
List<UserDto> page = jdbc.queryPage(
    "SELECT id, name, created_at FROM tb_user ORDER BY created_at DESC",
    0,
    20,
    (rs, i) -> new UserDto(rs.getLong("id"), rs.getString("name"))
);
```

### 프로퍼티 저장소

DB에 `TB_APPLICATION_PROPERTY` 테이블을 준비한 뒤 `JpaApplicationProperties` 또는
`JdbcApplicationProperties`를 빈으로 등록한다. `get`, `put`, `putAll`, `remove`로 런타임 설정을
읽고 갱신할 수 있다.

### MyBatis mapper

```kotlin
dependencies {
    implementation(project(":studio-platform-data-mybatis"))
    implementation(project(":starter:studio-platform-starter-mybatis"))
}
```

```yaml
studio:
  mybatis:
    mapper-locations:
      - classpath*:mybatis/**/*.xml
```

## ADR
- `docs/adr/0001-sql-mapper-xml.md`는 폐기된 XML mapper 설계 기록이다. 신규 구현 기준은 MyBatis convention이다.

## 대응 스타터
이 모듈은 `starter/studio-platform-starter`에 `api` 의존성으로 포함된다.

```kotlin
implementation(project(":starter:studio-platform-starter"))
```
