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

## Company Member / Permission
기존 `TB_APPLICATION_COMPANY`를 tenant/account 경계로 확장한다.

- `V302__extend_company_and_create_company_members.sql`에서 `STATUS`, `ARCHIVED_AT`, `ARCHIVED_BY`를 추가한다.
- `TB_APPLICATION_COMPANY_MEMBERS`는 `(COMPANY_ID, USER_ID)`를 primary key로 사용한다.
- Company 생성 시 actor user id가 제공되면 생성자를 `OWNER` member로 등록한다.
- 신규 관리 흐름은 archive를 사용하고, 기존 `ApplicationCompanyService.delete()`는 호환용으로 유지한다.

Company 권한은 tenant 관리용 기반이며 Workspace 콘텐츠 권한의 1차 기준은 기존 Workspace role이다.

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

## 대응 스타터
이 모듈의 엔티티/리포지토리 스캔과 서비스 빈 등록은
`starter/studio-platform-starter-user`가 담당한다.

```kotlin
implementation(project(":starter:studio-platform-starter-user"))
implementation(project(":studio-platform-user-default"))
```

스타터 상세 설정(패키지 스캔, 엔드포인트 토글, 영속성 타입)은
`starter/studio-platform-starter-user/README.md`를 참고한다.
