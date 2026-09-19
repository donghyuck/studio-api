# 배포 artifact 선택 구성 검증

이 프로젝트에는 `includeBuild` 또는 `project(...)` 의존성이 없다. 독립 Maven 소비자가 실제 게시한 JAR과 전이 의존성을 사용한다.

실행: 저장소 root에서 `bash scripts/verify-modular-consumers.sh`.
현재 버전의 artifact를 임시 파일 Maven 저장소에 배포하고 base, team, workspace, ai, team-ai, full, rag-minimal을 각각 실행한다.
`full`은 Team + Workspace + AI Web 통합 조합을 뜻한다.

검증은 classpath, 자동 설정 application context, 선택 bridge back-off를 확인한다.
Team/Workspace는 H2 create-drop을 사용하며 AI 외부 provider/pipeline 포트는 테스트 대역이다.
외부 모델 응답 품질이나 운영 DB Flyway 설치 검증을 대신하지 않는다.
운영 DB 검증은 기존 `-PrunDbTests=true` 테스트를 별도로 실행한다.

Team 스키마의 기존 Company 외래키 때문에 설치 시 user schema가 선행되어야 한다.
Workspace 스키마는 user 및 Team schema를 선행한다. 스키마 설치와 Company 업무 기능 활성화는 별개다.
이미 배포된 V1800/V1801 파일은 checksum 호환을 위해 수정하지 않는다.
