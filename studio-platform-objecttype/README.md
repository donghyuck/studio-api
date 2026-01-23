# Studio Platform ObjectType

objectType 레지스트리/정책/권한 라우팅을 위한 공통 구현 모듈입니다.  
`studio-platform`에 정의된 계약을 구현하며, DB 기반 레지스트리와 캐시, rebind/cleanup
라이프사이클을 제공합니다.

## 목표
- objectType 메타데이터를 단일 레지스트리로 관리
- 정책 평가와 권한 라우팅을 공통 방식으로 제공
- 캐시/리바인딩/정리 로직을 중앙화
- 보안/데이터 계층과의 결합 최소화

## 주요 타입 (계약/구현)
- 계약(`studio-platform`)
  - `ObjectTypeRegistry`
  - `ObjectPolicyResolver`
  - `ObjectRebindService`
- 구현(`studio-platform-objecttype`)
  - `CachedObjectTypeRegistry`
  - `JdbcObjectTypeRegistry` / `JpaObjectTypeRegistry`
  - `DefaultObjectTypeAdminService` / `DefaultObjectTypeRuntimeService`

## 사용 예시
```java
ObjectTypeRegistry registry = ...;
ObjectTypeMetadata meta = registry.getByType(1001);

AuthorizationRouter router = ...;
PermissionEvaluator evaluator = router.route(meta);
```

## 구조
- `src/main/java`: 구현
- `src/main/resources`: 리소스

## 설정 예시
```yaml
studio:
  features:
    objecttype:
      enabled: true
  objecttype:
    mode: yaml          # yaml | db
    yaml:
      resource: classpath:objecttype.yml
    db:
      enabled: false
    registry:
      cache:
        enabled: true
        ttl-seconds: 300
        max-size: 1000
    policy:
      cache:
        enabled: true
        ttl-seconds: 300
        max-size: 1000
```

## YAML 구조
```yaml
objecttypes:
  - type: 2001
    key: attachment
    name: Attachment
    domain: attachment
    status: active
    description: Default attachment object type
    policy:
      key: attachment-policy
      maxFileMb: 50
      allowedExt: "jpg,png,webp,pdf"
      allowedMime: "image/*,application/pdf"
      policyJson:
        customFlag: true
```

## 캐시/리바인드
- registry/policy 캐시는 `studio.objecttype.*.cache` 설정으로 활성화하며 TTL 기반이다.
- TTL이 `0` 또는 음수면 캐시를 우회하고 원본 구현을 그대로 호출한다.
- `ObjectRebindService.rebind()` 호출 시 캐시가 무효화되도록 연결되어 있다.

## 관리자(관리자용 API) 가이드
관리자 API는 ObjectType/Policy를 등록/수정하기 위한 엔드포인트다.
기본적으로 보호(인증/인가)는 외부에서 처리한다.

### 엔드포인트
- `GET    /api/mgmt/object-types` (domain/status/q, paging optional)
- `GET    /api/mgmt/object-types/{objectType}`
- `POST   /api/mgmt/object-types`
- `PUT    /api/mgmt/object-types/{objectType}` (upsert)
- `PATCH  /api/mgmt/object-types/{objectType}` (status/description/etc)
- `GET    /api/mgmt/object-types/{objectType}/policy`
- `PUT    /api/mgmt/object-types/{objectType}/policy` (upsert)
- `POST   /api/mgmt/object-types/reload` (cache evict/rebind)

### 요청 예시 (등록/수정)
```json
{
  "objectType": 2001,
  "code": "attachment",
  "name": "Attachment",
  "domain": "attachment",
  "status": "active",
  "description": "Default attachment object type",
  "updatedBy": "admin",
  "updatedById": 1,
  "createdBy": "admin",
  "createdById": 1
}
```

### 정책 upsert 예시
```json
{
  "maxFileMb": 50,
  "allowedExt": "jpg,png,webp,pdf",
  "allowedMime": "image/*,application/pdf",
  "policyJson": "{\"customFlag\":true}",
  "updatedBy": "admin",
  "updatedById": 1,
  "createdBy": "admin",
  "createdById": 1
}
```

### 검증/에러 코드
- `UNKNOWN_OBJECT_TYPE`
- `OBJECT_TYPE_DISABLED`
- `OBJECT_TYPE_DEPRECATED`
- `VALIDATION_ERROR`
- `POLICY_VIOLATION`
- `CONFLICT`

### 목록 응답
objecttype 관리 API는 목록을 `Page`가 아닌 `List`로 반환한다.

## 타 모듈 연동 가이드
다른 모듈은 아래 계약을 사용해 objectType과 정책을 조회/검증할 수 있다.

### 레지스트리 조회
```java
ObjectTypeRegistry registry = ...;
ObjectTypeMetadata meta = registry.findByType(2001).orElseThrow();
```

### 정책 조회
```java
ObjectPolicyResolver resolver = ...;
ObjectPolicy policy = resolver.resolve(meta).orElse(null);
```

### 런타임 검증(업로드 등)
```java
ObjectTypeRuntimeService runtime = ...;
runtime.validateUpload(2001, new ValidateUploadRequest("a.png", "image/png", 1024L));
```

## Vue 클라이언트 가이드
Vue에서는 axios/fetch로 관리자 API를 호출한다. 날짜는 ISO-8601 `OffsetDateTime`으로 내려온다.

### 목록 조회 예시
```js
import axios from "axios";

const { data } = await axios.get("/api/mgmt/object-types", {
  params: { domain: "attachment", status: "active", q: "" }
});
const list = data.data; // ApiResponse<T>의 data
```

### axios 공통 에러 처리 예시
```js
import axios from "axios";

const api = axios.create({ baseURL: "/api" });

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const detail = error?.response?.data?.detail;
    const code = error?.response?.data?.code;
    console.error("API error", code, detail);
    return Promise.reject(error);
  }
);

export default api;
```

### 날짜 표시 예시
```js
const createdAt = new Date(dto.createdAt); // ISO-8601
const display = createdAt.toLocaleString();
```

## OpenAPI 스타일 응답 스키마(요약)
```yaml
ObjectTypeDto:
  type: object
  properties:
    objectType: { type: integer }
    code: { type: string }
    name: { type: string }
    domain: { type: string }
    status: { type: string, enum: [active, deprecated, disabled] }
    description: { type: string, nullable: true }
    createdBy: { type: string }
    createdById: { type: integer, format: int64 }
    createdAt: { type: string, format: date-time }
    updatedBy: { type: string }
    updatedById: { type: integer, format: int64 }
    updatedAt: { type: string, format: date-time }

ObjectTypePolicyDto:
  type: object
  properties:
    objectType: { type: integer }
    maxFileMb: { type: integer, nullable: true }
    allowedExt: { type: string, nullable: true }
    allowedMime: { type: string, nullable: true }
    policyJson: { type: string, nullable: true }
    createdBy: { type: string }
    createdById: { type: integer, format: int64 }
    createdAt: { type: string, format: date-time }
    updatedBy: { type: string }
    updatedById: { type: integer, format: int64 }
    updatedAt: { type: string, format: date-time }
```

## 구현 분리 원칙
- 계약 인터페이스는 `studio-platform`
- objectType 구현은 `studio-platform-objecttype`
- DB/Repository 인프라는 `studio-platform-data`

의존 방향은 `platform → objecttype → data`로 유지합니다.
