# Studio Platform Identity

사용자 식별을 위한 공통 계약 모듈입니다. Spring Security 또는 특정 User 도메인에 직접
의존하지 않도록 설계되어 있습니다.

## 요약
userId/username/principal을 공통 타입으로 표현하는 계약 모듈이다.

## 설계
- 인증 프레임워크와 사용자 저장소를 직접 의존하지 않는다.
- `IdentityService`/`PrincipalResolver`로 경계를 나눈다.
- `UserKey`/`UserRef`로 식별 정보를 표준화한다.

## 사용법
- `IdentityService` 구현을 애플리케이션에서 제공
- 보안 어댑터가 `ApplicationPrincipal`/`PrincipalResolver`를 공급

## 확장 포인트
- `IdentityService` 구현 교체(DB/IAM)
- `PrincipalResolver` 구현 교체(Spring Security, 커스텀 세션 등)
- `UserRef` 확장(추가 속성)

## 설정
이 모듈은 자체 설정이 없고, 구현 모듈에서 빈 등록으로 활성화한다.

## 환경별 예시
- **dev**: 간단한 InMemory `IdentityService`로 빠른 테스트
- **stage/prod**: 실제 사용자 저장소 구현을 바인딩하고, PrincipalResolver를 보안 설정과 일치

## YAML 예시
```yaml
studio:
  identity:
    resolver: security
    user-source: database
```

## ADR
- `docs/adr/0001-identity-contract-separation.md`

## 목표
- 모든 모듈에서 동일한 타입으로 userId/username을 처리
- 사용자 시스템(DB/외부 IAM) 교체 시 계약(API)만 유지되면 영향 최소화
- 보안 구현체(UserDetails/Authentication 등)와 직접 결합하지 않음

## 주요 타입
- `UserKey` / `UserIdKey` / `UsernameKey`: 사용자 식별 키
- `UserRef`: userId/username/roles를 담는 참조 객체
- `IdentityService`: 사용자 식별/조회 계약
- `ApplicationPrincipal`: 애플리케이션 전역 principal 추상화
- `PrincipalResolver`: 현재 principal 조회 계약

## 사용 예시
```java
IdentityService identityService = ...;
identityService.resolve(new UserIdKey(100L))
    .ifPresent(ref -> {
        Long userId = ref.userId();
        String username = ref.username();
    });
```

## 구조
- `src/main/java`: 구현/계약
- `src/main/resources`: 리소스

## 구현 분리 원칙
Identity 모듈을 실제로 동작시키기 위해서는 두 종류의 구현 모듈이 필요합니다.
- 보안 어댑터 모듈: Spring Security/Authentication을 받아 `ApplicationPrincipal`/`PrincipalResolver`로 변환
- 사용자 시스템 구현 모듈: 사용자 저장소(DB/IAM)에서 `IdentityService`를 구현

이 분리를 통해 보안 프레임워크나 사용자 시스템이 바뀌어도 공통 계약은 유지됩니다.
