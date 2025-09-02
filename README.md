# studio-api (echoes)
[![release](https://img.shields.io/badge/release-0.1-blue.svg)](https://github.com/metasfresh/metasfresh/releases/tag/5.175)
[![license](https://img.shields.io/badge/license-APACHE-blue.svg)](https://github.com/metasfresh/metasfresh/blob/master/LICENSE.md)

A backend spellbook for every creator.

A modular Spring Boot API platform that provides authentication, file management, user control, and logging — all in one base system for building web services at scale.

Think of it as your magic studio engine.

|Name|Version|Initial Release|EOS (End of Service)|
|------|---|---|---|
|Spring Framework|5.3.31|2020-10-27|2024-12-31|
|Spring Security|5.7.11|2022-05-16|2023-05-16|
|Spring Cloud | 2021.0.8 | | |

Studio Api 는 core, base 모듈들로 구성된다.

코어 모듈은 아래와 같이 구성되어 있다. 
```
├── core 
│   ├── platform    # 공통 플랫폼 기능 (studio.platform)
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── java
│   │   │   │   │   └── studio.echo.platform
│   │   │   │   │       ├── component
│   │   │   │   │       ├── constant
│   │   │   │   │       ├── exception
│   │   │   │   │       ├── service
│   │   │   │   │       └── util
│   │   │   │   └── resources
│   │   │   │       └── i18n
│   │   │   └── test
│   │   └── build.gradle.kts
│   ├── platform-jpa
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── java
│   │   │   │   │   └── studio.echo.platform.jpa
│   │   │   │   │       ├── entity
│   │   │   │   │       ├── repository
│   │   │   │   │       ├── mapper 
│   │   │   │   │       └── component
│   │   │   │   └── resources
│   │   │   │       └── i18n
│   │   │   └── test 
│   │   └── build.gradle.kts
│   └── platform-starter     # Spring Boot Starter 구성 (자동 설정, AutoConfig 등)
│       ├── src
│       │   └── main
│       │       ├── java
│       │       │   └── studio.echo.platform.starter
│       │       │       └── autoconfig
│       │       │           └── condition
│       │       └── resources
│       └── build.gradle.kts
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



