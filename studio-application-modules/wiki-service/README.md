# Workspace Wiki Service

Workspace 단위 Wiki page/revision MVP를 제공하는 애플리케이션 모듈이다. 저장소는 v1에서 JPA만 지원하며, 권한은 별도 Wiki ACL 테이블이 아니라 `studio-platform-workspace`의 `WorkspacePermissionService`와 `WorkspacePermissionContributor`로 처리한다.

## 범위
- 포함: page 생성/조회/수정/archive, revision history/detail, revision revert, markdown 원문과 sanitized HTML 응답.
- 제외: 첨부파일, page-level ACL, custom role, deny override, hard delete, Git-backed storage.
- 삭제는 hard delete가 아니라 archive이며, revision 이력은 보존된다.

## 구성 요소
- `WikiPageService`: page/revision service 계약.
- `DefaultWikiPageService`: JPA repository와 `WorkspacePermissionService`를 사용하는 기본 구현.
- `WikiWorkspacePermissionContributor`: workspace role에 Wiki action을 추가한다.
- `DefaultWikiRenderService`: CommonMark로 markdown을 HTML로 렌더링하고 Jsoup으로 sanitize한다.
- `WikiController`: 인증 사용자용 API.
- `WikiMgmtController`: 관리용 API. `features:workspace/manage` 또는 플랫폼 `ADMIN` role에서 workspace member 여부와 무관하게 동작한다.

## 권한 모델
Wiki action은 workspace permission action으로 등록된다.

| Action | 설명 |
| --- | --- |
| `wiki.page.read` | page 읽기 |
| `wiki.page.create` | page 생성 |
| `wiki.page.update` | page 수정 |
| `wiki.page.delete` | page archive |
| `wiki.page.revert` | revision revert |
| `wiki.page.history.read` | revision history/detail 조회 |
| `wiki.admin` | `_Sidebar`, `_Footer` 관리 |

기본 role mapping:

| Role | 추가 Wiki 권한 |
| --- | --- |
| `VIEWER` | `wiki.page.read`, `wiki.page.history.read` |
| `EDITOR` | `VIEWER` + `wiki.page.create`, `wiki.page.update`, `wiki.page.revert` |
| `ADMIN` | `EDITOR` + `wiki.page.delete`, `wiki.admin` |
| `OWNER` | 전체 action |

`_Sidebar`, `_Footer`의 생성/수정/revert/archive는 일반 create/update/delete/revert 권한이 아니라 `wiki.admin`을 요구한다.

## REST API
기본 경로는 사용자용과 관리용을 분리한다.

- 사용자용: `/api/workspaces/{workspaceId}/wiki`
- 관리용: `/api/mgmt/workspaces/{workspaceId}/wiki`

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/pages` | non-archived page 목록 |
| `GET` | `/pages/{pageSlug}` | page 조회. `markdown`, `sanitizedHtml` 포함 |
| `PUT` | `/pages/{pageSlug}` | create-or-update. body: `title`, `markdown`, `baseRevisionId` |
| `DELETE` | `/pages/{pageSlug}` | archive |
| `GET` | `/pages/{pageSlug}/revisions` | revision 목록 |
| `GET` | `/pages/{pageSlug}/revisions/{revisionId}` | revision 상세. `markdown`, `sanitizedHtml` 포함 |
| `POST` | `/pages/{pageSlug}/revisions/{revisionId}/revert` | 이전 revision을 새 revision으로 복사 |

`baseRevisionId`가 현재 revision과 다르면 `409 Conflict`가 반환된다. Archived page는 일반 read/list에서 제외되며, revision history는 조회 가능하다.

## 데이터 모델
- `TB_APPLICATION_WORKSPACE_WIKI_PAGE`: workspace별 page slug, archive 상태, current revision 포인터.
- `TB_APPLICATION_WORKSPACE_WIKI_PAGE_REVISION`: revisionNo와 markdown 원문을 immutable history로 저장.
- Flyway range: `wiki` `1400-1499`.
- 초기 migration: `schema/wiki/{postgres,mysql,mariadb}/V1400__create_workspace_wiki_tables.sql`.

## 직접 사용 예
```java
WikiPage page = wikiPageService.putPage(
        10L,
        "Home",
        new WikiPageWriteCommand("Home", "# Hello", null, actor));
```

서비스 계층이 항상 `WorkspacePermissionService`를 호출하므로 controller 외부에서 직접 호출해도 workspace 권한 정책이 적용된다.
