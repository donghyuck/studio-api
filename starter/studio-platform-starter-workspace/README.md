# Studio Platform Workspace Starter

Workspace 계약과 JPA 기본 구현을 Spring Boot auto-configuration으로 등록하는 starter입니다.

## 의존성
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-workspace"))
}
```

보안이 적용된 애플리케이션에서는 `PrincipalResolver`와 `endpointAuthz` 표현식 bean을 제공하는 security starter를 함께 사용합니다.

```kotlin
implementation(project(":starter:studio-platform-starter-security"))
```

## 설정
```yaml
studio:
  features:
    workspace:
      enabled: true
      persistence: jpa
      company-required: false
      company-scope-enforced: false
      web:
        enabled: true
        public-base-path: /api/workspaces
        mgmt-base-path: /api/mgmt/workspaces
  workspace:
    tree:
      max-depth: 10
      max-children-per-node: 200
    slug:
      max-length: 100
    permission:
      inherit-parent-role: true
      deny-override-enabled: false
      company-owner-override-enabled: false
```

`studio.features.workspace.persistence`는 v1에서 `jpa`만 지원합니다. 다른 값을 지정하면 기본 JPA service/repository가 등록되지 않습니다.

## API 경로
- `studio.features.workspace.web.public-base-path`: 로그인 사용자용 API. 기본값은 `/api/workspaces`이며 익명 공개 API가 아닙니다.
- `studio.features.workspace.web.mgmt-base-path`: 관리용 API. 기본값은 `/api/mgmt/workspaces`입니다.

관리용 API는 `features:workspace/manage` 또는 platform `ADMIN` role을 요구합니다. 사용자용 API는 endpoint auth 통과 후 service layer에서 workspace role/visibility permission을 다시 검사합니다.

Company scope가 있는 workspace는 Workspace role을 1차 권한 기준으로 사용합니다. `studio.workspace.permission.company-owner-override-enabled=true`일 때만 Workspace role이 부족한 Company `OWNER`가 Workspace `OWNER`급 override를 받으며, Company `ADMIN`은 private workspace나 wiki content read 권한을 자동으로 얻지 않습니다. 이 설정을 켜려면 `ApplicationCompanyMemberService` bean이 필요하며, 없으면 애플리케이션이 기동 단계에서 실패합니다.

관리 화면에서 전체 workspace 목록을 시작점으로 조회할 때는 `GET /api/mgmt/workspaces`를 사용합니다. `q`, `companyId`, `parentId`, `rootOnly`, `archived`, `page`, `size`, `sort` query parameter를 지원하며 기본 정렬은 `path ASC`입니다.

root workspace 생성 request는 선택적으로 `companyId`를 받을 수 있습니다. `company-required=false`가 기본값이라 기존 company 없는 root 생성은 유지되며, `company-required=true`에서는 root 생성 시 `companyId`가 필수입니다. 사용자용 `POST /api/workspaces`는 company-scoped root 생성을 허용하지 않으므로, company root 생성은 관리용 `POST /api/mgmt/workspaces`를 사용해야 합니다. child workspace는 parent의 `companyId`를 상속합니다. path 조회는 `GET {base-path}/by-path?companyId=10&path=acme/engineering`처럼 company scope를 함께 보낼 수 있습니다. 기존 `path` 단독 조회는 `company-required=false` 전환 기간의 legacy 호환 경로이며, enforcement 전에는 client가 `companyId`를 보내도록 먼저 전환해야 합니다.

`V1302__enforce_workspace_company_scope.sql`을 적용한 운영 환경은 DB가 `COMPANY_ID NOT NULL`과 company-scoped unique 제약을 강제합니다. 이 단계에서는 기존 데이터 backfill과 duplicate 검증을 완료한 뒤 `studio.features.workspace.company-required=true`, `studio.features.workspace.company-scope-enforced=true`를 함께 설정합니다. `company-scope-enforced=true`는 `company-required=true`와 함께만 사용할 수 있고, runtime root slug 중복 체크를 company scope로 전환합니다. V1302 schema가 없으면 애플리케이션은 기동 단계에서 실패합니다. 반대로 V1302 schema가 이미 적용된 DB에서 `company-scope-enforced=false`로 기동하는 것도 tenant-ambiguous `path` 조회를 막기 위해 실패합니다. `V1303__add_workspace_company_fk.sql`은 `TB_PLATFORM_WORKSPACE.COMPANY_ID`가 `TB_APPLICATION_COMPANY.COMPANY_ID`를 참조하도록 강제하므로 user schema migration을 먼저 적용하고 orphan workspace가 없는지 확인해야 합니다. 절차는 [workspace-company-scope-enforcement.md](../../docs/dev/workspace-company-scope-enforcement.md)를 참고합니다.

Workspace member 목록은 사용자용/관리용 경로 모두에서 서버 페이징으로 조회합니다.

- `GET {base-path}/{workspaceId}/members`
- `GET {base-path}/{workspaceId}/members/effective`

지원 query parameter는 `q`, `keyword`, `role`, `inherited`, `page`, `size`, `sort`입니다. `keyword`는 `q`보다 우선하며 숫자 `userId`와, `ApplicationUserService` bean이 있을 때 사용자 `username`, `name`, `email`을 검색합니다. direct member 목록은 항상 `inherited=false`이므로 `inherited=true` 요청 시 빈 페이지를 반환합니다. 응답은 `content`, `totalElements`, `totalPages`, `number`, `size`를 포함하는 `Page<WorkspaceMemberRef>` 형태입니다.

Workspace parent 변경은 사용자용/관리용 경로 모두에서 `PATCH {base-path}/{workspaceId}/parent`로 제공합니다. 요청 body의 `newParentId`가 `null`이면 root로 이동합니다.

```json
{
  "newParentId": 10
}
```

서버는 이동 시 subtree의 `rootId`, `path`, `depth`와 closure table을 재계산합니다. 자기 자신 또는 descendant 아래로 이동하는 순환 구조는 `409 Conflict`로 거부하고, archived workspace 이동은 기존 mutation 정책에 따라 거부합니다.

Workspace 상태 변경은 사용자용/관리용 경로 모두에서 제공합니다.

- `POST {base-path}/{workspaceId}/archive`
- `POST {base-path}/{workspaceId}/activate`

요청 body는 선택 사항이며 child를 함께 처리할 때 `cascade=true`를 전달합니다.

```json
{
  "cascade": true
}
```

`archive`는 대상 workspace에 활성 descendant가 있으면 `cascade=true` 없이 거부합니다. `cascade=true`는 대상과 모든 활성 descendant를 함께 비활성화합니다. `activate`는 성공 시 `WorkspaceRef`를 반환하며 이미 활성 상태인 workspace에는 idempotent하게 동작합니다. archived parent 아래의 child 단독 활성화는 거부되며 parent subtree를 복구하려면 parent에 `cascade=true`로 요청합니다.

## 자동구성
- `WorkspaceAutoConfiguration`: JPA entity/repository scan, `WorkspaceTreeService`, `WorkspaceMemberService`, `WorkspacePermissionService`
- `WorkspaceWebAutoConfiguration`: `WorkspaceController`, `WorkspaceMgmtController`

## 검증
```bash
./gradlew :starter:studio-platform-starter-workspace:build
```
