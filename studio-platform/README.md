# studio-platform

## 요약
플랫폼 공통 계약/기본 구현 모듈이다. 웹 API 응답 규격(ApiResponse, ProblemDetails), 컨트롤러 네이밍 규칙, 도메인 이벤트 발행, objectType/권한 라우팅 계약, 공통 예외/DTO/유틸을 제공한다.

## 설계
- **웹 계약**: 성공 응답은 `ApiResponse<T>`, 오류는 RFC7807 스타일 `ProblemDetails`를 사용한다.
- **컨트롤러 분류**: `*MgmtController`, `*PublicController`, `*MeController`, `*Controller`로 보안 경계를 명시한다.
- **도메인 이벤트**: `RepositoryImpl`이 트랜잭션 커밋 이후 이벤트 발행을 추상화한다.
- **ObjectType 계약**: 레지스트리/정책/라우팅/리바인드 계약을 정의하고 구현은 다른 모듈에 위임한다.

## 사용법
- **ApiResponse**
  ```java
  return ResponseEntity.ok(ApiResponse.ok(dto));
  ```
- **ProblemDetails**: `AbstractExceptionHandler`/`GlobalExceptionHandler`를 통해 표준 오류 응답 사용.
- **컨트롤러 네이밍**: `WEB_API_DEVELOPMENT_GUIDE.md` 기준으로 suffix/prefix 일관성 유지.

## 확장 포인트
- `ErrorType`/i18n 메시지 확장 (새 오류 코드, 지역화)
- `ObjectTypeRegistry`/`ObjectPolicyResolver` 구현 교체
- 도메인 이벤트 퍼블리셔 확장(트랜잭션 후 처리 방식)

## 설정
이 모듈 자체는 런타임 설정이 거의 없고, 컨트롤러/보안 정책은 애플리케이션 레이어에서 구성한다.
세부 규칙은 `WEB_API_DEVELOPMENT_GUIDE.md` 참조.

## 환경별 예시
- **dev**: 예외 메시지/traceId 노출을 허용하고, `DEBUG` 로깅으로 문제 원인 추적
- **prod**: ProblemDetails의 `detail`은 사용자 메시지로 제한, traceId만 노출
- **common**: 컨트롤러 suffix/prefix 규칙을 SecurityFilterChain의 경로 규칙과 동기화

## YAML 예시
```yaml
logging:
  level:
    studio.one.platform.web: DEBUG
    studio.one.platform.error: DEBUG
```

## ADR
- `docs/adr/0001-web-api-contracts.md`

## 참고
- `WEB_API_DEVELOPMENT_GUIDE.md`: 응답 포맷/오류 처리/컨트롤러 규칙
