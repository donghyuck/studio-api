# SqlQuery 개발자 가이드

이 문서는 `studio-platform-data`의 SqlQuery 기반 SQL 매퍼 사용법을 설명한다.

## 개요
`SqlQuery`는 `classpath:/sql` 아래의 XML에 정의된 SQL 스테이트먼트를 이름으로 호출하는 핵심 인터페이스다. 다음을 지원한다.

- `queryForList`, `queryForObject`, `executeUpdate`, `call`
- `setStartIndex`/`setMaxResults` 기반 페이징
- MyBatis 스타일 동적 SQL 노드
- `@SqlMapper`/`@SqlStatement` 기반 정적 매핑

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
  <select id="select.byId">
    SELECT id, name, email
    FROM tb_user
    WHERE id = #{id}
  </select>

  <select id="select.page">
    SELECT id, name, email
    FROM tb_user
    ORDER BY created_at DESC
  </select>
</sqlset>
```

전체 스테이트먼트 키는 `user.select.byId`, `user.select.page` 형태가 된다.

## SqlQuery 직접 사용
팩토리를 생성한 뒤 `SqlQuery`를 사용한다.

```java
SqlQueryFactory factory = new SqlQueryFactoryImpl(dataSource, repository);
SqlQuery sqlQuery = factory.getSqlQuery();

Map<String, Object> row = sqlQuery
    .queryForObject("user.select.byId", Map.of("id", 10));

List<Map<String, Object>> rows = sqlQuery
    .setStartIndex(0)
    .setMaxResults(20)
    .queryForList("user.select.page");
```

## 매퍼 인터페이스 사용
인터페이스에 어노테이션을 붙여 스테이트먼트를 바인딩한다.

```java
@SqlMapper("user-sqlset.xml")
public interface UserSqlMapper {
    @SqlStatement("user.select.byId")
    Map<String, Object> selectById(long id);

    @SqlStatement("user.select.page")
    List<UserDto> selectPage(int startIndex, int maxResults);
}
```

팩토리 바인딩:

```java
SqlQueryFactory factory = new SqlQueryFactoryImpl(dataSource, repository);
UserSqlMapper mapper = factory.getMapper(UserSqlMapper.class);
List<UserDto> page = mapper.selectPage(0, 20);
```

## 페이징 참고
`SqlQuery`의 페이징은 SQL 매퍼 레이어에서 적용된다. 목록 조회 전에
`startIndex`와 `maxResults`를 모두 설정해야 한다.

임의 SQL에 JDBC 수준의 페이징이 필요하다면 `PagingJdbcTemplate`을 사용한다.

## 동적 SQL 팁
- 선택 조건은 파라미터 맵으로 전달한다.
- 파라미터 이름은 명확하게 지정한다.
- 동적 조각은 작게 유지해 디버깅을 쉽게 한다.

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
