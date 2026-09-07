# Studio Platform Team

Team을 Company와 독립적인 협업·멤버십·권한·RAG 경계로 제공하는 계약 모듈이다.

## 핵심 계약

- Team은 Company에 선택적으로 배정할 수 있다.
- `PUBLIC`은 검색 가능성과 가입 정책을 의미하며 자료의 익명 공개를 의미하지 않는다.
- Team 역할은 `OWNER`, `ADMIN`, `MEMBER`다.
- Team은 서로 독립적인 여러 root Workspace 트리를 소유할 수 있으며 하위 Workspace는 같은 Team을 유지한다.
- Team 전체 RAG는 읽기 권한이 있는 모든 Workspace 트리를 하나의 검색 범위로 집계한다.
- `permissionVersion`은 멤버·역할 변경 시 증가해 RAG cache를 격리한다.
- Team migration port는 Workspace와 knowledge 구현을 primitive ID 계약으로 분리한다.

구현은 `studio-platform-team-default`, 자동 구성은 `studio-platform-starter-team`을 사용한다.
