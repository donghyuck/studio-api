# Studio Platform Data MyBatis

MyBatis 기반 SQL mapper 공통 convention 모듈이다. Spring Boot 3.5 / Java 17 환경에서는
`starter:studio-platform-starter-mybatis`를 통해 mapper를 구성한다.

## Convention

- Java mapper: `**/mybatis/*Mapper.java`
- XML mapper: `src/main/resources/mybatis/**/*.xml`
- XML namespace: Java mapper FQCN
- 기본 mapper location: `classpath*:mybatis/**/*.xml`

## Properties

```yaml
studio:
  mybatis:
    mapper-locations:
      - classpath*:mybatis/**/*.xml
    map-underscore-to-camel-case: true
    database-id-aliases:
      PostgreSQL: postgresql
      MySQL: mysql
      MariaDB: mariadb
      H2: h2
      Oracle: oracle
      Microsoft SQL Server: sqlserver
```

`studio.mybatis.*`는 Studio convention 기본값이다. MyBatis starter의 표준 `mybatis.*` 설정을 직접
지정하면 해당 설정을 우선한다.

`database-id-aliases`는 기본 map에 설정값이 merge된다. 같은 product name을 지정하면 alias가
덮어써지고, 새 product name은 추가된다.
