# Studio Starters

애플리케이션에서 공통 기능을 빠르게 활성화하기 위한 Spring Boot starter 모음이다. 각 starter는 `studio.features.*`로 feature gate를, `studio.*`로 runtime detail을, `spring.*`으로 외부 provider SDK 값을 다룬다.

## 빠른 선택 가이드
- 공통 웹/데이터/JPA 기반이 필요하면 `:starter:studio-platform-starter`
- 인증/인가가 필요하면 `:starter:studio-platform-starter-security`
- 사용자 기본 구현까지 필요하면 `:starter:studio-platform-starter-user`
- ACL이 필요하면 `:starter:studio-platform-starter-security-acl`
- objectType 레지스트리/정책이 필요하면 `:starter:studio-platform-starter-objecttype`
- WebSocket/STOMP 실시간 알림이 필요하면 `:starter:studio-platform-starter-realtime`
- RAG indexing용 chunking 전략이 필요하면 `:starter:studio-platform-starter-chunking`
- 첨부/아바타/템플릿/메일은 각 application starter를 추가

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
- provider API key, model, base-url 같은 외부 SDK 값은 가능하면 `spring.*`를 단일 소스로 사용한다.
- `studio.features.<module>.*`에는 enable/persistence/web만 두고, 세부 정책과 storage/routing/rag는 `studio.<module>.*`에 둔다.

## 포함 starter
- `studio-platform-starter`: 코어 플랫폼 자동 구성, 공통 유틸, 기본 프로퍼티 바인딩
- `studio-platform-starter-security`: Spring Security 기본 구성과 인증/인가 훅
- `studio-platform-starter-security-acl`: ACL 엔티티/리포지토리 스캔과 ACL 연동
- `studio-platform-starter-user`: 사용자 도메인 서비스와 기본 REST 구성
- `studio-platform-starter-objecttype`: objectType 레지스트리/정책/런타임 검증 자동 구성
- `studio-platform-starter-realtime`: WebSocket/STOMP 엔드포인트와 Redis Pub/Sub 연동 자동 구성
- `studio-platform-starter-chunking`: RAG indexing용 fixed-size/recursive chunking 자동 구성
- `studio-platform-starter-ai`: OpenAI/Spring AI, 벡터스토어, RAG 등 AI core 구성
- `studio-platform-starter-ai-web`: AI HTTP endpoint와 JSON component 노출
- `studio-platform-starter-jasypt`: Jasypt 암호화/복호화 지원
- `studio-platform-starter-objectstorage`, `-aws`, `-oci`: 오브젝트 스토리지 공통 및 provider별 구성
- `studio-application-starter-attachment`, `-avatar`, `-template`, `-mail`: 애플리케이션 기능 모듈 자동 구성

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
implementation(project(":starter:studio-application-starter-attachment"))
implementation(project(":studio-application-modules:content-embedding-pipeline"))
implementation(project(":starter:studio-platform-starter-chunking"))
implementation(project(":starter:studio-platform-starter-ai"))
implementation("org.springframework.ai:spring-ai-starter-model-openai")
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

## studio-platform-starter-ai 사용법

AI 기능을 사용하려면 스타터와 함께 **필요한 provider 라이브러리를 직접 선언**해야 한다. 스타터는 provider 라이브러리를 transitive하게 제공하지 않는다.

### OpenAI

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
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
        chat:
          enabled: true
        embedding:
          enabled: true
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat.options.model: gpt-4o-mini
      embedding.options.model: text-embedding-3-small
```

### Google GenAI (Chat)

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))
    implementation("org.springframework.ai:spring-ai-google-genai")
    implementation("org.springframework.ai:spring-ai-google-genai-embedding")
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
        chat:
          enabled: true
        embedding:
          enabled: true
spring:
  ai:
    google:
      genai:
        chat:
          api-key: ${GOOGLE_API_KEY}
          options:
            model: gemini-2.5-flash
        embedding:
          api-key: ${GOOGLE_API_KEY}
          text:
            options:
              model: gemini-embedding-001
```

### Google GenAI (Embedding)

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))
    implementation("org.springframework.ai:spring-ai-google-genai")
    implementation("org.springframework.ai:spring-ai-google-genai-embedding")
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
        chat:
          enabled: true
        embedding:
          enabled: true
spring:
  ai:
    google:
      genai:
        chat:
          api-key: ${GOOGLE_API_KEY}
          options:
            model: gemini-2.5-flash
        embedding:
          api-key: ${GOOGLE_API_KEY}
          text:
            options:
              model: text-embedding-004
```

### Ollama (Embedding)

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))
    implementation("org.springframework.ai:spring-ai-ollama")
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
        chat:
          enabled: true
      ollama:
        type: OLLAMA
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
    ollama:
      base-url: http://localhost:11434
      embedding.options.model: nomic-embed-text
```

### 동작 원리
- provider 라이브러리가 classpath에 있을 때만 해당 provider auto-configuration이 활성화된다.
- `studio.features.ai.enabled`가 AI feature gate이며, provider routing은 `studio.ai.routing.*`, provider SDK 값은 `spring.ai.*`에 둔다.
- Spring AI BOM이 `api`로 노출되므로 provider 라이브러리의 버전은 별도로 지정하지 않아도 된다.

## 문서 바로가기
- 루트 개요: `../README.md`
- 애플리케이션 모듈 가이드: `../studio-application-modules/README.md`
- 새 스타터 작성 절차: `STARTER_GUIDE.md`
- 플랫폼 starter 상세: `studio-platform-starter/README.md`
- Security starter 상세: `studio-platform-starter-security/README.md`
- Security ACL starter 상세: `studio-platform-starter-security-acl/README.md`
- 사용자 starter 상세: `studio-platform-starter-user/README.md`
- Jasypt starter 상세: `studio-platform-starter-jasypt/README.md`
- ObjectType starter 상세: `studio-platform-starter-objecttype/README.md`
- Realtime starter 상세: `studio-platform-starter-realtime/README.md`
- AI starter 상세: `studio-platform-starter-ai/README.md`
- AI Web starter 상세: `studio-platform-starter-ai-web/README.md`
- Object Storage starter 상세: `studio-platform-starter-objectstorage/README.md`
- Attachment starter 상세: `studio-application-starter-attachment/README.md`
- Avatar starter 상세: `studio-application-starter-avatar/README.md`
- Template starter 상세: `studio-application-starter-template/README.md`
- Mail starter 상세: `studio-application-starter-mail/README.md`
