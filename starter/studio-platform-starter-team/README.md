# Studio Platform Team Starter

Team JPA 구현, REST API, 멤버·가입 정책, root Workspace provisioning과 migration engine을 자동 구성한다.

```kotlin
implementation(project(":starter:studio-platform-starter-team"))
implementation(project(":starter:studio-platform-starter-workspace"))
```

```yaml
studio:
  features:
    team:
      enabled: true
      persistence: jpa
      web:
        enabled: true
        public-base-path: /api/teams
```

일반 Team 생성은 root Workspace를 자동 생성한다. 기존 root Workspace를 이관할 migration target Team은
platform admin이 `provisionRootWorkspace=false`로 생성한 뒤 `/api/mgmt/team-migrations`의
dry-run/apply/verify/rollback 흐름을 사용한다.

Team RAG를 사용하려면 AI Web, Workspace, attachment/web knowledge starter를 함께 구성한다. 기존 source와
vector는 복사하지 않으며 Team corpus fingerprint와 permission version으로 cache와 citation을 격리한다.
