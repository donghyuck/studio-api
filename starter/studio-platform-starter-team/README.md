# Studio Platform Team Starter

Team JPA 구현, REST API, 멤버·가입 정책을 자동 구성한다. Workspace와 AI artifact는 필수가 아니다.
Workspace provisioning과 migration은 해당 연결 포트가 제공될 때 구성한다.

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

Team 단독 서버는 `provisionRootWorkspace=false`로 Team을 생성한다. Workspace를 함께 구성한 서버의
일반 Team 생성은 root Workspace를 자동 생성한다. 기존 root Workspace를 이관할 migration target Team은
platform admin이 `provisionRootWorkspace=false`로 생성한 뒤 `/api/mgmt/team-migrations`의
dry-run/apply/verify/rollback 흐름을 사용한다.

Team RAG를 사용하려면 AI Web, Workspace, attachment/web knowledge starter를 함께 구성한다. 기존 source와
vector는 복사하지 않으며 Team corpus fingerprint와 permission version으로 cache와 citation을 격리한다.

DB 설치 시 기존 Company FK를 위해 user schema를 먼저 적용한다. Company 배정 서비스를 활성화할 필요는 없다.
MVC/validation/security/JPA 인프라는 실행 서버에서 명시하며, 별도 consumer 검증은
`bash scripts/verify-modular-consumers.sh`로 실행한다.
