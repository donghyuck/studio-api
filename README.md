# Studio One Platform

[![release](https://img.shields.io/badge/release-2.0.0-blue.svg)](https://github.com/metasfresh/metasfresh/releases/tag/5.175)
[![license](https://img.shields.io/badge/license-APACHE-blue.svg)](https://github.com/metasfresh/metasfresh/blob/master/LICENSE.md)

모듈화된 Spring Boot 기반 백엔드 플랫폼. 인증/인가, 사용자/그룹 관리, 파일·첨부 관리, 템플릿, 메일, 실시간 메시징, AI 임베딩/RAG 파이프라인을 공통 컴포넌트와 스타터로 제공한다.

## 빠른 시작
1. JDK 17과 Gradle 실행 환경을 준비한다.
2. 필요한 secret을 셸 환경변수 또는 로컬 전용 `~/.gradle/gradle.properties`에 넣는다.
3. 루트에서 `./gradlew clean build`를 실행한다.
4. 애플리케이션에서는 필요한 starter만 선택해 의존성에 추가한다.

최소 확인용 명령:

```bash
./gradlew clean build
```

필수 secret 예시는 [.env.example](.env.example)에서 확인한다.

## 기술 기준
- Java toolchain / source compatibility: `17`
- Spring Boot: `3.5.9`
- Spring Dependency Management Plugin: `1.1.7`
- Build: `Gradle Wrapper`

## 레포지토리 구성
```
starter/                         # Spring Boot 스타터 모음 (자동 구성)
studio-application-modules/      # 애플리케이션 기능 모듈 (attachment, avatar, embedding pipeline, template, mail)
studio-platform/                 # 코어 플랫폼 라이브러리
studio-platform-objecttype/      # objectType 레지스트리/정책/런타임 검증 구현
studio-platform-ai/              # AI/RAG 공통 계약과 포트
studio-platform-autoconfigure/   # 공통 자동 구성
studio-platform-data/            # 데이터 액세스 공통
studio-platform-identity/        # 인증/식별 추상화(계약)
studio-platform-security(+acl)/  # 보안 + ACL
studio-platform-realtime/        # 실시간 기능(웹소켓 등) 공통
studio-platform-storage/         # 오브젝트 스토리지 공통
studio-platform-user/            # 사용자/그룹/역할/회사 도메인 (계약)
studio-platform-user-default/    # 사용자 기본 구현 (엔터티/리포지토리/서비스/컨트롤러)
```

## 주요 모듈
- `studio-platform`: 공통 웹/예외/도메인 계약
- `studio-platform-security`, `studio-platform-security-acl`: 인증/인가, JWT, ACL
- `studio-platform-user`, `studio-platform-user-default`: 사용자 계약과 기본 구현
- `studio-platform-data`, `studio-platform-objecttype`, `studio-platform-realtime`: 데이터, objectType, 실시간 기능 공통
- `studio-platform-ai`, `studio-platform-storage`, `studio-platform-identity`: AI/RAG 계약, 저장소, 식별 공통
- `studio-application-modules/*`: attachment, avatar, embedding pipeline, template, mail

세부 설정, 엔드포인트, 확장 포인트는 각 모듈 README를 참고한다.

## 스타터
각 기능은 대응되는 스타터를 추가하면 자동 구성된다. 요약은 `starter/README.md` 참고.
예시:
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter"))          // 플랫폼 기본
    implementation(project(":starter:studio-platform-starter-security")) // 보안
    implementation(project(":starter:studio-platform-starter-user"))     // 사용자
    implementation(project(":starter:studio-platform-starter-objecttype")) // objectType
    implementation(project(":starter:studio-application-starter-attachment")) // 첨부
}
```

대표적인 선택 기준:
- 공통 웹/데이터/JPA 기반은 `:starter:studio-platform-starter`
- 인증/인가가 필요하면 `:starter:studio-platform-starter-security`
- 사용자 기본 구현까지 필요하면 `:starter:studio-platform-starter-user`와 `:studio-platform-user-default`
- objectType 정책/검증이 필요하면 `:starter:studio-platform-starter-objecttype`
- STOMP/WebSocket 실시간 알림이 필요하면 `:starter:studio-platform-starter-realtime`
- 첨부/아바타/템플릿/메일 같은 기능 모듈은 각 application starter를 추가

대표 조합 예시:

```kotlin
// 기본 인증 앱
implementation(project(":starter:studio-platform-starter"))
implementation(project(":starter:studio-platform-starter-security"))
implementation(project(":starter:studio-platform-starter-user"))
implementation(project(":studio-platform-user-default"))

// 첨부 + AI 임베딩 앱
implementation(project(":starter:studio-application-starter-attachment"))
implementation(project(":studio-application-modules:content-embedding-pipeline"))
implementation(project(":starter:studio-platform-starter-ai"))
implementation("org.springframework.ai:spring-ai-starter-model-openai")

// 실시간 알림 앱
implementation(project(":starter:studio-platform-starter-realtime"))
implementation("org.springframework.boot:spring-boot-starter-data-redis")

// 템플릿 + 메일 앱
implementation(project(":starter:studio-application-starter-template"))
implementation(project(":starter:studio-application-starter-mail"))
```

### AI 스타터 사용 시 주의사항
`studio-platform-starter-ai`는 provider 라이브러리를 `compileOnly`로만 제공하므로,
소비 앱에서 필요한 provider를 직접 선언해야 한다. Spring AI BOM은 `api(platform(...))`으로
노출되므로 별도 BOM 선언 없이 일관된 Spring AI 버전을 사용할 수 있다.

```kotlin
// OpenAI 사용 예시
implementation(project(":starter:studio-platform-starter-ai"))
implementation("org.springframework.ai:spring-ai-starter-model-openai")

// Google GenAI 사용 예시
implementation(project(":starter:studio-platform-starter-ai"))
implementation("org.springframework.ai:spring-ai-google-genai")

// Ollama 사용 예시
implementation(project(":starter:studio-platform-starter-ai"))
implementation("org.springframework.ai:spring-ai-ollama")
```

## 모듈 의존 방향
의존성은 아래 방향을 권장한다. (순환 의존 금지)

```
studio-platform
  → studio-platform-objecttype
  → studio-platform-data

studio-platform
  → studio-platform-security
  → studio-platform-security-acl

studio-platform
  → studio-platform-user
  → studio-platform-user-default

studio-platform
  → studio-platform-ai

studio-platform
  → studio-platform-realtime
  → studio-platform-storage

starter
  → platform modules
  → application modules

application modules
  → platform modules
```

## 사용 요약
- 스타터를 통해 필요한 기능만 활성화한다.
- 세부 웹/API 규칙은 `studio-platform/WEB_API_DEVELOPMENT_GUIDE.md`를 따른다.
- ACL 외부 연동은 `studio.one.platform.security.acl.AclPermissionService` 인터페이스만 의존한다.

## 빌드
```bash
./gradlew clean build
```
모듈은 라이브러리 형태로 배포되며, 스타터를 사용하는 애플리케이션에서 의존성을 추가해 실행한다.

로컬에 Gradle이 설치되어 있고 wrapper 파일을 다시 만들고 싶으면 다음 명령을 사용할 수 있다.

```bash
gradle wrapper
```

개별 모듈만 확인할 때는 다음처럼 실행할 수 있다.

```bash
./gradlew :studio-platform:build
./gradlew :studio-application-modules:attachment-service:test
```

## 로컬 Nexus 배포
개발 중 로컬 Nexus에 배포할 때는 `gradle.properties`를 수정하지 말고 로컬 배포 스크립트를 사용한다.
스크립트는 기본적으로 `.env.local`을 읽으며, 이미 셸에 설정된 환경변수는 덮어쓰지 않는다.

```bash
NEXUS_USERNAME=...
NEXUS_PASSWORD=...
scripts/publish-local-nexus.sh
```

특정 모듈만 배포할 때는 Gradle task를 그대로 전달한다.

```bash
scripts/publish-local-nexus.sh :studio-platform-user:publish
```

로컬 Nexus에 같은 버전이 이미 올라가 있어 삭제 후 다시 배포해야 할 때는 `--delete-existing`을 사용한다.
이 옵션만 지정하면 settings에 포함된 전체 모듈을 확인하고, 기존 component가 있는 경우 삭제한 뒤 기본 `publish`를 실행한다.

```bash
scripts/publish-local-nexus.sh --delete-existing
```

특정 모듈만 삭제 후 재배포할 때는 대상 모듈을 명시한다.

```bash
scripts/publish-local-nexus.sh --delete-existing --module :studio-platform-user
```

이 스크립트는 기본적으로 `http://localhost:8081/repository/maven-releases/`와
`http://localhost:8081/repository/maven-snapshots/`를 사용하며, repository URL과
`nexus.allowInsecure=true` 값을 Gradle project property로 전달한다.
로컬 Nexus base URL이 다르면 `NEXUS_URL` 환경변수로 변경할 수 있다.
다른 env 파일을 쓰려면 `--env-file <path>`를 전달한다.

## 보안 설정
- secret은 저장소에 커밋하지 않고 환경변수 또는 `~/.gradle/gradle.properties` 로만 주입한다.
- 샘플 환경변수 목록은 [.env.example](.env.example), 상세 운영 규칙과 회전 절차는 [SECURITY.md](SECURITY.md)를 참고한다.

자주 필요한 값:
- `STUDIO_JWT_SECRET`
- `JASYPT_ENCRYPTOR_PASSWORD`
- `JASYPT_HTTP_TOKEN`
- `OPENAI_API_KEY`
- `NEXUS_USERNAME`, `NEXUS_PASSWORD`

| 환경변수 | 관련 기능/스타터 | 미설정 시 동작 |
|---|---|---|
| `STUDIO_JWT_SECRET` | `studio-platform-starter-security` | JWT 활성화 시 기동 실패 |
| `JASYPT_ENCRYPTOR_PASSWORD` | `studio-platform-starter-jasypt` | 암호화 프로퍼티 복호화 실패 |
| `JASYPT_HTTP_TOKEN` | `studio-platform-starter-jasypt` | 내부 Jasypt HTTP 엔드포인트 보호 토큰으로 사용 |
| `OPENAI_API_KEY` | `studio-platform-starter-ai` + OpenAI provider | OpenAI provider 활성화 시 기동 실패 |
| `GOOGLE_API_KEY` 또는 `GOOGLE_AI_API_KEY` | `studio-platform-starter-ai` + Google GenAI provider | Google provider 활성화 시 기동 실패 |
| `NEXUS_USERNAME`, `NEXUS_PASSWORD`, `NEXUS_URL` | `scripts/publish-local-nexus.sh` | 로컬 Nexus 배포 스크립트 실패 |

## 기본 설정 예시
```yaml
studio:
  persistence:
    type: jpa            # jpa|jdbc
  features:
    attachment:
      enabled: true
      web:
        enabled: true
        base-path: /api/mgmt/attachments
      storage:
        type: filesystem # filesystem|database
        cache-enabled: false
    avatar-image:
      enabled: true
    user:
      enabled: true
      web:
        enabled: true
    security-acl:
      enabled: true
      cache-name: aclCache
      admin-role: ROLE_ADMIN
      use-spring-acl: false
      metrics-enabled: true
      audit-enabled: true
  ai:
    enabled: true
    default-provider: openai   # 필수 — 미설정 시 기동 실패
    providers:
      openai:
        type: OPENAI
        chat:
          enabled: true
        embedding:
          enabled: true
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        options:
          model: text-embedding-3-small
```
필요 없는 기능은 `enabled=false` 로 비활성화하고, 경로나 저장소 타입은 `studio.features.<feature>.*` 속성으로 조정한다.

## 문서 바로가기
- 스타터 요약: `starter/README.md`
- 애플리케이션 모듈 가이드: `studio-application-modules/README.md`
- 사용자 계약: `studio-platform-user/README.md`
- 사용자 기본 구현: `studio-platform-user-default/README.md`
- 변경 이력: `CHANGELOG.md` (`2.x` 라인부터 새 기준으로 관리)
- 보안 운영 규칙: `SECURITY.md`
- 플랫폼 웹 규칙: `studio-platform/WEB_API_DEVELOPMENT_GUIDE.md`
- 설정 네임스페이스 가이드: `CONFIGURATION_NAMESPACE_GUIDE.md`

## 포함 모듈
- 플랫폼 공통: `studio-platform`, `studio-platform-data`, `studio-platform-autoconfigure`, `studio-platform-identity`
- 보안: `studio-platform-security`, `studio-platform-security-acl`
- 사용자: `studio-platform-user`, `studio-platform-user-default`
- 부가기능: `studio-platform-objecttype`, `studio-platform-realtime`, `studio-platform-storage`, `studio-platform-ai`
- 애플리케이션 모듈: `attachment-service`, `avatar-service`, `content-embedding-pipeline`, `template-service`, `mail-service`
