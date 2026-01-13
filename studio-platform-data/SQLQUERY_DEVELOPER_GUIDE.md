# SqlQuery 개발자 가이드

이 문서는 `studio-platform-data`의 SqlQuery 기반 SQL 매퍼 사용법을 설명한다.

## 개요
`SqlQuery`는 `classpath:/sql` 아래의 XML에 정의된 SQL 스테이트먼트를 이름으로 호출하는 핵심 인터페이스다. 다음을 지원한다.

- `queryForList`, `queryForObject`, `executeUpdate`, `call`
- `setStartIndex`/`setMaxResults` 기반 페이징
- MyBatis 스타일 동적 SQL 노드
- `@SqlMapper`/`@SqlStatement`/`@SqlBoundStatement`/`@SqlMappedStatement` 기반 정적 매핑

`SqlQueryFactoryImpl`과 `DirectoryScanner`가 `sql` 디렉터리를 주기적으로 스캔해
신규/변경 스테이트먼트를 반영한다.

## 디렉터리 구조
SQL XML 파일은 `classpath:/sql` 아래에 둔다.

```
src/main/resources/
  sql/
    user-sqlset.xml
    order-sqlset.xml
```

## SQL XML 포맷
각 XML 파일은 이름이 있는 스테이트먼트 집합을 가진다. `SqlQuery` 호출 시 이 이름을 사용한다.
동적 노드는 MyBatis 스타일 문법을 지원한다.

예시:

```xml
<sqlset namespace="user">
  <select id="selectById">
    SELECT id, name, email
    FROM tb_user
    WHERE id = #{id}
  </select>

  <select id="selectPage">
    SELECT id, name, email
    FROM tb_user
    ORDER BY created_at DESC
  </select>
</sqlset>
```

스테이트먼트 키 규칙:
- id에 점(.)이 포함되어 있으면 네임스페이스를 붙이지 않고 그대로 사용한다.
- id에 점이 없으면 `namespace.id`로 보정된다.
예: id가 `select.byId`이면 키는 `select.byId`, id가 `select`이면 키는 `user.select`.

주의: id에 점을 포함해 저장했는데 호출 시 `namespace.id` 형태로 찾으면 내부적으로 매칭되지 않아 “쿼리를 찾지 못함” 오류가 발생한다.

## SqlQuery 직접 사용
팩토리를 생성한 뒤 `SqlQuery`를 사용한다.

```java
SqlQueryFactory factory = new SqlQueryFactoryImpl(dataSource, repository);
SqlQuery sqlQuery = factory.getSqlQuery();

Map<String, Object> row = sqlQuery
    .queryForObject("user.selectById", Map.of("id", 10));

List<Map<String, Object>> rows = sqlQuery
    .setStartIndex(0)
    .setMaxResults(20)
    .queryForList("user.selectPage");
```

## 매퍼 인터페이스 사용
인터페이스에 어노테이션을 붙여 스테이트먼트를 바인딩한다.

```java
@SqlMapper("user-sqlset.xml")
public interface UserSqlMapper {
    @SqlStatement("user.selectById")
    Map<String, Object> selectById(long id);

    @SqlStatement("user.selectPage")
    List<UserDto> selectPage(int startIndex, int maxResults);
}
```

팩토리 바인딩:

```java
SqlQueryFactory factory = new SqlQueryFactoryImpl(dataSource, repository);
UserSqlMapper mapper = factory.getMapper(UserSqlMapper.class);
List<UserDto> page = mapper.selectPage(0, 20);
```

## BoundSql 주입
필드 또는 단일 파라미터 세터에 `@SqlBoundStatement`를 붙이면 `BoundSql`이 주입된다.

```java
public class UserRepository {
    @SqlBoundStatement("user.selectPage")
    private BoundSql selectPage;

    public BoundSql getSelectPage() {
        return selectPage;
    }
}
```

## MappedStatement 주입
동적 쿼리 생성을 위해 필드 또는 단일 파라미터 세터에 `@SqlMappedStatement`를 붙이면 `MappedStatement`가 주입된다.

```java
public class UserRepository {
    @SqlMappedStatement("user.selectPage")
    private MappedStatement selectPage;

    public String buildPageSql(Map<String, Object> params, Map<String, Object> additional) {
        return selectPage.getBoundSql(params, additional).getSql();
    }
}
```

## MappedStatement SQL 캐시 (옵션)
동일한 조건 "형태"를 반복 생성하는 경우 `cacheKey`를 지정해 SQL 문자열만 캐시할 수 있다.
캐시는 SQL 텍스트만 재사용하며 파라미터 바인딩은 매번 수행된다. (기본 LRU 256)

```java
public class UserRepository {
    @SqlMappedStatement("user.selectPage")
    private MappedStatement selectPage;

    public String buildPageSql(UserFilter filter, Map<String, Object> additional) {
        String cacheKey = "user.selectPage:"
            + filter.isActive() + ":"
            + filter.hasTeam() + ":"
            + filter.getSortKey();
        return selectPage.getBoundSqlCached(filter, additional, cacheKey).getSql();
    }
}
```

## 캐시 키 유틸
캐시 키는 `SqlCacheKey`로 표준화해서 생성한다.

```java
import studio.one.platform.data.sqlquery.SqlCacheKey;

String cacheKey = SqlCacheKey.of("user.selectPage",
    filter.isActive(),
    filter.hasTeam(),
    filter.getSortKey()
);
```

## 파라미터 객체 우선
동적 SQL 파라미터는 Map보다 파라미터 객체(DTO)를 우선 사용한다.
Map은 추가 파라미터나 확장 용도로만 제한한다.

```java
public String buildPageSql(UserFilter filter, Map<String, Object> additional) {
    return selectPage.getBoundSql(filter, additional).getSql();
}
```

## 파라미터 빌더 유틸
Map이 필요한 경우 `SqlParams`를 사용해 키-값 생성 실수를 줄인다.

```java
import studio.one.platform.data.sqlquery.SqlParams;

Map<String, Object> params = SqlParams.of(
    "filter", filter,
    "startDate", filter.getStartDate(),
    "endDate", filter.getEndDate(),
    "sort", filter.getSort()
);
Map<String, Object> additional = SqlParams.additional(
    "TENANT_ID", ctx.get("TENANT_ID"),
    "REQUIRED_ROLE", ctx.get("REQUIRED_ROLE")
);
```

## 페이징 참고
`SqlQuery`의 페이징은 SQL 매퍼 레이어에서 적용된다. 목록 조회 전에
`startIndex`와 `maxResults`를 모두 설정해야 한다.

임의 SQL에 JDBC 수준의 페이징이 필요하다면 `PagingJdbcTemplate`을 사용한다.

## 동적 SQL 팁
- 선택 조건은 파라미터 객체(DTO)를 우선으로 전달한다.
- 파라미터 이름은 명확하게 지정한다.
- 동적 조각은 작게 유지해 디버깅을 쉽게 한다.

## 부팅 시 존재 검증 (Fail-fast)
`@SqlStatement`, `@SqlBoundStatement`, `@SqlMappedStatement`는 빈 초기화 시점에
스테이트먼트 존재 여부를 검증한다. 누락된 id가 있으면 애플리케이션이 즉시 실패한다.
기본값은 `true`이며 운영 환경에서도 on 상태를 권장한다.

```yaml
studio:
  persistence:
    jdbc:
      sql:
        fail-fast: true
```

## 관찰성 (로그)
`MappedStatement` 캐시 hit/miss와 SQL 생성 시간은 debug 로그로 확인할 수 있다.
캐시 miss 로그는 `buildMicros`로 생성 시간을 제공한다.

## LLM 개발 규칙 요약
다음 규칙을 지키면 LLM이 이 가이드를 바탕으로 SQL 매퍼 코드를 안정적으로 생성할 수 있다.

1) 스테이트먼트 키 규칙
- id에 점(.)이 있으면 네임스페이스를 붙이지 않는다.
- id에 점이 없으면 `namespace.id`로 보정된다.
- 호출 시에는 실제 키와 정확히 일치해야 한다.

2) 어노테이션 사용 규칙
- `@SqlStatement`: SQL 문자열 또는 `BoundSql` 주입용 (필드/세터/매퍼 메서드).
- `@SqlBoundStatement`: `BoundSql` 주입 전용.
- `@SqlMappedStatement`: 동적 쿼리 생성을 위한 `MappedStatement` 주입 전용.

3) 동적 쿼리 생성 규칙
- `MappedStatement.getBoundSql(params, additional)`로 동적 SQL을 생성한다.
- 템플릿 변수는 `parameters`, `additional_parameters` 키로 바인딩된다.
  - `params`가 Map이면 키가 그대로 노출된다.
  - `additional`이 Map이 아니면 `additional_parameter`로 노출된다.
  - `additional`이 null이면 추가 파라미터는 바인딩하지 않는다.
  - 파라미터는 DTO 우선, Map은 추가용으로 제한한다.

4) 예시
```java
@SqlMappedStatement("user.selectPage")
private MappedStatement selectPage;

public String buildSql(Map<String, Object> params, Map<String, Object> additional) {
    return selectPage.getBoundSql(params, additional).getSql();
}
```

5) 복잡한 예시
```java
@SqlMappedStatement("report.userActivity")
private MappedStatement userActivity;

public String buildReportSql(ReportFilter filter, Map<String, Object> ctx) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("filter", filter);
    params.put("startDate", filter.getStartDate());
    params.put("endDate", filter.getEndDate());
    params.put("sort", filter.getSort());
    params.put("columns", List.of("USER_ID", "ACTION", "CREATED_AT"));

    Map<String, Object> additional = new HashMap<>();
    additional.put("REQUIRED_ROLE", ctx.get("REQUIRED_ROLE"));
    additional.put("TENANT_ID", ctx.get("TENANT_ID"));

    return userActivity.getBoundSql(params, additional).getSql();
}
```

## 트러블슈팅
스테이트먼트를 찾지 못하는 경우:

- XML 파일이 `classpath:/sql` 아래에 있는지 확인한다.
- 스테이트먼트 키가 `namespace.id`와 일치하는지 확인한다.
- `DirectoryScanner`가 올바른 디렉터리를 스캔하는지 확인한다.

변경 내용이 반영되지 않는 경우:

- 스캐너 주기가 지났는지 기다린다.
- 동일 런타임 클래스패스에서 파일이 갱신되고 있는지 확인한다.

## 관련 컴포넌트
- `SqlQueryFactoryImpl`: `SqlQuery` 인스턴스와 매퍼 프록시 생성
- `DirectoryScanner`: `sql` 디렉터리 스캔 및 리로드
- `PagingJdbcTemplate`: JDBC 페이징 유틸
