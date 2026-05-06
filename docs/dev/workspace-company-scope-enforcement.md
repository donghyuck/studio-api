# Workspace Company Scope Enforcement

Workspace `companyId` nullable 전환 기간이 끝난 뒤 적용하는 운영 절차입니다.

## 적용 대상

- migration: `V1302__enforce_workspace_company_scope.sql`, `V1303__add_workspace_company_fk.sql`
- table: `TB_PLATFORM_WORKSPACE`
- 적용 후 제약:
  - `COMPANY_ID NOT NULL`
  - company별 `PATH` unique
  - company별 root `SLUG` unique
  - company + parent별 `SLUG` unique
  - `COMPANY_ID`가 `TB_APPLICATION_COMPANY(COMPANY_ID)`를 참조하는 FK

`V1302`는 데이터 backfill과 duplicate 정리가 끝난 환경에서만 적용합니다. `V1303`은 workspace가 참조하는 company가 실제로 존재해야 적용되므로, user schema migration을 먼저 적용하고 orphan workspace를 정리해야 합니다.

## 적용 순서

1. 모든 root workspace에 대상 company를 결정합니다.
2. root의 `COMPANY_ID`를 채우고, child workspace는 parent/root의 `COMPANY_ID`를 상속하도록 backfill합니다.
3. root 생성과 path 조회를 호출하는 client를 점검합니다. company-scoped root 생성은 관리용 `POST /api/mgmt/workspaces`에서 `companyId`를 전달해야 하며, 사용자용 `POST /api/workspaces`는 company-scoped root 생성을 허용하지 않습니다. `GET /api/workspaces/by-path`와 `GET /api/mgmt/workspaces/by-path`는 `companyId` query parameter를 보내야 합니다.
4. 아래 검증 SQL로 null, mixed-company tree, duplicate가 없는지 확인합니다.
5. maintenance window에서 `V1302`, `V1303`을 적용합니다.
6. 애플리케이션 설정을 `studio.features.workspace.company-required=true`, `studio.features.workspace.company-scope-enforced=true`로 전환합니다. `company-scope-enforced=true`는 `company-required=true`와 함께만 사용할 수 있고, runtime root slug 중복 체크를 company scope로 바꿉니다. V1302 schema가 없으면 애플리케이션은 기동 단계에서 실패합니다. V1302 schema가 이미 적용된 DB에서 `company-scope-enforced=false`로 기동하는 것도 tenant-ambiguous path 조회를 막기 위해 실패합니다.
7. 관리용 신규 root 생성 API와 company-aware path 조회가 정상 동작하는지 다시 smoke test합니다.

## PostgreSQL 검증 SQL

```sql
-- companyId 미지정 workspace가 없어야 합니다.
SELECT WORKSPACE_ID, PARENT_ID, ROOT_ID, PATH
FROM TB_PLATFORM_WORKSPACE
WHERE COMPANY_ID IS NULL;

-- child는 parent와 같은 companyId를 가져야 합니다.
SELECT CHILD.WORKSPACE_ID, CHILD.COMPANY_ID, CHILD.PARENT_ID, PARENT.COMPANY_ID AS PARENT_COMPANY_ID
FROM TB_PLATFORM_WORKSPACE CHILD
JOIN TB_PLATFORM_WORKSPACE PARENT ON PARENT.WORKSPACE_ID = CHILD.PARENT_ID
WHERE CHILD.COMPANY_ID IS DISTINCT FROM PARENT.COMPANY_ID;

-- subtree의 모든 row는 root와 같은 companyId를 가져야 합니다.
SELECT WORKSPACE.WORKSPACE_ID, WORKSPACE.COMPANY_ID, WORKSPACE.ROOT_ID, ROOT.COMPANY_ID AS ROOT_COMPANY_ID
FROM TB_PLATFORM_WORKSPACE WORKSPACE
JOIN TB_PLATFORM_WORKSPACE ROOT ON ROOT.WORKSPACE_ID = WORKSPACE.ROOT_ID
WHERE WORKSPACE.COMPANY_ID IS DISTINCT FROM ROOT.COMPANY_ID;

-- company별 path 중복이 없어야 합니다.
SELECT COMPANY_ID, PATH, COUNT(*)
FROM TB_PLATFORM_WORKSPACE
GROUP BY COMPANY_ID, PATH
HAVING COUNT(*) > 1;

-- company별 root slug 중복이 없어야 합니다.
SELECT COMPANY_ID, SLUG, COUNT(*)
FROM TB_PLATFORM_WORKSPACE
WHERE PARENT_ID IS NULL
GROUP BY COMPANY_ID, SLUG
HAVING COUNT(*) > 1;

-- company + parent별 slug 중복이 없어야 합니다.
SELECT COMPANY_ID, PARENT_ID, SLUG, COUNT(*)
FROM TB_PLATFORM_WORKSPACE
WHERE PARENT_ID IS NOT NULL
GROUP BY COMPANY_ID, PARENT_ID, SLUG
HAVING COUNT(*) > 1;
```

## MySQL/MariaDB 검증 SQL

```sql
-- companyId 미지정 workspace가 없어야 합니다.
SELECT WORKSPACE_ID, PARENT_ID, ROOT_ID, PATH
FROM TB_PLATFORM_WORKSPACE
WHERE COMPANY_ID IS NULL;

-- child는 parent와 같은 companyId를 가져야 합니다.
SELECT CHILD.WORKSPACE_ID, CHILD.COMPANY_ID, CHILD.PARENT_ID, PARENT.COMPANY_ID AS PARENT_COMPANY_ID
FROM TB_PLATFORM_WORKSPACE CHILD
JOIN TB_PLATFORM_WORKSPACE PARENT ON PARENT.WORKSPACE_ID = CHILD.PARENT_ID
WHERE NOT (CHILD.COMPANY_ID <=> PARENT.COMPANY_ID);

-- subtree의 모든 row는 root와 같은 companyId를 가져야 합니다.
SELECT WORKSPACE.WORKSPACE_ID, WORKSPACE.COMPANY_ID, WORKSPACE.ROOT_ID, ROOT.COMPANY_ID AS ROOT_COMPANY_ID
FROM TB_PLATFORM_WORKSPACE WORKSPACE
JOIN TB_PLATFORM_WORKSPACE ROOT ON ROOT.WORKSPACE_ID = WORKSPACE.ROOT_ID
WHERE NOT (WORKSPACE.COMPANY_ID <=> ROOT.COMPANY_ID);

-- company별 path 중복이 없어야 합니다.
SELECT COMPANY_ID, PATH, COUNT(*) AS CNT
FROM TB_PLATFORM_WORKSPACE
GROUP BY COMPANY_ID, PATH
HAVING CNT > 1;

-- company별 root slug 중복이 없어야 합니다.
SELECT COMPANY_ID, SLUG, COUNT(*) AS CNT
FROM TB_PLATFORM_WORKSPACE
WHERE PARENT_ID IS NULL
GROUP BY COMPANY_ID, SLUG
HAVING CNT > 1;

-- company + parent별 slug 중복이 없어야 합니다.
SELECT COMPANY_ID, PARENT_ID, SLUG, COUNT(*) AS CNT
FROM TB_PLATFORM_WORKSPACE
WHERE PARENT_ID IS NOT NULL
GROUP BY COMPANY_ID, PARENT_ID, SLUG
HAVING CNT > 1;
```

## Backfill 예시

운영 환경의 company 매핑 기준은 서비스별로 다르므로, 아래 SQL은 절차 예시입니다.

```sql
-- 예시: 특정 root subtree를 company 10에 배정합니다.
UPDATE TB_PLATFORM_WORKSPACE
SET COMPANY_ID = 10
WHERE ROOT_ID = 100 OR WORKSPACE_ID = 100;
```

여러 legacy root가 같은 `PATH` 또는 root `SLUG`를 공유하더라도 company가 다르면 `V1302` 이후 schema에서는 허용됩니다. runtime에서 이 동작을 허용하려면 `V1302` 적용 후 `studio.features.workspace.company-scope-enforced=true`를 켭니다. 같은 company 안에서 중복이 남아 있으면 migration이 실패하므로 slug/path 정리를 먼저 완료해야 합니다.

## MySQL/MariaDB PARENT_KEY

MySQL/MariaDB unique index는 `NULL` 값을 서로 다른 값으로 취급하므로 root slug 중복을 막기 위해 generated column `PARENT_KEY = COALESCE(PARENT_ID, 0)`을 추가합니다. 최종 unique key는 `(COMPANY_ID, PARENT_KEY, SLUG)`이며, root와 child slug 제약을 같은 방식으로 강제합니다.
