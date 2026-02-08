# ADR 0001: STOMP + Redis Pub/Sub for Realtime Messaging

## 상태
승인

## 맥락
멀티 노드 환경에서 실시간 알림을 제공하려면 세션 공유 없이도 fan-out이 필요하다.
WebSocket은 세션 상태가 있어 확장이 어렵다.

## 결정
- 클라이언트와의 통신은 WebSocket + STOMP를 사용한다.
- 서버 간 fan-out은 Redis Pub/Sub으로 처리한다.
- DTO 페이로드만 전송하도록 제한한다.

## 결과
- 세션 공유 없이 수평 확장이 가능하다.
- 실시간 알림이 모듈 공통 서비스로 재사용 가능하다.
