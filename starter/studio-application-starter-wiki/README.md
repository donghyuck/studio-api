# Studio Application Starter Wiki

`studio-application-modules:wiki-service`의 JPA repository, service, workspace permission contributor, REST controller를 자동 구성하는 Spring Boot starter다.

## 의존성
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter"))
    implementation(project(":starter:studio-platform-starter-workspace"))
    implementation(project(":starter:studio-application-starter-wiki"))
}
```

Wiki는 `studio-platform-workspace` contract에 의존하며, 기본 구현은 workspace starter가 제공하는 `WorkspacePermissionService`가 필요하다.

## 설정
```yaml
studio:
  features:
    workspace:
      enabled: true
      persistence: jpa
    wiki:
      enabled: true
      persistence: jpa
      web:
        enabled: true
        public-base-path: /api/workspaces
        mgmt-base-path: /api/mgmt/workspaces
```

- `studio.features.wiki.enabled=true`: Wiki service와 permission contributor 활성화.
- `studio.features.wiki.persistence=jpa`: v1에서 지원하는 유일한 기본 persistence.
- `studio.features.wiki.web.enabled=true`: 사용자용/관리용 controller 등록.
- `public-base-path`: 인증 사용자용 API prefix. 최종 경로는 `{public-base-path}/{workspaceId}/wiki`이며 익명 공개 API가 아니다.
- `mgmt-base-path`: 관리용 API prefix. 최종 경로는 `{mgmt-base-path}/{workspaceId}/wiki`이며 `features:workspace/manage` 또는 플랫폼 `ADMIN` role이면 workspace member 여부와 무관하게 관리 작업을 수행한다.

## 엔드포인트
사용자용과 관리용 경로 모두 같은 page/revision API를 제공한다.

- `GET /pages`
- `GET /pages/{pageSlug}`
- `PUT /pages/{pageSlug}`
- `DELETE /pages/{pageSlug}`
- `GET /pages/{pageSlug}/revisions`
- `GET /pages/{pageSlug}/revisions/{revisionId}`
- `POST /pages/{pageSlug}/revisions/{revisionId}/revert`

쓰기 요청은 `baseRevisionId`를 받을 수 있으며 현재 revision과 다르면 `409 Conflict`가 반환된다. `_Sidebar`, `_Footer` write/revert/archive는 `wiki.admin` 권한이 필요하다.

## Schema
Flyway location에 `schema/wiki/{db}`를 추가해 `V1400__create_workspace_wiki_tables.sql`이 적용되도록 구성한다. 이 migration은 다음 테이블을 생성한다.

- `TB_APPLICATION_WORKSPACE_WIKI_PAGE`
- `TB_APPLICATION_WORKSPACE_WIKI_PAGE_REVISION`

## 보안
- raw markdown은 응답에 포함되지만, HTML은 서버에서 CommonMark 렌더링 후 Jsoup sanitize 결과인 `sanitizedHtml`로 함께 제공한다.
- 클라이언트가 HTML을 렌더링할 때는 `sanitizedHtml`을 사용한다.
- endpoint auth는 사용자용 read/write, 관리용 manage를 먼저 검사하고, 실제 workspace role/visibility 권한은 `WorkspacePermissionService`에서 다시 검사한다.
