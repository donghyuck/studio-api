# studio-platform-data

데이터/콘텐츠 계층에서 바로 쓸 수 있는 도구 모음이다. JDBC 페이징, SQL-XML 매퍼, 파일 텍스트 추출기, DB 기반 애플리케이션 프로퍼티 저장소, JPA 진단 유틸을 한 모듈에 묶어 제공한다.

## 주요 기능
- SQL-XML 매퍼: `@SqlMapper`/`@SqlStatement`/`@SqlBoundStatement` 인터페이스와 `SqlQueryFactory`가 XML `sql` 디렉터리의 스테이트먼트를 읽어와 동적 바인딩·페이징·프로시저 호출을 처리한다.
- JDBC 페이징: `PagingJdbcTemplate`가 DB 종류에 따라 `LIMIT/OFFSET`/`ROWNUM`/`TOP` 등을 자동 적용하는 `PaginationDialectResolver`를 내장한다.
- 파일 텍스트 추출: `FileContentExtractionService`와 `FileParserFactory`가 PDFBox, POI, Jsoup, Tesseract 기반 파서를 통해 PDF/DOCX/PPTX/HTML/TXT/이미지에서 본문을 뽑아낸다.
- 애플리케이션 프로퍼티 저장소: `JpaApplicationProperties`/`JdbcApplicationProperties`가 DB 테이블(`TB_APPLICATION_PROPERTY`)을 Map 형태로 노출하고 변경 이벤트를 퍼블리시한다.
- JPA 진단: `EntityLogger`가 PU에 등록된 엔티티와 테이블명을 로깅해 스캔 설정 문제를 빠르게 확인할 수 있게 한다.

## 구성 요소 상세
- **SQL 매퍼 레이어**  
  `SqlQuery` 인터페이스는 `queryForList/queryForObject/executeUpdate/call` 등을 제공하며, `setStartIndex/setMaxResults`로 페이징 값을 전달한다. `SqlQueryFactoryImpl`과 `DirectoryScanner`가 `sql` 디렉터리의 XML을 주기적으로 스캔하여 새/변경 스테이트먼트를 반영한다. XML은 MyBatis 스타일의 동적 노드를 지원하며, `@SqlMapper`/`@SqlStatement`/`@SqlBoundStatement`로 정적 매핑도 가능하다.
- **DB 페이징 유틸**  
  `PagingJdbcTemplate`는 데이터소스에서 DB 타입을 추론해 적합한 `PaginationDialect`(Postgres, MySQL, Oracle, SQL Server 등)를 선택하고, 동일한 SQL에 페이징을 적용해 실행한다.
- **텍스트 추출 파서**  
  파서는 `supports(contentType, filename)`로 입력을 판별하고 `parse`로 텍스트를 반환한다. PDF는 PDFBox, DOCX/PPTX는 Apache POI, HTML은 Jsoup, 이미지(OCR)는 Tesseract(언어/데이터 경로 필요), 일반 텍스트는 기본 파서를 사용한다. `FileContentExtractionService`는 파일/스트림을 받아 적절한 파서를 선택해준다.
- **프로퍼티 저장소**  
  `ApplicationProperties` 구현체로, DB에 저장된 설정 값을 `Map`처럼 읽고 수정한다. 초기화 시 전체 프로퍼티를 로딩하고, 변경 시 `PropertyChangeEvent`를 발행한다. JPA 버전은 `EntityManager`, JDBC 버전은 `JdbcTemplate`을 사용한다. DDL은 `src/main/resources/schema` 하위에 포함되어 있다.
- **JPA 로깅**  
  `EntityLogger.log`에 `EntityManagerFactory`와 로거를 넘기면 PU 이름과 엔티티 목록(+테이블명)을 정렬해 출력하고, 패키지에 엔티티가 없으면 경고를 남긴다.

## 빠른 사용법
- **의존성 추가**  
  ```kotlin
  dependencies {
      implementation(project(":studio-platform-data"))
  }
  ```
- **SQL 매퍼 설정**  
  `classpath:/sql` 아래에 XML SQL-Set을 두고 데이터소스와 함께 `SqlQueryFactoryImpl`을 생성한다. `SqlQuery` 인스턴스에서 `setStartIndex/setMaxResults`로 페이징 후 `queryForList` 등을 호출한다.  
  - 어노테이션 매퍼 예시:
    ```java
    @SqlMapper(value = "user-sqlset.xml") // classpath:/sql/user-sqlset.xml
    public interface UserSqlMapper {
        @SqlStatement("user.select.byId")
        Map<String, Object> selectById(long userId);

        @SqlStatement("user.select.page")
        List<UserDto> selectPage(int startIndex, int maxResults);
    }
    ```
  - `BoundSql` 주입 예시:
    ```java
    public class UserRepository {
        @SqlBoundStatement("user.select.page")
        private BoundSql selectPage;

        public BoundSql selectPage() {
            return selectPage;
        }
    }
    ```
    ```java
    // 팩토리 생성 후 매퍼 바인딩
    SqlQueryFactory factory = new SqlQueryFactoryImpl(dataSource, repository); // repository는 sql 디렉터리 접근자
    UserSqlMapper mapper = factory.getMapper(UserSqlMapper.class);

    // 페이지 조회
    List<UserDto> users = mapper.selectPage(0, 20);
    ```
  - `SqlQuery` 직접 사용 예시:
    ```java
    SqlQuery sqlQuery = factory.getSqlQuery();
    List<Map<String, Object>> rows = sqlQuery
        .setStartIndex(0)
        .setMaxResults(10)
        .queryForList("user.select.page");
    ```
- **PagingJdbcTemplate 사용**  
  기본 `JdbcTemplate` 대체로 등록하면 동일한 SQL을 페이징해서 호출할 수 있다. DB Dialect는 데이터소스 메타데이터로 자동 추론된다.
  ```java
  PagingJdbcTemplate jdbc = new PagingJdbcTemplate(dataSource);
  List<UserDto> page = jdbc.queryPage(
      "SELECT id, name, created_at FROM tb_user ORDER BY created_at DESC",
      0, 20,
      (rs, i) -> new UserDto(rs.getLong("id"), rs.getString("name"))
  );
  ```
- **파일 텍스트 추출**  
  필요한 파서를 리스트로 주입해 `FileParserFactory`를 만들고 `FileContentExtractionService`를 통해 `extractText(contentType, filename, fileOrStream)`을 호출한다. OCR을 쓰려면 `ImageFileParser` 생성 시 Tesseract 데이터 경로와 언어 코드를 전달한다.
- **프로퍼티 저장소**  
  DB에 `TB_APPLICATION_PROPERTY` 테이블을 준비한 뒤 `JpaApplicationProperties` 또는 `JdbcApplicationProperties`를 빈으로 등록한다. `get/put/putAll/remove`를 통해 런타임 설정을 읽고 갱신할 수 있다.
