# studio-platform-starter-realtime

`studio-platform-realtime`의 WebSocket/STOMP 기능을 Spring Boot auto-configuration으로 등록하는 starter다. STOMP 엔드포인트, Simple Broker, 선택적 Redis Pub/Sub fan-out, JWT 기반 handshake principal 구성을 제공한다.

## 의존성
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-realtime"))

    // Redis Pub/Sub 사용 시
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // JWT handshake 사용 시
    implementation(project(":starter:studio-platform-starter-security"))
}
```

## 자동 구성
`AutoConfiguration.imports`에 아래 구성이 등록된다.

| 구성 | 역할 |
|---|---|
| `RealtimeStompAutoConfiguration` | `RealtimeMessagingService`, JWT handshake handler, session interceptor 등록 |
| `RealtimeStompWebSocketAutoConfiguration` | STOMP endpoint와 Simple Broker 등록 |
| `RealtimeStompRedisAutoConfiguration` | Redis template/listener 기반 Pub/Sub fan-out 등록 |

## 설정
기능 gate는 `studio.features.realtime.enabled`이고, STOMP 런타임 설정은 `studio.realtime.stomp.*`에 둔다.

```yaml
studio:
  features:
    realtime:
      enabled: true
  realtime:
    stomp:
      enabled: true
      endpoint: /ws
      app-destination-prefix: /app
      topic-prefix: /topic
      user-prefix: /user
      allowed-origins:
        - "https://app.example.com"
      sock-js: true
      jwt-enabled: true
      reject-anonymous: true
      redis-enabled: false
      redis-channel: studio:realtime:events
```

## 동작 조건
- `studio.features.realtime.enabled=true`일 때 WebSocket/STOMP 구성이 활성화된다.
- `studio.realtime.stomp.enabled=true`가 기본값이다.
- `studio.realtime.stomp.jwt-enabled=true`인데 `JwtTokenProvider` 빈이 없으면 기동 시 실패한다.
- Redis 연동은 `studio.realtime.stomp.redis-enabled=true`이고 `RedisConnectionFactory`가 있을 때 활성화된다.
- 다중 노드 fan-out이 필요하면 `RealtimeMessagingService.publish(...)`를 사용한다.

## 관련 모듈
- `studio-platform-realtime`: STOMP domain, messaging service, Redis subscriber 구현.
- `studio-platform-starter-security`: JWT handshake에 필요한 `JwtTokenProvider` 제공.
- `studio-application-starter-mail`: 메일 동기화 완료 알림을 STOMP로 전송할 때 이 starter를 소비한다.

## 검증
```bash
./gradlew :starter:studio-platform-starter-realtime:compileJava
```
