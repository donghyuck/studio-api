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
- `DELETE /pages/{pageSlug}` (`baseRevisionId` body 필요)
- `GET /pages/{pageSlug}/revisions`
- `GET /pages/{pageSlug}/revisions/{revisionId}`
- `POST /pages/{pageSlug}/revisions/{revisionId}/revert` (`baseRevisionId` body 필요)

신규 page 생성은 `baseRevisionId`를 생략한다. 기존 page update, archive, revert는 현재 `currentRevisionId`를 `baseRevisionId`로 전달해야 하며 누락되거나 현재 revision과 다르면 `409 Conflict`가 반환된다. `_Sidebar`, `_Footer` write/revert/archive는 `wiki.admin` 권한이 필요하다.

## 저장소
Wiki DB 저장소는 다음 테이블을 사용한다.

- `TB_APPLICATION_WORKSPACE_WIKI_PAGE`
- `TB_APPLICATION_WORKSPACE_WIKI_PAGE_REVISION`

## Package structure
자동구성 import는 Wiki 모듈의 새 패키지 구조를 기준으로 한다.

- contract: `studio.one.application.wiki.application.usecase`, `studio.one.application.wiki.application.command`, `studio.one.application.wiki.application.error`, `studio.one.application.wiki.domain.*`
- default implementation: `studio.one.application.wiki.application.service`, `studio.one.application.wiki.infrastructure.persistence.jpa`
- web DTO: `studio.one.application.wiki.web.dto.request`, `studio.one.application.wiki.web.dto.response`

## 보안
- raw markdown은 응답에 포함되지만, HTML은 서버에서 CommonMark 렌더링 후 Jsoup sanitize 결과인 `sanitizedHtml`로 함께 제공한다.
- 클라이언트가 HTML을 렌더링할 때는 `sanitizedHtml`을 사용한다.
- endpoint auth는 사용자용 read/write, 관리용 manage를 먼저 검사하고, 실제 workspace role/visibility 권한은 `WorkspacePermissionService`에서 다시 검사한다.
