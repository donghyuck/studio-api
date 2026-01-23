# Starter 작성 가이드

이 문서는 현재 프로젝트에서 사용 중인 스타터 생성 방식(예: `studio-platform-starter-*`,
`studio-application-starter-*`)을 기준으로, 새로운 스타터를 빠르게 만드는 절차를 정리한다.

## 1) 모듈 생성
- 디렉터리 생성: `starter/<starter-name>/`
- Gradle 등록: `settings.gradle.kts`에 `include(":starter:<starter-name>")` 추가
- `build.gradle.kts` 기본 구조

```kotlin
plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for using <FeatureName>"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":starter:studio-platform-starter"))
    api(project(":<feature-module>"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    // 필요한 경우 data-jpa/security 등 추가
}
```

## 2) AutoConfiguration 작성
- 패키지 규칙: `studio.one.platform.autoconfigure.<feature>`
- 기본 스켈레톤

```java
@AutoConfiguration
@EnableConfigurationProperties({ FeatureProperties.class })
@ConditionalOnProperty(prefix = "studio.features.<feature>", name = "enabled", havingValue = "true")
public class FeatureAutoConfiguration {
    // @Bean 등록
}
```

## 3) feature 전용 프로퍼티
- 위치: `studio.one.platform.autoconfigure.<feature>`
- 형태: `FeatureToggle` 상속 + web 설정 필요 시 `WebEndpointProperties` 포함

```java
@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".<feature>")
public class FeatureProperties extends FeatureToggle {
    @Valid
    private WebEndpointProperties web = new WebEndpointProperties();
}
```

## 4) 상세 설정 프로퍼티
- 기능 세부 설정은 `studio.<feature>.*` 별도 프로퍼티 클래스로 정의
- 예: `ObjectTypeProperties` (`studio.objecttype.*`)

## 5) AutoConfiguration 등록
- 파일 생성:
  `starter/<starter-name>/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 예시:
```
studio.one.platform.autoconfigure.<feature>.<FeatureAutoConfiguration>
```

## 6) Persistence 분기(jpa|jdbc) 패턴
- 공통 조건: `ConditionalOnFeaturePersistence` 활용
- 전용 어노테이션 생성:

```java
@ConditionalOnFeaturePersistence(feature = "<feature>")
public @interface ConditionalOn<Feature>Persistence {
    @AliasFor(annotation = ConditionalOnFeaturePersistence.class, attribute = "value")
    PersistenceProperties.Type value();
}
```

## 7) 구현 모듈 연결
- 기본 구현은 `<feature-module>`에 두고, 스타터에서 프로퍼티 기반으로 선택
- YAML/DB 구현을 함께 지원하려면:
  - `studio.<feature>.mode` (yaml|db)
  - `studio.features.<feature>.persistence` (jpa|jdbc)

## 체크리스트
- [ ] `settings.gradle.kts` include 추가
- [ ] `build.gradle.kts` 작성
- [ ] AutoConfiguration 작성
- [ ] `@ConfigurationProperties` 추가
- [ ] `AutoConfiguration.imports` 등록
- [ ] 프로퍼티 문서/README 업데이트

