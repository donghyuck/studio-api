# studio-api (one)
[![release](https://img.shields.io/badge/release-0.4-blue.svg)](https://github.com/metasfresh/metasfresh/releases/tag/5.175)
[![license](https://img.shields.io/badge/license-APACHE-blue.svg)](https://github.com/metasfresh/metasfresh/blob/master/LICENSE.md)

A backend spellbook for every creator.

A modular Spring Boot API platform that provides authentication, file management, user control, and logging — all in one base system for building web services at scale.

Think of it as your magic studio engine.

|Name|Version|Initial Release|EOS (End of Service)|
|------|---|---|---|
|Spring Boot|2.7.18| | |
|Spring Framework|5.3.31|2020-10-27|2024-12-31|
|Spring Security|5.7.11|2022-05-16|2023-05-16|
|Spring Cloud | 2021.0.8 | | |

Studio Api 는 component, starter 모듈들로 구성된다.

코어 모듈은 아래와 같이 구성되어 있다. 
```
├── starter 
│   ├── studio-platform-starter    
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── java
│   │   │   │   │   └── studio.echo.platform.autoconfigure
│   │   │   │   │       ├── i18n
│   │   │   │   │       ├── jpa
│   │   │   │   │       │   └── auditor
│   │   │   │   └── resources
│   │   │   │       └── META-INF
│   │   │   │           ├── i18n/platform
│   │   │   │           └── spring
│   │   │   │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   │   └── test
│   │   └── build.gradle.kts
│   ├── studio-platform-starter-jasypt 
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── java
│   │   │   │   │   └── studio.echo.platform.ajasypt.autoconfigure
│   │   │   │   └── resources
│   │   │   │       └── META-INF
│   │   │   │           ├── i18n/jasypt
│   │   │   │           └── spring
│   │   │   │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   │   └── test 
│   │   └── build.gradle.kts
│   ├── studio-platform-starter-security 
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── java
│   │   │   │   │   └── studio.echo.platform.security.autoconfigure
│   │   │   │   └── resources
│   │   │   │       └── META-INF
│   │   │   │           ├── i18n/jasypt
│   │   │   │           └── spring
│   │   │   │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   │   └── test 
│   │   └── build.gradle.kts
│   └── studio-platform-starter-user
│       ├── src
│       │   └── main
│       │       ├── java
│       │       │   └── studio.echo.platform.user.starter
│       │       │       └── autoconfig
│       │       │           └── condition
│       │       └── resources
│       └── build.gradle.kts
├── studio-platform
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.platform
│   │       └── resources
│   │           ├── i18n/platform
│   │           ├── META-INF
│   │           └── banner.txt
├── studio-platform-autoconfigure
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.jplatform.autoconfigure
│   │       └── resources 
│   │           └── META-INF 
│   └── build.gradle.kts   
│
├── studio-platform-jpa
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.jplatform
│   │       └── resources 
│   │           └── schema
│   │               ├── mysql
│   │               └── postgres
│   └── build.gradle.kts 
├── studio-platform-security
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.base.security
│   │       └── resources 
│   │           ├── META-INF 
│   │           └── i18n/security
│   └── build.gradle.kts 
├── studio-platform-user
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.base.user
│   │       └── resources 
│   │           ├── META-INF 
│   │           └── i18n/security
│   │           └── schema
│   └── build.gradle.kts 
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
└── settings.gradle.kts

```




## Create dababase (postgres)
```
-- 로그인 가능한 사용자 생성 + 비밀번호 지정
CREATE USER studioapi WITH PASSWORD 'studioapi';

-- 데이터베이스 생성 권한 부여
ALTER USER studioapi CREATEDB;

CREATE SCHEMA studioapi AUTHORIZATION studioapi;

GRANT ALL PRIVILEGES ON SCHEMA studioapi TO studioapi;

```



