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
- Validation errors → HTTP 400

## Customization Guideline
If you need a different data model or policy, implement it in
`studio-platform-user-default` (or a project-specific module), while keeping
this module's contracts stable.
