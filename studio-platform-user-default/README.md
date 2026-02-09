# studio-platform-user-default

Default **implementation** of user management for the platform.
Project-specific customizations should be made here rather than in
`studio-platform-user`.

## Scope
- JPA/JDBC persistence
- Service implementations
- Controllers
- Mapping logic
- Policy decisions (validation, security, cache)

## Self Profile Update (Implementation Notes)
Implemented in:
- `UserMeController` (PATCH/PUT endpoints)
- `ApplicationUserServiceImpl` (update logic)

### Security & Policy
- **Email uniqueness** enforced on self update (409 on conflict).
- **Properties** update is **merge + whitelist**.
  - Keys must match `[A-Za-z0-9_.-]{1,100}`.
  - Keys starting with `security.`, `auth.`, `role.`, `admin.`, `permission.` are rejected.
- **Audit** events emitted on PATCH/PUT.

## Self Password Change (Implementation Notes)
Implemented in:
- `UserMeController` (PUT `/api/self/password`)
- `ApplicationUserServiceImpl` (change logic)

### Security & Policy
- **Current password required**; mismatch throws `BadCredentialsException`.
- **New password must differ** from current password.
- **Password policy enforced** via `PasswordPolicyValidator`.
- **Failed attempts/lock** are cleared on success.
- **Audit** action: `USER_PASSWORD_CHANGE`.

### Cache
Self update evicts:
- `users.byUserId`
- `users.byUsername`

If additional user caches exist, add them here.

## Properties Merge Semantics
- PATCH/PUT do **not** replace the entire properties map.
- Incoming keys are validated and merged into existing properties.
- If you need delete semantics, define a reserved value and handle it in
  `mergeSafeProperties`.

## Validation Behavior
- PUT requires: `name`, `email`, `nameVisible`, `emailVisible`
- PATCH accepts any subset; null fields are ignored

## Performance Notes
- PATCH/PUT do **not** load roles; response is profile-only fields
- If roles are required for clients, add a dedicated endpoint

## Error Mapping
- Duplicate email → `UserAlreadyExistsException.byEmail(...)` (HTTP 409)
- User not found → `UserNotFoundException.of(username)` (HTTP 404)

## Customization Points
When new fields are added:
1) Update `ApplicationUser` entity.
2) Update JDBC/JPA mappers.
3) Update `ApplicationUserServiceImpl` patch/put mapping logic.
4) Update controller DTO mapping if the field should be returned.

If fields are **internal only**, keep DTO changes in this module only.
If fields are **API-visible**, update DTO contracts in `studio-platform-user`.

## Extension Pattern (Recommended)
If policy changes are frequent, extract:
- `UserSelfUpdatePolicy` (validation)
- `UserSelfUpdateMapper` (field application)
- `UserSelfUpdateHook` (before/after)
and inject them via Spring to override per project.
