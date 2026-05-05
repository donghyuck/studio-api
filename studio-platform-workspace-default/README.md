# Studio Platform Workspace Default

`studio-platform-workspace` 계약의 JPA-only 기본 구현입니다. v1은 workspace tree와 member role 기반 effective permission만 다루며 Wiki, page-level ACL, custom role, deny override, hard delete, subtree move는 포함하지 않습니다.

## 저장 모델
- `TB_PLATFORM_WORKSPACE`: workspace 본문, `parentId`, immutable `slug`, materialized `path`, archive 상태
- `TB_PLATFORM_WORKSPACE_CLOSURE`: ancestor/descendant closure table
- `TB_PLATFORM_WORKSPACE_MEMBER`: workspace별 direct member role

root 생성 시 self closure와 creator `OWNER` member가 자동 생성됩니다. child 생성 시 parent ancestor closure를 복사하고 self closure를 추가합니다.

## 권한 규칙
- effective role은 ancestor direct role 중 가장 높은 role입니다.
- `PRIVATE` workspace는 role이 없으면 접근을 거부합니다.
- `INTERNAL`/`PUBLIC` workspace는 인증 사용자에게 implicit `VIEWER`를 부여합니다.
- archived workspace는 read/tree/member/permission read 계열만 허용하고 mutation은 거부합니다.
- 관리용 컨트롤러는 platform admin context로 service를 호출하지만, 사용자용 컨트롤러는 workspace permission을 우회하지 않습니다.

## API
웹 컨트롤러는 starter에서 `studio.features.workspace.web.enabled=true`일 때 등록됩니다.

사용자용 기본 경로는 `/api/workspaces`이며 익명 공개 API가 아닙니다.

- `POST /api/workspaces`
- `POST /api/workspaces/{workspaceId}/children`
- `GET /api/workspaces/{workspaceId}`
- `GET /api/workspaces/by-path?path=...`
- `GET /api/workspaces/{workspaceId}/children`
- `GET /api/workspaces/{workspaceId}/ancestors`
- `GET /api/workspaces/{workspaceId}/descendants`
- `GET /api/workspaces/{workspaceId}/tree`
- `PATCH /api/workspaces/{workspaceId}`
- `POST /api/workspaces/{workspaceId}/archive`
- `GET /api/workspaces/{workspaceId}/members`
- `GET /api/workspaces/{workspaceId}/members/effective`
- `POST /api/workspaces/{workspaceId}/members`
- `PUT /api/workspaces/{workspaceId}/members/{userId}`
- `DELETE /api/workspaces/{workspaceId}/members/{userId}`
- `GET /api/workspaces/{workspaceId}/permissions/me`
- `GET /api/workspaces/{workspaceId}/permissions/actions`

관리용 기본 경로는 `/api/mgmt/workspaces`이며 같은 endpoint shape를 제공합니다. 관리용 API는 `features:workspace/manage` 또는 platform `ADMIN` role을 요구합니다.

## Schema
Flyway range는 `workspace` `1300-1399`입니다.

마이그레이션 위치:
- `src/main/resources/schema/workspace/postgres/V1300__create_workspace_tables.sql`
- `src/main/resources/schema/workspace/mysql/V1300__create_workspace_tables.sql`
- `src/main/resources/schema/workspace/mariadb/V1300__create_workspace_tables.sql`

## 검증
```bash
./gradlew :studio-platform-workspace-default:build
```
