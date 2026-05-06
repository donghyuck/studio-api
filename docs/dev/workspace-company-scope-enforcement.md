# Workspace Company Scope Enforcement

Workspace `companyId` nullable 전환 기간이 끝난 뒤 적용하는 운영 절차입니다.

## 적용 대상

- migration: `V1302__enforce_workspace_company_scope.sql`
- table: `TB_PLATFORM_WORKSPACE`
- 적용 후 제약:
  - `COMPANY_ID NOT NULL`
  - company별 `PATH` unique
  - company별 root `SLUG` unique
  - company + parent별 `SLUG` unique

`V1302`는 데이터 backfill과 duplicate 정리가 끝난 환경에서만 적용합니다.

## 적용 순서

1. 모든 root workspace에 대상 company를 결정합니다.
2. root의 `COMPANY_ID`를 채우고, child workspace는 parent/root의 `COMPANY_ID`를 상속하도록 backfill합니다.
3. 아래 검증 SQL로 null과 duplicate가 없는지 확인합니다.
4. 애플리케이션 설정을 `studio.features.workspace.company-required=true`로 전환합니다.
5. `V1302`를 적용합니다.
6. 신규 root 생성 API가 항상 `companyId`를 보내는지 smoke test합니다.

## PostgreSQL 검증 SQL

```sql
-- companyId 미지정 workspace가 없어야 합니다.
SELECT WORKSPACE_ID, PARENT_ID, ROOT_ID, PATH
FROM TB_PLATFORM_WORKSPACE
WHERE COMPANY_ID IS NULL;

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

여러 legacy root가 같은 `PATH` 또는 root `SLUG`를 공유하더라도 company가 다르면 `V1302` 이후 허용됩니다. 같은 company 안에서 중복이 남아 있으면 migration이 실패하므로 slug/path 정리를 먼저 완료해야 합니다.

## MySQL/MariaDB PARENT_KEY

MySQL/MariaDB unique index는 `NULL` 값을 서로 다른 값으로 취급하므로 root slug 중복을 막기 위해 generated column `PARENT_KEY = COALESCE(PARENT_ID, 0)`을 추가합니다. 최종 unique key는 `(COMPANY_ID, PARENT_KEY, SLUG)`이며, root와 child slug 제약을 같은 방식으로 강제합니다.
