# Redis RAG cache 적용 가이드

## 현재 결정

- exact answer cache는 Spring Boot 3.5.16/Spring AI 1.1.8 기준으로 제공한다.
- 기본 backend는 `NONE`이며 개발 서버에서만 명시적으로 `REDIS`를 활성화한다.
- semantic cache는 serving하지 않는다. 준비된 Redis가 Query Engine/vector search를 지원하는지,
  HNSW index 생성 권한이 있는지 확인되기 전에는 shadow 실행도 시작하지 않는다.
- Boot 4.1/Spring AI 2.0 전환은
  [호환성 스파이크](spring-boot-4-spring-ai-2-spike.md)의 재개 조건을 충족할 때까지 보류한다.

## 개발 서버 설정

소비 서버에 다음 runtime dependency와 설정이 필요하다.

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      username: ${REDIS_USERNAME}
      password: ${REDIS_PASSWORD}
      ssl:
        enabled: true

studio:
  ai:
    rag:
      answer-cache:
        type: redis
        ttl: 5m
        namespace: studio:ai:rag-answer:v1
        fail-open: true
```

비밀값은 repository나 로그에 기록하지 않는다. realtime Redis와 같은 서버를 사용하더라도 별도 ACL
사용자와 `studio:ai:rag-answer:v1` prefix 권한을 사용한다.

## 승격 순서

1. `type=none`으로 candidate server의 ApplicationContext, chat, embedding, RAG sync/SSE를 확인한다.
2. Redis 연결·ACL·TLS를 확인한다.
3. TTL 5분과 새 namespace로 exact cache를 활성화한다.
4. 동일 principal/object/evidence 요청을 두 번 실행한다.
5. 두 번째 응답의 `metadata.ragAnswerCache=HIT`, provider 호출 0회, canonical/reference parity를 확인한다.
6. object revision 변경, 재색인, 다른 principal/object 요청이 모두 miss인지 확인한다.
7. Redis를 중단해도 API가 provider 경로로 정상 응답하는지 확인한다.
8. warm p95가 cold path보다 50% 이상 개선되는지 확인한 뒤 TTL을 조정한다.

## Compatibility matrix

| Server | Candidate artifact | Cache mode | Context | Chat | Embedding | RAG sync | RAG SSE | Redis fail-open | Owner |
|---|---|---|---|---|---|---|---|---|---|
| 개발 서버 | 미입력 | `none` → `redis` | 미검증 | 미검증 | 미검증 | 미검증 | 미검증 | 미검증 | 미입력 |

이 repository에는 실행 가능한 server instance와 해당 `application.yml`이 포함되어 있지 않다.
따라서 위 matrix가 소비 서버에서 채워지기 전에는 candidate artifact를 정식 승격하지 않는다.

## Semantic cache 사전 점검

다음을 모두 확인한 뒤에만 별도 shadow 작업을 시작한다.

- Redis Query Engine/vector search 명령 사용 가능
- HNSW/vector index 생성·삭제 권한
- 고정 embedding deployment와 dimension
- memory/eviction/backup 정책
- 최소 200개 평가 질문과 human review 계획

shadow candidate는 사용자에게 반환하지 않는다. workspace/object/revision/retrieval/prompt/model/evidence
fingerprint가 하나라도 다르면 miss로 처리하고, 현재 `PackedEvidenceSet`으로 citation support를 다시
검증한다. false-hit precision 0.99와 citation support precision 0.95를 충족하기 전까지 serving은
비활성화한다.

## Rollback

- 즉시 `studio.ai.rag.answer-cache.type=none`으로 변경한다.
- Redis 오류는 기본 fail-open이므로 provider 경로를 유지한다.
- namespace를 변경하면 기존 payload를 즉시 논리적으로 격리할 수 있다.
- cache 기능은 SQL schema를 변경하지 않으므로 DB rollback이 필요하지 않다.
