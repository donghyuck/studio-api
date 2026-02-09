# studio-platform-user

User platform **contracts** module. This module defines public DTOs, controller interfaces, service interfaces,
and error types that other modules depend on. Keep this module stable and focused on API contracts.

## Scope
- Web DTOs (request/response)
- Service interfaces
- Controller API interfaces
- Error types

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
