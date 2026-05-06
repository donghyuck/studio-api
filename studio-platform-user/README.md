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
