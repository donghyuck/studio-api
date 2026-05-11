# Studio Platform Workspace

Workspace 공통 계약 모듈입니다. 트리형 workspace, member role, effective permission 계산에 필요한 public contract만 제공합니다.

## 범위
- `WorkspaceTreeService`: root/child 생성, parent 변경, id/path 조회, children/tree/ancestors/descendants 조회, name/visibility 변경, archive/activate
- `WorkspaceMemberService`: direct/effective member 조회, pageable member 조회, member 추가/역할 변경/제거
- `WorkspacePermissionService`: role/visibility/ancestor 상속 기반 권한 계산
- `WorkspacePermissionContributor`: 모듈별 action과 기본 role mapping 확장

## 기본 모델
- role: `VIEWER`, `EDITOR`, `ADMIN`, `OWNER`
- visibility: `PRIVATE`, `INTERNAL`, `PUBLIC`
- slug는 v1에서 immutable이다.
- `WorkspaceRef.path`는 materialized path 표현이며 기본 구현은 closure table을 함께 사용한다.
- `WorkspaceRef.companyId`는 tenant scope 식별자다. company scope를 강제하는 운영 환경에서는 root 생성 시 필수이며, child workspace는 parent의 `companyId`를 상속한다.

## 기본 action
- `workspace.read`
- `workspace.create`
- `workspace.update`
- `workspace.archive`
- `workspace.activate`
- `workspace.tree.read`
- `workspace.member.read`
- `workspace.member.manage`
- `workspace.permission.read`
- `workspace.permission.manage`

## 기본 role mapping
- `VIEWER`: read/tree/member/permission read
- `EDITOR`: `VIEWER` + create/update
- `ADMIN`: `EDITOR` + archive/activate/member.manage/permission.manage
- `OWNER`: 전체 action

## 구현 모듈
기본 구현은 `studio-platform-workspace-default`에 있다. 이 계약 모듈은 JPA, web, Spring Boot auto-configuration을 직접 포함하지 않는다.

## Package structure
이 모듈은 `domain / application` 중심의 public contract 패키지를 사용한다. 이 변경은 breaking rename이며 기존
`studio.one.platform.workspace.model`, `permission`, `service`, `exception` wrapper는 제공하지 않는다.

- `domain.model`: `WorkspaceRef`, `WorkspaceRole`, `WorkspaceVisibility`, permission action/mapping 모델
- `application.command`: create/update/member/query/access context command
- `application.usecase`: `WorkspaceTreeService`, `WorkspaceMemberService`, `WorkspacePermissionService`, `WorkspacePermissionContributor`
- `application.error`: workspace error code와 exception

## Company scope
- root workspace는 `CreateRootWorkspaceCommand(companyId, ...)`로 company scope를 받을 수 있다.
- 기존 `CreateWorkspaceCommand` 기반 root 생성은 legacy 호환 경로로 유지되며 `companyId=null` root를 생성한다.
- company scope를 DB 제약까지 강제하는 운영 환경에서는 `studio.features.workspace.company-required=true`, `studio.features.workspace.company-scope-enforced=true`를 함께 사용하고 root 생성 시 `companyId`를 반드시 전달해야 한다.
- path 조회는 `getByPath(companyId, path, actor)`를 우선 사용한다. 기존 `getByPath(path, actor)`는 `company-required=false` 전환 기간의 legacy 조회 호환을 위해 유지하며, enforcement 이후에는 `companyId`를 반드시 전달해야 한다.
- 기본 구현은 Workspace direct/ancestor role을 우선 판정한다. `studio.workspace.permission.company-owner-override-enabled=true`일 때만 부족한 권한을 Company `OWNER`의 Workspace `OWNER`급 override로 보강한다. 이 설정은 `ApplicationCompanyMemberService` bean이 있어야 기동된다. Company `ADMIN`은 private workspace나 wiki content read 권한을 자동으로 얻지 않는다.
