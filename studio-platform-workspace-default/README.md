# Studio Platform Workspace Default

`studio-platform-workspace` 계약의 JPA-only 기본 구현입니다. v1은 workspace tree와 member role 기반 effective permission, parent 변경 기반 subtree move, nullable Company scope를 다루며 Wiki, page-level ACL, custom role, deny override, hard delete는 포함하지 않습니다.

## 저장 모델
- `TB_PLATFORM_WORKSPACE`: workspace 본문, nullable `companyId`, `parentId`, immutable `slug`, materialized `path`, archive 상태
- `TB_PLATFORM_WORKSPACE_CLOSURE`: ancestor/descendant closure table
- `TB_PLATFORM_WORKSPACE_MEMBER`: workspace별 direct member role

root 생성 시 self closure와 creator `OWNER` member가 자동 생성됩니다. child 생성 시 parent ancestor closure를 복사하고 self closure를 추가합니다.

root 생성은 `companyId`를 받을 수 있습니다. child workspace는 parent의 `companyId`를 상속합니다. `studio.features.workspace.company-required=true`를 설정하면 companyId 없는 root 생성은 거부됩니다. company scope가 DB 제약으로 강제되는 운영 환경에서는 `studio.features.workspace.company-scope-enforced=true`를 함께 켜서 root slug 중복 체크를 company scope로 전환합니다.

parent 변경 시 대상 subtree의 `parentId`, `rootId`, `path`, `depth`와 closure row를 서버에서 재계산합니다. `newParentId=null`은 root 이동으로 처리하며, 자기 자신 또는 descendant 아래로 이동하는 순환 구조는 거부합니다.

## Package structure
구현 패키지는 `domain / application / infrastructure / web` 기준으로 재배치되었습니다. 이 변경은 breaking rename이며 기존
`studio.one.platform.workspace.service.impl`, `persistence.jpa`, `persistence.mybatis`, `web.dto` wrapper는 제공하지 않습니다.

- `application.service`: JPA 기반 workspace tree/member/permission usecase 구현과 설정 helper
- `application.error`: workspace 기본 구현에서 사용하는 platform exception/error type
- `infrastructure.persistence.jpa`: JPA entity/repository 구현
- `web.controller`: 사용자용/관리용 HTTP adapter
- `web.dto.request`, `web.dto.response`: request/response DTO

## 권한 규칙
- effective role은 ancestor direct role 중 가장 높은 role입니다.
- `PRIVATE` workspace는 role이 없으면 접근을 거부합니다.
- `INTERNAL`/`PUBLIC` workspace는 인증 사용자에게 implicit `VIEWER`를 부여합니다.
- company-scoped workspace에서는 visibility만으로 implicit `VIEWER`를 부여하지 않습니다. `studio.workspace.permission.company-owner-override-enabled=true`일 때 Workspace role이 부족한 Company `OWNER`만 Workspace `OWNER`급 override를 받을 수 있습니다. 이 설정은 `ApplicationCompanyMemberService` bean을 전제로 하며, bean이 없으면 starter가 fail-fast 처리합니다.
- Company `ADMIN`은 tenant 관리자 역할이며 private workspace/wiki content super-reader가 아닙니다.
- archived workspace는 read/tree/member/permission read 계열과 `workspace.activate`만 허용하고 그 외 mutation은 거부합니다.
- 관리용 컨트롤러는 platform admin context로 service를 호출하지만, 사용자용 컨트롤러는 workspace permission을 우회하지 않습니다.

## Company scope enforcement

company scope가 강제된 운영 환경에서는 `TB_PLATFORM_WORKSPACE.COMPANY_ID`가 필수 컬럼이고 기존 전역 `PATH`/`PARENT_ID+SLUG` unique 제약은 company-scoped unique 제약으로 대체됩니다.

- PostgreSQL: `(COMPANY_ID, PATH)`, `(COMPANY_ID, PARENT_ID, SLUG)`, root 전용 partial unique `(COMPANY_ID, SLUG) WHERE PARENT_ID IS NULL`
- MySQL/MariaDB: generated `PARENT_KEY = COALESCE(PARENT_ID, 0)`와 `(COMPANY_ID, PARENT_KEY, SLUG)` unique

`studio.features.workspace.company-scope-enforced=true`는 `studio.features.workspace.company-required=true`와 함께만 사용할 수 있습니다. 설정을 켜면 root slug 중복 검사가 company scope로 동작하며, 필요한 DB 제약이 없으면 애플리케이션이 기동 단계에서 실패합니다.

## API
웹 컨트롤러는 starter에서 `studio.features.workspace.web.enabled=true`일 때 등록됩니다.

사용자용 기본 경로는 `/api/workspaces`이며 익명 공개 API가 아닙니다. 사용자용 `POST /api/workspaces`는 company-scoped root 생성을 허용하지 않습니다. company-scoped root 생성은 관리용 `POST /api/mgmt/workspaces`에서 `companyId`를 전달해야 합니다.

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
- `POST /api/workspaces/{workspaceId}/activate`
- `GET /api/workspaces/{workspaceId}/members`
- `GET /api/workspaces/{workspaceId}/members/effective`
- `POST /api/workspaces/{workspaceId}/members`
- `PUT /api/workspaces/{workspaceId}/members/{userId}`
- `DELETE /api/workspaces/{workspaceId}/members/{userId}`
- `GET /api/workspaces/{workspaceId}/permissions/me`
- `GET /api/workspaces/{workspaceId}/permissions/actions`

관리용 기본 경로는 `/api/mgmt/workspaces`이며 같은 endpoint shape를 제공합니다. 관리용 API는 `features:workspace/manage` 또는 platform `ADMIN` role을 요구합니다.

Archive/activate request body는 선택 사항이며 `cascade`를 지원합니다.

```json
{
  "cascade": true
}
```

`archive`는 `workspace.archive` 권한을 요구합니다. 대상 workspace에 활성 descendant가 있으면 `cascade=true`가 필요하며, `cascade=false` 또는 body 생략 시 `409 Conflict`로 거부합니다. `cascade=true`는 대상과 모든 활성 descendant를 함께 archived 상태로 변경합니다.

`activate`는 `workspace.activate` 권한을 요구하며 성공 시 `WorkspaceRef`를 반환합니다. 이미 활성 상태인 workspace 활성화는 idempotent하게 현재 `WorkspaceRef`를 반환합니다. archived ancestor 아래의 child를 단독 활성화하는 요청은 `409 Conflict`로 거부합니다. parent subtree를 함께 복구하려면 parent workspace에 `cascade=true`로 요청합니다.

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

## MyBatis note

현재 production 구현은 JPA 기본 구현입니다. `src/test/resources/mybatis/workspace` 아래의 smoke test로
`classpath*:mybatis/**/*.xml` 로딩과 mapper scan 동작을 검증합니다. 실제 workspace read model/permission
query의 MyBatis 구현은 포함하지 않습니다.

## 검증
```bash
./gradlew :studio-platform-workspace-default:build
```
