# ADR 0001: Web API Contracts (ApiResponse + ProblemDetails + Controller Naming)

## 상태
승인

## 맥락
서비스가 늘어나면서 API 응답 형식과 컨트롤러 보안 경계가 불일치해 유지보수가 어려워졌다.
클라이언트/서버 모두에서 일관된 응답 처리와 보안 규칙 구성이 필요하다.

## 결정
- 성공 응답은 `ApiResponse<T>`로 통일한다.
- 오류 응답은 RFC7807 스타일 `ProblemDetails`로 통일한다.
- 컨트롤러는 기능/보안 경계가 드러나도록 suffix와 URL prefix 규칙을 따른다.

## 결과
- 클라이언트는 응답 처리 로직을 단일화할 수 있다.
- 보안 규칙을 URL prefix 기반으로 일관되게 구성할 수 있다.
- 신규 모듈이 추가돼도 문서/검증/로깅 패턴이 유지된다.

## 참고
- `WEB_API_DEVELOPMENT_GUIDE.md`
