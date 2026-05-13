# Studio Starters

애플리케이션에서 공통 기능을 빠르게 활성화하기 위한 Spring Boot starter 모음이다. 각 starter는 `studio.features.*`로 feature gate를, `studio.*`로 runtime detail을 다룬다. AI provider SDK 값은 `studio.ai.providers.<id>.*`가 canonical source이며, `spring.ai.*`는 1.x migration fallback으로만 지원한다.

## 빠른 선택 가이드
- 공통 웹/데이터/JPA 기반이 필요하면 `:starter:studio-platform-starter`
- 인증/인가가 필요하면 `:starter:studio-platform-starter-security`
- 사용자 기본 구현까지 필요하면 `:starter:studio-platform-starter-user`
- ACL이 필요하면 `:starter:studio-platform-starter-security-acl`
- objectType 레지스트리/정책이 필요하면 `:starter:studio-platform-starter`와 `:starter:studio-platform-starter-objecttype`
- workspace tree/member/permission API가 필요하면 `:starter:studio-platform-starter-workspace`
- workspace 기반 Wiki page/revision API가 필요하면 `:starter:studio-application-starter-wiki`
- MyBatis mapper convention이 필요하면 `:starter:studio-platform-starter-mybatis`
- WebSocket/STOMP 실시간 알림이 필요하면 `:starter:studio-platform-starter-realtime`
- RAG indexing용 chunking 전략이 필요하면 `:starter:studio-platform-starter-chunking`
- image/PDF 썸네일 생성 SPI가 필요하면 `:starter:studio-platform-thumbnail-starter`
- 첨부/아바타/템플릿/메일/Wiki는 각 application starter를 추가

최소 예시:

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter"))
    implementation(project(":starter:studio-platform-starter-security"))
}
```

## 사용 원칙
- feature 플래그가 `true`일 때만 빈이 등록된다.
- REST 엔드포인트를 노출하는 모듈은 `...web.enabled`로 비활성화할 수 있다.
- 파일/오브젝트 스토리지를 쓰는 모듈은 경로와 자격 증명을 먼저 확인한다.
- AI provider API key, model, base-url 같은 외부 SDK 값은 `studio.ai.providers.<id>.*`를 표준으로 사용한다. 기존 `spring.ai.*` 값은 1.x 호환 fallback으로만 읽는다.
- `studio.features.<module>.*`에는 enable/persistence/web만 두고, 세부 정책과 storage/routing/rag는 `studio.<module>.*`에 둔다.
- persistence resolver는 MyBatis-aware 경로에서 `studio.features.<module>.persistence`를 전역 `studio.persistence.type`보다 우선하도록 제공된다. 값은 `jpa`, `mybatis`, `jdbc`를 지원하지만, feature 조건은 각 starter가 실제 제공하는 구현만 활성화한다.
- 아직 직접 JDBC 구현만 제공하는 feature는 해당 starter의 feature-scoped 설정을 따른다. MyBatis 구현이 추가된 feature는 `studio.features.<module>.persistence=mybatis`로 opt-in하고, 직접 JDBC 호환 경로가 필요한 feature는 `jdbc` 또는 starter가 제공하는 `mybatisAsJdbc` fallback을 사용한다.
- SQL mapper는 MyBatis convention을 사용한다. XML mapper 리소스는 `classpath*:mybatis/**/*.xml`로 로드한다.

## 포함 starter
- `studio-platform-starter`: 코어 플랫폼 자동 구성, 공통 유틸, 기본 프로퍼티 바인딩
- `studio-platform-starter-security`: Spring Security 기본 구성과 인증/인가 훅
- `studio-platform-starter-security-acl`: ACL 엔티티/리포지토리 스캔과 ACL 연동
- `studio-platform-starter-user`: 사용자 도메인 서비스와 기본 REST 구성
- `studio-platform-starter-objecttype`: objectType 레지스트리/정책/런타임 검증 자동 구성. 기반 계약과 data helper는 `studio-platform-starter`가 제공한다.
- `studio-platform-starter-workspace`: workspace tree/member/permission JPA 기본 구현과 API 자동 구성
- `studio-platform-starter-mybatis`: MyBatis Boot starter와 `classpath*:mybatis/**/*.xml` mapper convention 자동 구성
- `studio-platform-starter-realtime`: WebSocket/STOMP 엔드포인트와 Redis Pub/Sub 연동 자동 구성
- `studio-platform-starter-chunking`: RAG indexing용 fixed-size/recursive chunking 자동 구성
- `studio-platform-thumbnail-starter`: image/PDF 썸네일 generation service 자동 구성
- `studio-platform-starter-ai`: OpenAI/LangChain4j, 벡터스토어, RAG 등 AI core 구성
- `studio-platform-starter-ai-web`: AI HTTP endpoint와 JSON component 노출
- `studio-platform-starter-jasypt`: Jasypt 암호화/복호화 지원
- `studio-platform-starter-objectstorage`, `-aws`, `-oci`: 오브젝트 스토리지 공통 및 provider별 구성
- `studio-application-starter-attachment`, `-avatar`, `-template`, `-mail`, `-wiki`: 애플리케이션 기능 모듈 자동 구성

## 대표 조합

**기본 인증 앱**
```kotlin
implementation(project(":starter:studio-platform-starter"))
implementation(project(":starter:studio-platform-starter-security"))
implementation(project(":starter:studio-platform-starter-user"))
implementation(project(":studio-platform-user-default"))
```

**첨부파일 + AI 임베딩 앱**
```kotlin
implementation(project(":starter:studio-platform-starter"))
implementation(project(":starter:studio-platform-starter-security"))
implementation(project(":starter:studio-platform-starter-user"))
implementation(project(":starter:studio-platform-starter-objecttype"))
implementation(project(":starter:studio-application-starter-attachment"))
implementation(project(":studio-application-modules:content-embedding-pipeline"))
implementation(project(":starter:studio-platform-starter-chunking"))
implementation(project(":starter:studio-platform-starter-ai"))
```

**실시간 알림 앱**
```kotlin
implementation(project(":starter:studio-platform-starter"))
implementation(project(":starter:studio-platform-starter-security"))
implementation(project(":starter:studio-platform-starter-realtime"))
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

**템플릿 + 메일 앱**
```kotlin
implementation(project(":starter:studio-application-starter-template"))
implementation(project(":starter:studio-application-starter-mail"))
```

**Workspace + Wiki 앱**
```kotlin
implementation(project(":starter:studio-platform-starter-workspace"))
implementation(project(":starter:studio-application-starter-wiki"))
```

## studio-platform-starter-ai 사용법

AI 기능을 사용하려면 `studio-platform-starter-ai`만 선언한다. 스타터는 OpenAI, Google AI Gemini, Ollama용 LangChain4j provider artifact를 직접 포함하며 Spring AI BOM/provider starter를 요구하지 않는다.

### OpenAI

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))
}
```

```yaml
# application.yml
studio:
  features:
    ai:
      enabled: true
  ai:
    routing:
      default-chat-provider: openai
      default-embedding-provider: openai
    providers:
      openai:
        type: OPENAI
        enabled: true
        api-key: ${OPENAI_API_KEY}
        base-url: https://api.openai.com/v1
        chat:
          enabled: true
          model: gpt-4o-mini
        embedding:
          enabled: true
          model: text-embedding-3-small
          dimension: 1536
```

### Google GenAI (Chat)

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))
}
```

```yaml
studio:
  features:
    ai:
      enabled: true
  ai:
    routing:
      default-chat-provider: google
      default-embedding-provider: google
    providers:
      google:
        type: GOOGLE_AI_GEMINI
        enabled: true
        api-key: ${GOOGLE_API_KEY}
        chat:
          enabled: true
          model: gemini-1.5-flash
        embedding:
          enabled: true
          model: gemini-embedding-001
          dimension: 768
        google-embedding:
          task-type: RETRIEVAL_DOCUMENT
```

### Google GenAI (Embedding)

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))
}
```

```yaml
studio:
  features:
    ai:
      enabled: true
  ai:
    routing:
      default-chat-provider: google
      default-embedding-provider: google
    providers:
      google:
        type: GOOGLE_AI_GEMINI
        enabled: true
        api-key: ${GOOGLE_API_KEY}
        chat:
          enabled: true
          model: gemini-1.5-flash
        embedding:
          enabled: true
          model: text-embedding-004
          dimension: 768
        google-embedding:
          task-type: RETRIEVAL_DOCUMENT
```

### Ollama (Embedding)

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))
}
```

```yaml
studio:
  features:
    ai:
      enabled: true
  ai:
    routing:
      default-chat-provider: openai
      default-embedding-provider: ollama
    providers:
      openai:
        type: OPENAI
        enabled: true
        api-key: ${OPENAI_API_KEY}
        chat:
          enabled: true
          model: gpt-4o-mini
      ollama:
        type: OLLAMA
        enabled: true
        base-url: http://localhost:11434
        embedding:
          enabled: true
          model: nomic-embed-text
```

### 동작 원리
- LangChain4j provider artifact가 스타터에 포함되어 있으며, provider 설정은 `studio.ai.providers.*`로 활성화한다.
- `studio.features.ai.enabled`가 AI feature gate이며, provider routing은 `studio.ai.routing.*`, provider SDK 값은 `studio.ai.providers.*`에 둔다. `spring.ai.*`는 migration fallback이다.
- `langchain4j.*` namespace는 운영 설정으로 노출하지 않는다.

## 문서 바로가기
- 루트 개요: `../README.md`
- 애플리케이션 모듈 가이드: `../studio-application-modules/README.md`
- 새 스타터 작성 절차: `STARTER_GUIDE.md`
- 플랫폼 starter 상세: `studio-platform-starter/README.md`
- Security starter 상세: `studio-platform-starter-security/README.md`
- Security ACL starter 상세: `studio-platform-starter-security-acl/README.md`
- 사용자 starter 상세: `studio-platform-starter-user/README.md`
- Workspace starter 상세: `studio-platform-starter-workspace/README.md`
- Jasypt starter 상세: `studio-platform-starter-jasypt/README.md`
- ObjectType starter 상세: `studio-platform-starter-objecttype/README.md`
- Realtime starter 상세: `studio-platform-starter-realtime/README.md`
- AI starter 상세: `studio-platform-starter-ai/README.md`
- AI Web starter 상세: `studio-platform-starter-ai-web/README.md`
- Object Storage starter 상세: `studio-platform-starter-objectstorage/README.md`
- Attachment starter 상세: `studio-application-starter-attachment/README.md`
- Thumbnail starter 상세: `studio-platform-thumbnail-starter/README.md`
- Avatar starter 상세: `studio-application-starter-avatar/README.md`
- Template starter 상세: `studio-application-starter-template/README.md`
- Mail starter 상세: `studio-application-starter-mail/README.md`
