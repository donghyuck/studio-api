# ADR 0001: Identity Contract Separation

## 상태
승인

## 맥락
사용자 시스템(DB/IAM)이나 인증 프레임워크가 변경될 수 있다.
각 모듈이 Spring Security나 특정 User 엔티티에 직접 의존하면 교체 비용이 크다.

## 결정
사용자 식별은 계약 모듈에서 `IdentityService`/`PrincipalResolver`로 추상화한다.
보안 어댑터와 사용자 저장소 구현은 별도 모듈에서 제공한다.

## 결과
- 인증 프레임워크 교체 시 영향 범위를 최소화한다.
- 모듈 간 결합도를 낮춰 재사용성을 높인다.
