# Studio Platform Workspace Default

`studio-platform-workspace` 계약의 JPA-only 기본 구현입니다. v1은 workspace tree와 member role 기반 effective permission, parent 변경 기반 subtree move, nullable Company scope를 다루며 Wiki, page-level ACL, custom role, deny override, hard delete는 포함하지 않습니다.

## 저장 모델
- `TB_PLATFORM_WORKSPACE`: workspace 본문, nullable `companyId`, `parentId`, immutable `slug`, materialized `path`, archive 상태
- `TB_PLATFORM_WORKSPACE_CLOSURE`: ancestor/descendant closure table
- `TB_PLATFORM_WORKSPACE_MEMBER`: workspace별 direct member role

root 생성 시 self closure와 creator `OWNER` member가 자동 생성됩니다. child 생성 시 parent ancestor closure를 복사하고 self closure를 추가합니다.

root 생성은 `companyId`를 받을 수 있습니다. `companyId`는 전환 단계에서 nullable이며, child workspace는 parent의 `companyId`를 상속합니다. `studio.features.workspace.company-required=true`를 설정하면 companyId 없는 root 생성은 거부됩니다.

parent 변경 시 대상 subtree의 `parentId`, `rootId`, `path`, `depth`와 closure row를 서버에서 재계산합니다. `newParentId=null`은 root 이동으로 처리하며, 자기 자신 또는 descendant 아래로 이동하는 순환 구조는 거부합니다.

## 권한 규칙
- effective role은 ancestor direct role 중 가장 높은 role입니다.
- `PRIVATE` workspace는 role이 없으면 접근을 거부합니다.
- `INTERNAL`/`PUBLIC` workspace는 인증 사용자에게 implicit `VIEWER`를 부여합니다.
- company-scoped workspace에서는 visibility만으로 implicit `VIEWER`를 부여하지 않습니다. `studio.workspace.permission.company-owner-override-enabled=true`일 때 Workspace role이 부족한 Company `OWNER`만 Workspace `OWNER`급 override를 받을 수 있습니다.
- Company `ADMIN`은 tenant 관리자 역할이며 private workspace/wiki content super-reader가 아닙니다.
- archived workspace는 read/tree/member/permission read 계열만 허용하고 mutation은 거부합니다.
- 관리용 컨트롤러는 platform admin context로 service를 호출하지만, 사용자용 컨트롤러는 workspace permission을 우회하지 않습니다.

## API
웹 컨트롤러는 starter에서 `studio.features.workspace.web.enabled=true`일 때 등록됩니다.

사용자용 기본 경로는 `/api/workspaces`이며 익명 공개 API가 아닙니다.

- `POST /api/workspaces`
- `POST /api/workspaces/{workspaceId}/children`
- `GET /api/workspaces/{workspaceId}`
- `GET /api/workspaces/by-path?companyId=...&path=...`
- `GET /api/workspaces/{workspaceId}/children`
- `GET /api/workspaces/{workspaceId}/ancestors`
- `GET /api/workspaces/{workspaceId}/descendants`
- `GET /api/workspaces/{workspaceId}/tree`
- `PATCH /api/workspaces/{workspaceId}`
- `PATCH /api/workspaces/{workspaceId}/parent`
- `POST /api/workspaces/{workspaceId}/archive`
- `GET /api/workspaces/{workspaceId}/members`
- `GET /api/workspaces/{workspaceId}/members/effective`
- `POST /api/workspaces/{workspaceId}/members`
- `PUT /api/workspaces/{workspaceId}/members/{userId}`
- `DELETE /api/workspaces/{workspaceId}/members/{userId}`
- `GET /api/workspaces/{workspaceId}/permissions/me`
- `GET /api/workspaces/{workspaceId}/permissions/actions`

관리용 기본 경로는 `/api/mgmt/workspaces`이며 같은 endpoint shape를 제공합니다. 관리용 API는 `features:workspace/manage` 또는 platform `ADMIN` role을 요구합니다.

member 목록 조회는 사용자용/관리용 경로 모두 서버 페이징 응답을 반환합니다.

- `GET {base-path}/{workspaceId}/members`
- `GET {base-path}/{workspaceId}/members/effective`

지원 query parameter는 `q`, `keyword`, `role`, `inherited`, `page`, `size`, `sort`입니다. `keyword`가 있으면 `q`보다 우선하며 사용자 `username`, `name`, `email`과 숫자 `userId`를 검색합니다. `role`은 `VIEWER`, `EDITOR`, `ADMIN`, `OWNER` 중 하나입니다. `inherited=true`는 inherited effective member만 조회할 때 사용하며 direct member 목록에서는 결과가 없습니다. 정렬은 `userId`, `workspaceId`, `role`을 지원하고 effective member 목록은 `inherited` 정렬도 지원합니다.

응답은 Spring Data `Page<WorkspaceMemberRef>` 형태이며 `content`, `totalElements`, `totalPages`, `number`, `size`를 포함합니다. `WorkspaceMemberRef` item은 `workspaceId`, `userId`, `role`, `inherited`를 포함합니다.

parent 변경 request body:

```json
{
  "newParentId": 10
}
```

`newParentId`를 `null`로 보내면 root workspace로 이동합니다. 사용자용 API는 이동 대상 workspace에 `workspace.update`, 새 parent가 있으면 parent에 `workspace.create` 권한을 요구합니다. 관리용 API는 기존 platform admin 우회 정책을 사용합니다. archived workspace는 이동할 수 없고 archived parent 아래로도 이동할 수 없습니다.

관리 화면의 첫 진입용 목록 API는 관리용 경로에만 제공됩니다.

- `GET /api/mgmt/workspaces`

지원 query parameter는 `q`, `companyId`, `parentId`, `rootOnly`, `archived`, `page`, `size`, `sort`입니다. `q`는 `name`, `slug`, `path`를 부분 검색하고, `rootOnly=true`는 root workspace만 반환합니다. 응답 item은 `id`, `companyId`, `parentId`, `rootId`, `name`, `slug`, `path`, `depth`, `visibility`, `archived`를 포함합니다.

## Schema
Flyway range는 `workspace` `1300-1399`입니다.

마이그레이션 위치:
- `src/main/resources/schema/workspace/postgres/V1300__create_workspace_tables.sql`
- `src/main/resources/schema/workspace/postgres/V1301__add_workspace_company_scope.sql`
- `src/main/resources/schema/workspace/mysql/V1300__create_workspace_tables.sql`
- `src/main/resources/schema/workspace/mysql/V1301__add_workspace_company_scope.sql`
- `src/main/resources/schema/workspace/mariadb/V1300__create_workspace_tables.sql`
- `src/main/resources/schema/workspace/mariadb/V1301__add_workspace_company_scope.sql`

## 검증
```bash
./gradlew :studio-platform-workspace-default:build
```
