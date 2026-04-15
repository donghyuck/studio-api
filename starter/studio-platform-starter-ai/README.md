# studio-platform-starter-ai

AI 서비스(채팅, 임베딩, 벡터 스토어, RAG 파이프라인)를 자동 구성하는 스타터이다.
애플리케이션 모듈은 이 스타터가 제공하는 포트와 `RagPipelineService`를 통해 공통 AI 기능을 재사용할 수 있다.
멀티 프로바이더 팩토리 패턴을 통해 OpenAI, Google AI Gemini, Ollama 등 여러 LLM 프로바이더를
동시에 등록하고, `default-provider`로 지정한 프로바이더를 기본 `ChatPort` / `EmbeddingPort` 빈으로 노출한다.
JdbcTemplate이 컨텍스트에 있으면 pgvector 기반 `VectorStorePort`도 자동으로 생성된다.

> **중요** Spring AI BOM(`org.springframework.ai:spring-ai-bom:1.1.2`)이 `api(platform())` 으로
> 노출되므로, 소비 앱에서 별도로 BOM을 선언하지 않아도 Spring AI 의존성 버전이 자동 관리된다.

## 1) 의존성 추가

스타터 자체와 함께 **사용할 프로바이더 라이브러리를 소비 앱에서 직접 선언**해야 한다.
프로바이더 라이브러리는 `compileOnly`로 선언되어 있어 전이 의존성으로 포함되지 않는다.

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))

    // --- 프로바이더 라이브러리 (사용할 것만 선언) ---

    // OpenAI
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // Google AI Gemini (채팅)
    implementation("org.springframework.ai:spring-ai-google-genai")

    // Google AI Gemini (임베딩)
    implementation("org.springframework.ai:spring-ai-google-genai-embedding")

    // Ollama
    implementation("org.springframework.ai:spring-ai-ollama")

    // 벡터 스토어를 사용할 경우 (pgvector)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
```

## 2) 기능 활성화

```yaml
studio:
  ai:
    enabled: true
```

## 3) 설정

### 기본 구조

```yaml
studio:
  ai:
    enabled: true
    default-provider: openai   # 기본 ChatPort / EmbeddingPort 로 사용할 프로바이더 ID
    providers:
      <provider-id>:
        type: OPENAI            # OPENAI | GOOGLE_AI_GEMINI | OLLAMA
        enabled: true
        chat:
          enabled: true
          model: gpt-4o-mini    # OPENAI / OLLAMA 는 여기서 지정
        embedding:
          enabled: true
          model: text-embedding-3-small
```

### OpenAI 예시

```yaml
studio:
  ai:
    enabled: true
    default-provider: openai
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
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        options:
          model: text-embedding-3-small
```

### Google AI Gemini 예시

```yaml
studio:
  ai:
    enabled: true
    default-provider: gemini
    providers:
      gemini:
        type: GOOGLE_AI_GEMINI
        enabled: true
        api-key: ${GOOGLE_AI_API_KEY}
        chat:
          enabled: true
          model: gemini-2.0-flash
        embedding:
          enabled: false

spring:
  ai:
    google:
      genai:
        embedding:
          api-key: ${GOOGLE_AI_API_KEY}
          text:
            options:
              model: text-embedding-004
```

### Ollama 예시

```yaml
studio:
  ai:
    enabled: true
    default-provider: ollama
    providers:
      ollama:
        type: OLLAMA
        enabled: true
        chat:
          enabled: true
          model: llama3.2
        embedding:
          enabled: true

spring:
  ai:
    ollama:
      embedding:
        options:
          model: nomic-embed-text
```

### 엔드포인트 기본 경로 (선택)

```yaml
studio:
  ai:
    endpoints:
      enabled: false       # AI 정보 조회 엔드포인트 활성화 여부
      base-path: /api/ai   # 기본값
```

## 4) 자동 구성되는 주요 빈

| 빈 타입 | 설명 |
|---|---|
| `AiProviderRegistry` | 등록된 모든 프로바이더의 ChatPort / EmbeddingPort 를 보관하는 레지스트리 |
| `ChatPort` (기본) | `default-provider`에 해당하는 채팅 포트 |
| `EmbeddingPort` (기본) | `default-provider`에 해당하는 임베딩 포트 |
| `VectorStorePort` | JdbcTemplate이 있을 때 pgvector 기반 벡터 스토어 자동 생성 |
| `RagPipelineService` | RAG 인덱싱/검색 파이프라인 서비스 |
| `PromptManager` | Mustache 템플릿 기반 프롬프트 렌더러 |

`RagPipelineService`는 문서/파일/도메인 객체별 텍스트를 chunk로 나누고, 임베딩과 메타데이터를 벡터 스토어에 저장한다.
`objectType`/`objectId` 메타데이터를 함께 저장하면 AI web starter의 RAG chat API에서 특정 파일이나 객체 범위에 한정해 답변할 수 있다.

## 관련 모듈
- `studio-platform-ai` — 이 스타터가 구현하는 포트 인터페이스 모듈
- `studio-platform-starter-ai-web` — AI HTTP 엔드포인트를 노출하는 짝 스타터 (이 스타터와 함께 사용)
- `studio-application-modules/content-embedding-pipeline` — 이 스타터의 `EmbeddingPort`/`VectorStorePort`를 소비하는 임베딩 파이프라인 모듈
- `docs/dev/spring-ai-openai.md` — OpenAI provider의 Spring AI 전환 방향과 롤백 참고 문서

## 5) 참고 사항

- **fail-fast**: `studio.ai.default-provider`가 설정되지 않았거나, 해당 프로바이더에 chat/embedding 포트가
  모두 없으면 애플리케이션 시작 시 `IllegalStateException`으로 즉시 실패한다.
- **default-provider 생략 불가**: `default-provider`는 필수 항목이다. 값이 없으면 시작이 거부된다.
- OPENAI 타입 프로바이더는 동시에 하나만 활성화할 수 있다. 두 개 이상 활성화하면 시작 시 실패한다.
- Spring AI 표준 속성(`spring.ai.*`)과 Studio 전용 속성(`studio.ai.*`)을 혼합 사용한다.
  OpenAI의 API 키 및 모델은 `spring.ai.openai.*`로 설정하고, Google/Ollama 고유 옵션은
  각 프로바이더 `studio.ai.providers.<id>.*` 하위에 설정한다.
- 벡터 스토어는 PostgreSQL + pgvector 확장을 사용하며, `JdbcTemplate` 빈이 없으면 생성되지 않는다.
- Caffeine 캐시와 Resilience4j Retry가 내장되어 있어 LLM 호출에 재시도 및 캐싱이 적용된다.
