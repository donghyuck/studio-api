# studio-api
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


공통 모듈과 기초 공통모듈은 아래와 같이 구성되어 있다.
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
│   ├── jpa         # JPA 관련 설정/기반 클래스 (BaseEntity, Repository 등) (studio.platform, studio.user)
│   └── starter     # Spring Boot Starter 구성 (자동 설정, AutoConfig 등)  (studio.platform)
├── security (studio.user)
├── community 
│   ├──  studio.domain.community.streams
│   ├──  studio.domain.community.commet


kr.cufit.curation.api







```

```
└── studio-api-core 
│   └── src       
│   │   └── main
│   │   │   └── java
│   │   │   │   └── studio
│   │   │   │   │   └── platform 
│   │   │   │   │       └── config
│   │   │   │   │       └── components
│   │   │   │   │       └── domains
│   │   │   │   │       └── exception
│   │   │   │   │       └── services
│   │   │   │   │       └── util
│   │   │   └── resources
│   │   │       └── META-INF
│   │   │       │   └── logo 
│   │   │       └── studio 
│   │   │       │   └── platform
│   │   │       │       └── i18n
│   │   │       │           └── UserLogLocalizer.properties
│   │   │       └── schema 
│   │   │       │   └── mysql
│   │   │       │       └── *.sql
│   │   │       └── sql 
│   │   │           └── *-sqlset.xml   
│   │   └── test    
│   │       └── java
│   │       └── resources             
│   └── build.gradle.kts
└── .gitignore
└── build.gradle.kts
└── gradle.properties
└── gradlew
└── gradlew.bat
└── README.md
└── settings.gradle.kts

```


./gradlew dependencyCheckAnalyze