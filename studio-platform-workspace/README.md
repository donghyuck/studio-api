# Studio Platform Workspace

Workspace 공통 계약 모듈입니다. 트리형 workspace, member role, effective permission 계산에 필요한 public contract만 제공합니다.

## 범위
- `WorkspaceTreeService`: root/child 생성, parent 변경, id/path 조회, children/tree/ancestors/descendants 조회, name/visibility 변경, archive
- `WorkspaceMemberService`: direct/effective member 조회, pageable member 조회, member 추가/역할 변경/제거
- `WorkspacePermissionService`: role/visibility/ancestor 상속 기반 권한 계산
- `WorkspacePermissionContributor`: 모듈별 action과 기본 role mapping 확장

## 기본 모델
- role: `VIEWER`, `EDITOR`, `ADMIN`, `OWNER`
- visibility: `PRIVATE`, `INTERNAL`, `PUBLIC`
- slug는 v1에서 immutable이다.
- `WorkspaceRef.path`는 materialized path 표현이며 기본 구현은 closure table을 함께 사용한다.
- `WorkspaceRef.companyId`는 tenant scope 식별자다. v1 전환 단계에서는 nullable이며, child workspace는 parent의 `companyId`를 상속한다.

## 기본 action
- `workspace.read`
- `workspace.create`
- `workspace.update`
- `workspace.archive`
- `workspace.tree.read`
- `workspace.member.read`
- `workspace.member.manage`
- `workspace.permission.read`
- `workspace.permission.manage`

## 기본 role mapping
- `VIEWER`: read/tree/member/permission read
- `EDITOR`: `VIEWER` + create/update
- `ADMIN`: `EDITOR` + archive/member.manage/permission.manage
- `OWNER`: 전체 action

## 구현 모듈
기본 구현은 `studio-platform-workspace-default`에 있다. 이 계약 모듈은 JPA, web, Spring Boot auto-configuration을 직접 포함하지 않는다.

## Company scope
- root workspace는 `CreateRootWorkspaceCommand(companyId, ...)`로 company scope를 받을 수 있다.
- 기존 `CreateWorkspaceCommand` 기반 root 생성은 legacy 호환 경로로 유지되며 `companyId=null` root를 생성한다.
- path 조회는 `getByPath(companyId, path, actor)`를 우선 사용한다. 기존 `getByPath(path, actor)`는 legacy 조회 호환을 위해 유지한다.
- Company 권한 override는 이 모듈에서 직접 판정하지 않으며 후속 Phase에서 `WorkspacePermissionService` 흐름에 연결한다.
