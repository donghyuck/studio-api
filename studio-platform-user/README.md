# studio-platform-user

User platform **contracts** module. This module defines public DTOs, controller interfaces, service interfaces,
and error types that other modules depend on. Keep this module stable and focused on API contracts.

## Scope
- Web DTOs (request/response)
- Service interfaces
- Controller API interfaces
- Error types

## Company Member / Permission Contract
Company는 enterprise tenant/account의 기본 경계이며 기존 `ApplicationCompany*` 계약을 확장한다.

- `ApplicationCompanyService`: 기존 Company CRUD와 archive 흐름을 제공한다. 기존 `delete()`는 호환용으로 유지한다.
- `ApplicationCompanyMemberService`: Company별 member role을 관리한다.
- `ApplicationCompanyPermissionService`: Company role 기반 action check를 제공한다.
- `CompanyRole`: `MEMBER`, `BILLING_ADMIN`, `ADMIN`, `OWNER`

기본 action은 `company.read`, `company.update`, `company.archive`, `company.member.*`,
`company.permission.*`, `company.workspace.*`, `company.billing.*`이다.
Company 권한은 Workspace 콘텐츠 권한을 대체하지 않으며, Workspace/Wiki 콘텐츠 접근은 후속 Workspace 권한 통합에서 별도로 판정한다.

Company management endpoint는 endpoint-level `features:company/*` 권한을 먼저 적용한 뒤 Company 객체 권한을 검사한다.
객체 권한 검사는 인증 principal을 `IdentityService`로 userId에 매핑할 수 있어야 하며,
`IdentityService`가 없거나 actor를 해석할 수 없는 경우 객체 단위 조회/수정/member/permission endpoint는 fail-closed로 거부한다.
전체 Company 목록 조회는 교차 tenant 메타데이터 노출을 막기 위해 `features:company/admin` 권한만 허용한다.

## Company Join Request Contract
Company 멤버 가입은 멤버 키 발급 후 가입 요청을 승인/거절하는 흐름을 사용한다.

- `ApplicationCompanyJoinRequestService`: 멤버 키 생성, 가입 요청 생성, 관리자 조회/승인/거절을 제공한다.
- 멤버 키는 생성 응답에서만 평문 `memberKey`로 반환하며 저장소에는 `SHA-256(memberKey)` hash만 저장한다.
- Company member 관리자는 자신의 Company role보다 높은 role의 멤버 키를 발급할 수 없다. 플랫폼 관리자는 관리용 endpoint에서 이 제한을 우회할 수 있다.
- Company member role 변경/삭제는 마지막 `OWNER`를 제거하지 못하도록 거부한다.
- 가입 요청은 `PENDING`, `APPROVED`, `REJECTED` 상태를 가진다.
- 가입 요청은 인증 사용자 경로인 `/api/self/company-join-requests`에서 생성한다.
- email만 받는 비로그인 가입 요청은 소유권 증명 없이 기존 계정에 안전하게 연결할 수 없으므로 이번 범위에서 노출하지 않는다.
- 승인 시 `ApplicationCompanyMemberService.addMember(...)`를 통해 Company member를 생성한다.
- `maxUses`는 요청 생성 시점이 아니라 승인 시점에 차감된다.
- `maxUses` 한도 계산은 승인 완료 건과 대기 중인 요청을 함께 반영해 과도한 대기 요청을 제한한다.
- 이미 승인/거절된 요청은 다시 처리할 수 없으며 `409 Conflict`로 거부된다.

기본 API 계약:

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/mgmt/companies/{companyId}/member-keys` | Company 관리자 멤버 키 생성 |
| `POST` | `/api/self/company-join-requests` | 로그인 사용자 가입 요청 생성 |
| `GET` | `/api/mgmt/companies/{companyId}/member-join-requests` | Company 관리자 가입 요청 목록 조회 |
| `POST` | `/api/mgmt/companies/{companyId}/member-join-requests/{requestId}/approve` | 가입 요청 승인 |
| `POST` | `/api/mgmt/companies/{companyId}/member-join-requests/{requestId}/reject` | 가입 요청 거절 |

가입 요청 관리 API는 신청자 email/message를 포함하므로 `company.member.manage` 객체 권한을 추가로 검사한다.
멤버 키는 로그, 감사 응답, 목록 응답에 저장하거나 노출하지 않고 생성 응답에서만 전달해야 한다.
`POST /api/self/company-join-requests`는 인증 사용자 경로이므로 전역 `/api/**` 인증 필터에서 보호되어야 한다.

## Self Profile API (Contract)
Base path is configurable, default is `/api/self`.

### Auth
- Requires authentication (JWT/session).

### GET /api/self
Returns the authenticated user's profile.

Response: `ApiResponse<MeProfileDto>`

## Password Policy Guide
Password policy is defined by configuration and enforced on:
- **Self password change**: `PUT /api/self/password`
- **Admin reset**: `POST /api/mgmt/users/{id}/password`
- **Admin delete**: `DELETE /api/mgmt/users/{id}` removes the user through the configured `ApplicationUserService` implementation.

### Policy Config (YAML)
```yaml
studio:
  features:
    user:
      password-policy:
        min-length: 8
        max-length: 20
        require-upper: false
        require-lower: false
        require-digit: false
        require-special: false
        allowed-specials: "!@#$%^&*"
        allow-whitespace: false
```

### Policy Config (YAML, Realistic Example)
```yaml
studio:
  features:
    user:
      password-policy:
        min-length: 12
        max-length: 64
        require-upper: true
        require-lower: true
        require-digit: true
        require-special: true
        allowed-specials: "!@#$%^&*"
        allow-whitespace: false
```

### Default Policy (When Not Configured)
- min-length: **8**
- max-length: **20**
- require-upper/lower/digit/special: **false**
- allowed-specials: `!@#$%^&*` (only relevant when require-special is true)
- allow-whitespace: **false**

### Policy API
- `GET /api/mgmt/users/password-policy` (admin UI)
- `GET /api/self/password-policy` (self UI)
- `GET /api/public/auth/password-policy` (pre-login UI)

Response: `ApiResponse<PasswordPolicyDto>`

### Policy Errors
- `error.user.password.policy` → HTTP 400  
  `detail` contains localized reason (e.g., “최소 8자 이상…”)

### PATCH /api/self
Partial update. Only provided fields are applied.

Request: `MeProfilePatchRequest`
- `name?: string` (max 100)
- `email?: string` (email, max 255)
- `nameVisible?: boolean`
- `emailVisible?: boolean`
- `firstName?: string` (max 100)
- `lastName?: string` (max 100)
- `properties?: Map<String, String>`

Response: `ApiResponse<MeProfileDto>`

### PUT /api/self
Full update. Missing required fields are rejected (400).

Request: `MeProfilePutRequest`
- `name` (required, max 100)
- `email` (required, email, max 255)
- `nameVisible` (required)
- `emailVisible` (required)
- `firstName?: string` (max 100)
- `lastName?: string` (max 100)
- `properties?: Map<String, String>`

Response: `ApiResponse<MeProfileDto>`

### PUT /api/self/password
Change password for the authenticated user.

Request: `MePasswordChangeRequest`
- `currentPassword` (required)
- `newPassword` (required)

Response: `ApiResponse<Void>` (OK only)

### GET /api/mgmt/users/password-policy
Returns current password policy configuration for admin UI.

Response: `ApiResponse<PasswordPolicyDto>`

### DELETE /api/mgmt/users/{id}
Deletes a user from the admin API.

Auth: `features:user` admin permission

Response: `204 No Content`

## Example Requests

### PATCH
```http
PATCH /api/self
Content-Type: application/json
Authorization: Bearer <token>
```
```json
{
  "name": "홍길동",
  "emailVisible": false,
  "properties": {
    "profile.title": "Engineer"
  }
}
```

### PUT
```http
PUT /api/self
Content-Type: application/json
Authorization: Bearer <token>
```
```json
{
  "name": "홍길동",
  "email": "hong@example.com",
  "nameVisible": true,
  "emailVisible": false,
  "properties": {
    "profile.title": "Engineer"
  }
}
```

### PUT /api/self/password
```http
PUT /api/self/password
Content-Type: application/json
Authorization: Bearer <token>
```
```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password"
}
```

## Example Response
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "username": "hong",
    "name": "홍길동",
    "email": "hong@example.com",
    "enabled": true,
    "roles": ["ROLE_USER"],
    "createdAt": "2026-02-01T10:00:00Z",
    "updatedAt": "2026-02-02T11:00:00Z"
  }
}
```

## Error Types
- `error.user.already.exists.email` → HTTP 409
- `error.user.not.found.username` → HTTP 404
- `error.user.password.policy` → HTTP 400
- Validation errors → HTTP 400

## Customization Guideline
If you need a different data model or policy, implement it in
`studio-platform-user-default` (or a project-specific module), while keeping
this module's contracts stable.

## 대응 스타터
이 모듈(계약)과 `studio-platform-user-default`(기본 구현)의 자동 구성은
`starter/studio-platform-starter-user`가 담당한다.

```kotlin
implementation(project(":starter:studio-platform-starter-user"))
// 기본 구현 사용 시
implementation(project(":studio-platform-user-default"))
```

스타터 상세 설정(패키지 스캔, 엔드포인트 토글, 영속성 타입)은
`starter/studio-platform-starter-user/README.md`를 참고한다.
