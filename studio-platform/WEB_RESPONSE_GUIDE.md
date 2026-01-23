# 웹 응답 가이드

이 문서는 플랫폼 웹 레이어의 표준 응답 형식과 오류 처리 방식을 설명한다.

## 성공 응답
성공 응답은 `ApiResponse<T>`를 사용한다.

```json
{
  "success": true,
  "data": { "...": "..." },
  "message": "optional message",
  "meta": { "page": 1, "size": 20 }
}
```

핵심 포인트:
- `success` 기본값은 `true`
- `data`는 실제 페이로드
- `message`/`meta`는 선택 값

예시(컨트롤러):
```java
return ResponseEntity.ok(ApiResponse.ok(dto));
```

## 오류 응답 (ProblemDetails)
오류 응답은 RFC7807 스타일의 `ProblemDetails`를 사용하며,
Content-Type은 `application/problem+json`이다.
공통 필드는 `AbstractExceptionHandler`에서 구성하고 `GlobalExceptionHandler`가 최종 생성한다.

```json
{
  "type": "urn:error:error.user.not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "User not found",
  "instance": "/api/users/1",
  "code": "error.user.not-found",
  "traceId": "abcd-1234",
  "timestamp": "2026-01-22T10:45:12+09:00",
  "locale": "en",
  "violations": [
    {
      "field": "name",
      "rejectedValue": "***",
      "code": "NotBlank",
      "message": "must not be blank"
    }
  ]
}
```

### 표준 필드
- `type`: URN 또는 `about:blank`
- `title`: HTTP Reason Phrase
- `status`: HTTP 상태 코드
- `detail`: 지역화된 메시지
- `instance`: 요청 경로
- `code`: 내부/i18n 에러 코드
- `traceId`: MDC traceId (존재 시)
- `timestamp`: 응답 시각
- `locale`: 현재 로케일
- `violations`: 검증 오류 목록(선택)

## 오류 메시지 처리
`GlobalExceptionHandler`는 `ErrorType`과 `I18n`을 사용해 메시지를 구성한다.
- `ErrorType.getId()`를 i18n 키로 사용
- `I18nUtils.safeGet(...)`로 메시지 해석
- 키가 없으면 안전한 기본 메시지 사용

민감 필드(예: `password`, `token`)는 검증 오류에서 마스킹된다.

## 검증 오류
검증 실패 시 `violations`를 포함한다:
```json
{
  "type": "urn:error:validation",
  "status": 400,
  "detail": "Validation failed",
  "violations": [
    { "field": "email", "code": "Email", "message": "must be a well-formed email address" }
  ]
}
```

## 목록 응답(Page)
목록 조회는 기본적으로 Spring `Page`를 사용하며 `ApiResponse`로 감싼다.
다만 데이터 크기가 제한적이거나 전체 조회가 기본인 경우에는 `List` 응답을 허용한다.
예: `objecttype` 관리 API는 목록을 `List`로 반환한다.
`Page`를 사용하는 경우 `data` 필드에 Page 페이로드(컨텐츠/총건수 등)가 포함된다.

```json
{
  "success": true,
  "data": {
    "content": [ { "...": "..." } ],
    "totalElements": 120,
    "totalPages": 6,
    "size": 20,
    "number": 0
  }
}
```

## Content-Type
모든 오류 응답은 아래 Content-Type을 사용한다.
```
application/problem+json; charset=UTF-8
```

## 날짜/시간 표준(OffsetDateTime)
DTO의 날짜/시간 필드는 `OffsetDateTime`을 표준으로 사용한다.
응답 형식은 ISO-8601이며 항상 offset을 포함한다.

예시:
```
2026-01-22T09:15:30+09:00
```

### Vue/JS에서 사용
- `new Date(value)`로 파싱하면 브라우저 로컬 시간으로 변환된다.
- 권장: `dayjs(value)` 또는 `luxon`으로 포맷/타임존 처리

예시(Vue):
```js
const createdAt = new Date(dto.createdAt);
const display = createdAt.toLocaleString();
```

### JSP에서 사용
- 문자열 그대로 출력하거나, JSTL로 포맷 처리

예시(JSP):
```jsp
<c:out value="${dto.createdAt}" />
```

## 권장 사용법
- 성공: `ApiResponse.ok(...)`
- 오류: `ErrorType`과 함께 `PlatformException`/`PlatformRuntimeException` 사용
- 검증: `@Valid` 사용, 글로벌 핸들러에서 처리

## 컨트롤러 사용 예시
아래는 성공/목록/메시지/오류 처리 흐름을 모두 포함한 예시다.

```java
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplatesService templatesService;

    public TemplateController(TemplatesService templatesService) {
        this.templatesService = templatesService;
    }

    // 성공 응답 + 메시지
    @Message("api.template.created")
    @PostMapping
    public ResponseEntity<ApiResponse<TemplateDto>> create(@Valid @RequestBody TemplateRequest request) {
        Template created = templatesService.create(request);
        return ResponseEntity.ok(ApiResponse.ok(TemplateDto.from(created)));
    }

    // 목록 응답(Page)
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TemplateDto>>> list(Pageable pageable) {
        Page<TemplateDto> page = templatesService.page(pageable).map(TemplateDto::from);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // 오류 처리 (코드 기반 + i18n 메시지)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateDto>> get(@PathVariable long id) {
        Template t = templatesService.findById(id)
                .orElseThrow(() -> new PlatformRuntimeException(TemplateError.NOT_FOUND, id));
        return ResponseEntity.ok(ApiResponse.ok(TemplateDto.from(t)));
    }
}
```

`messages_*.properties` 예시:
```
api.template.created=템플릿이 생성되었습니다.
error.template.not-found=템플릿을 찾을 수 없습니다. (id={0})
```

## 컨트롤러에서 Message 어노테이션 사용
컨트롤러에서 `@Message` 어노테이션을 사용하면 `messages_*.properties`에 등록된 메시지를
가져와 응답에 포함할 수 있다. (예: 성공 메시지, 사용자 안내 문구 등)

```java
@Message("api.template.created")
@PostMapping
public ResponseEntity<ApiResponse<TemplateDto>> create(...) {
    return ResponseEntity.ok(ApiResponse.ok(dto));
}
```

`messages_*.properties` 예시:
```
api.template.created=템플릿이 생성되었습니다.
```

## 코드 기반 오류 클래스 + 메시지 처리
오류는 `ErrorType` 기반의 코드 정의와 `messages_*.properties`를 함께 사용한다.
`GlobalExceptionHandler`는 `ErrorType.getId()` 값을 i18n 키로 사용해 메시지를 조회한다.

흐름:
- 예외 발생: `PlatformException` 또는 `PlatformRuntimeException`에 `ErrorType` 포함
- `GlobalExceptionHandler`가 `ErrorType.getId()`를 키로 메시지 조회
- 조회된 메시지를 `ProblemDetails.detail`에 포함

요약:
- **에러 코드 정의는 코드 기반**
- **메시지는 properties 기반(i18n)**

## 메시지 기능의 기본 구성
메시지 기능은 공통 `I18n` 서비스에 의존한다. 기본 구성에서는 i18n 자동구성이
MessageSource를 만들고 `messages_*.properties`를 통합해 제공한다.
리소스 위치는 프로젝트 구성에 따라 `studio-platform-data` 또는 각 모듈의 `i18n` 디렉터리에
둘 수 있으며, `I18n` 서비스가 이를 일관되게 조회한다.
