# ADR 0001: SQL Statement Registry via XML SqlSet

## 상태
승인

## 맥락
다수의 JDBC 기반 모듈에서 SQL 텍스트와 바인딩/페이징 로직이 중복되고,
SQL 변경 시 코드 재배포 부담이 컸다.

## 결정
SQL을 `classpath:/sql` 아래 XML(SqlSet)로 정의하고 `SqlQueryFactoryImpl`이 런타임에 로드한다.
동적 SQL은 MyBatis 스타일 노드를 지원하며, `@SqlStatement` 등으로 정적 바인딩도 제공한다.

## 결과
- SQL 변경을 코드 수정 없이 반영 가능(리소스 변경 배포 기준).
- 페이징/바인딩 로직의 일관성을 확보한다.
- JDBC 기반 모듈의 구현 비용을 크게 줄인다.

## 참고
- `JDBC_DEVELOPMENT_GUIDE.md`
