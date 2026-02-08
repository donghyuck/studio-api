# Studio Platform Realtime (WebSocket + STOMP)

Spring WebSocket/STOMP 공통 모듈. 단일 의존성 추가만으로 엔드포인트, 브로커, 메시징 서비스가 자동 구성되어 다른 모듈(예: 메일 동기화 완료 알림)에 재사용할 수 있다.

## 요약
WebSocket/STOMP + Redis Pub/Sub 기반의 실시간 메시징을 공통 모듈로 제공한다.

## 설계
- STOMP는 로컬 브로커에서 처리한다.
- 다중 노드는 Redis Pub/Sub으로 fan-out 한다.
- 페이로드는 DTO만 허용해 직렬화/보안 위험을 줄인다.

## 사용법
- 의존성 추가 후 `RealtimeMessagingService`로 전송
- 클라이언트는 `/ws` STOMP 엔드포인트에 연결해 `/topic` 구독

## 확장 포인트
- Redis Pub/Sub 사용 여부/채널 변경
- JWT Principal 주입 정책 변경
- 메시지 라우팅 규칙 변경(토픽/유저 경로)

## 설정
- `studio.realtime.stomp.*` (endpoint, prefix, redis, jwt)

## 환경별 예시
- **dev**: `redis-enabled=false`, `allowed-origins: ["*"]`로 로컬 개발 편의
- **stage**: redis-enabled=true로 다중 노드 fan-out 검증, jwt-enabled는 필요 시 활성화
- **prod**: redis-enabled=true 고정, allowed-origins 제한, `reject-anonymous=true` 고려

## YAML 예시
```yaml
studio:
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
      redis-enabled: true
      redis-channel: studio:realtime:events
```

## ADR
- `docs/adr/0001-stomp-redis-realtime.md`

## 목적 (Why)
- 멀티 서버 환경에서 비동기 작업 완료·이벤트·알림을 실시간 Push.
- 업무 모듈이 WebSocket/STOMP/Redis 구현을 몰라도 되도록 추상화 제공.

## 기본 전략 (How)
- 기술 조합: WebSocket + STOMP(클라이언트 통신), Redis Pub/Sub(서버 간 fan-out), JWT Principal(선택).
- 원칙: STOMP는 로컬 처리, 서버 간 동기화는 Redis Pub/Sub → 세션 공유 없이 확장.

### 아키텍처
```
Client
  ↓ (WebSocket + STOMP)
Load Balancer
  ↓
App Server A / B / C
  ↓           ↓
Redis Pub/Sub (fan-out)
  ↓           ↓
Local STOMP Broker
  ↓
Client
```

## 주요 기능
- STOMP 엔드포인트 자동 등록 (`/ws` 기본, SockJS 지원).
- Simple Broker 활성화 (`/topic`, `/user` 기본) 및 APP prefix(`/app`) 설정.
- `RealtimeMessagingService` 제공: 토픽/유저 전송 + Redis Pub/Sub 연계(옵션).
- DTO 전용 페이로드: `RealtimePayload` 인터페이스를 구현한 DTO만 전송/수신해 타입 안정성 확보.
- 설정은 `studio.realtime.*` 로 제어(`enabled=true` 기본).
- JWT 기반 Principal 주입(옵션): `studio.realtime.jwt-enabled=true` + `JwtTokenProvider` 빈이 있을 때 Authorization Bearer 토큰으로 Principal 설정.

## 설정 예시
```yaml
studio:
  realtime:
    stomp:
      enabled: true
      endpoint: /ws              # STOMP 엔드포인트
      app-destination-prefix: /app
      topic-prefix: /topic
      user-prefix: /user
      allowed-origins:           # CORS 허용 오리진
        - "*"
      sock-js: true              # SockJS fallback 사용 여부
      jwt-enabled: false         # JwtDecoder 사용 시 true
      redis-enabled: false       # Redis Pub/Sub 사용 시 true
      redis-channel: studio:realtime:events

> 참고: `studio.realtime.stomp.redis-enabled` 를 명시적으로 `false` 로 설정하면
> Redis auto-configuration 이 제외되어(연결 시도 포함) Redis 관련 오류를 방지한다.
```

## 사용법
1. 의존성 추가: `implementation(project(":studio-platform-realtime"))`.
2. DTO 정의 (`RealtimePayload` 구현 필수):
   ```java
   public class DemoPayload implements RealtimePayload {
       private String hello;
       // getters/setters
   }
   ```
3. 서버 측 전송:
   ```java
   @RestController
   @RequiredArgsConstructor
public class DemoController {
    private final RealtimeMessagingService messaging;

    @PostMapping("/api/demo/notify")
    public void notifyDemo(@RequestBody DemoPayload payload) {
        // 단일 노드 또는 Redis 미사용 시: 바로 로컬 브로커로 전송
        messaging.sendToTopic("/demo", payload);       // 클라이언트 구독: /topic/demo

        // 다중 노드 + Redis Pub/Sub 사용 시: Redis → 각 노드 Subscriber가 로컬 전송
        // messaging.publish(RealtimeEnvelopes.toTopic("/demo", payload));

        // 1:1 전송 예시
        // messaging.sendToUser("userId", "/demo", payload);
    }
}
```
3. 클라이언트(STOMP) 예시:
   ```js
   import SockJS from 'sockjs-client';
   import Stomp from 'stompjs';

   const socket = new SockJS('/ws');
   const stomp = Stomp.over(socket);
   stomp.connect({}, () => {
     stomp.subscribe('/topic/demo', (msg) => {
       console.log('demo message', JSON.parse(msg.body));
     });
     stomp.send('/app/demo', {}, JSON.stringify({ hello: 'world' }));
   });
   ```

## 메일 모듈 연동
- 메일 모듈은 완료 알림 전송 방식을 `SSE` 또는 `STOMP` 중 선택 가능.
- STOMP 선택 시 realtime 모듈의 `RealtimeMessagingService` 를 사용해 토픽으로 전송한다.

### 설정 예시 (메일)
```yaml
studio:
  features:
    mail:
      web:
        notify: stomp          # sse | stomp
        stomp-destination: /mail-sync
```

### 클라이언트 구독 예시
- 예: `topic-prefix=/topic` 이면 구독 경로는 `/topic/mail-sync`.

### Vue(STOMP) 수신 예시
```js
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

const socket = new SockJS('/ws');
const stomp = Stomp.over(socket);

stomp.connect({}, () => {
  stomp.subscribe('/topic/mail-sync', (message) => {
    const payload = JSON.parse(message.body);
    // payload.log 에 MailSyncLogDto 가 들어 있음
    console.log('mail sync completed', payload.log);
  });
});
```

## 제약사항 / 사용 규칙
- **Payload는 DTO만**: 모든 페이로드 DTO는 `RealtimePayload` 를 구현해야 하며, 엔티티/도메인 모델 직접 전송은 금지.
- **Destination 규칙**: 토픽은 `/topic/*`, 유저 대상은 `/user/*` 경로 사용을 권장.
- **Redis 사용 시**: `publish()` 로만 전송해야 중복 없이 모든 노드에 전달된다. `sendToTopic/sendToUser` 는 단일 노드 전용.
- **JWT 핸드셰이크**: `studio.realtime.jwt-enabled=true` 일 때 Authorization Bearer 토큰이 없으면 익명 Principal이 생성되며, `reject-anonymous=true` 설정 시 연결이 거부된다.
- **클라이언트 식별**: USER 메시지는 `userId` 기준이므로 서버의 Principal 추출 정책(예: JWT claim)이 일관돼야 한다.

## 운영 측면 주의/위험성 예시

| 위험 시나리오 | 영향 | 완화 방안 |
| --- | --- | --- |
| Redis 채널 오용으로 중복 전송 | 같은 메시지가 여러 번 브로드캐스트 | 다중 노드에서는 `publish()`만 사용, 로컬 send 금지 |
| Payload가 DTO가 아닌 엔티티/Proxy | 직렬화 실패, 민감정보 노출 | `RealtimePayload` 구현 DTO만 허용, 전송 전 매핑 |
| 익명 연결 허용 상태에서 민감 이벤트 노출 | 인증 없이 수신 가능 | `reject-anonymous=true` 설정 또는 토픽 권한 제어 |
| Redis 장애 | 멀티노드 간 메시지 손실 | 헬스체크 + 장애 시 fallback: 로컬 send 사용 안내 |
| 잘못된 destination 설계 | 클라이언트 구독 실패 | `/topic/...`·`/user/...` 네이밍 규칙 문서화 및 테스트 |

> 운영 팁: 운영/스테이징 환경마다 `studio.realtime.*` 값을 별도 관리하고, Redis Pub/Sub는 모니터링 대상(메시지 처리량, 실패 로그)으로 등록하세요.
