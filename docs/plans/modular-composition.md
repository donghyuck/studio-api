# 선택 모듈 구성 목표 및 검증

## 목표와 역할

서버는 명시한 starter, 인프라 의존성과 설정만으로 구성된다. 관리자 프런트는 서버의 실제 기능 가용성을 반영한다.
기능 모듈은 업무 규칙과 권한을 소유하고, 서버는 구현·DB·provider를 선택한다.

## 지원 계약

| 구성 | 필수 기능 | 선택 연결 |
|---|---|---|
| 공통 기반 | platform, autoconfigure, data | 문서 추출·썸네일 |
| Team | Team 계약·기본 구현, 필요한 JPA/보안 인프라 | Company 배정, Workspace 생성 |
| Team Workspace | Team + Workspace 및 관련 스키마 | AI, 첨부파일, Wiki, URL |
| AI Web | AI, MVC, validation, security 및 선택 provider | Team/Workspace/realtime |
| Team RAG | Team + Workspace + AI Web | 첨부파일·Wiki·URL contributor |

Workspace는 이번 제품의 Team 기반 자료 공간이다. Team 자체는 Workspace 없이 사용 가능하다.
기존 적용된 Flyway migration은 변경하지 않으며 Team Workspace 설치 시 Team 스키마를 함께 적용한다.

## 실행 단계 및 완료 기준

1. AI Web의 Team 연결 설정을 격리하고 Team/Workspace JAR 제거 테스트를 통과한다.
2. 공통 starter에서 문서 기능을 분리하고 기존 consumer가 필요한 기능을 명시한다.
3. Team의 불필요한 Workspace artifact 의존성을 제거하고 required/optional 설치 명세를 갱신한다.
4. 배포 artifact를 사용하는 별도 consumer 조합 검증을 추가한다.
5. 서버 기능 가용성 API와 프런트 메뉴·라우트 가드를 연결한다.
6. 관련 회귀 테스트, 최소 classpath 검증, 문서 및 변경 기록을 완료한다.

## 호환성과 검증

- 기존 전체 구성 서버의 자료·권한·스키마를 보존한다.
- 기능 가용성 정보는 접근 권한을 부여하지 않는다. 업무 API의 권한 검사를 유지한다.
- 지원되지 않는 조합은 명확한 설치 조건으로 설명한다.
- 검증 결과는 실제 실행한 범위만 기록한다.

## 구현 및 검증 결과 (2026-09-10)

| 목표 | 결과 |
|---|---|
| 선택 의존성 격리 | AiTeamRagAutoConfiguration 분리, Micrometer/AspectJ 격리 |
| 공통 starter 경량화 | textract/thumbnail 전이 제거, 실행 서버 명시 의존성 추가 |
| Team 독립성 | Team 계약/구현/starter에서 Workspace artifact 의존성 제거 |
| 배포 artifact 검증 | AI 기본 JAR classifier 수정, POM만 사용하는 독립 consumer 추가 |
| 관리자 기능 감지 | 모듈 소유 descriptor, 실제 MVC 등록 기준 capability API, 메뉴/라우트/선택 자료 가드 |
| 회귀 검증 | base/team/workspace/ai/team-ai/full의 6개 구성, 각 3개 검사 통과 |

실행 명령 및 근거:
- `bash scripts/verify-modular-consumers.sh`: 6개 구성 모두 PASS. 임시 파일 Maven repository만 사용한다.
- 선택 JAR 제거 probe: Team/Workspace 제거 시 AiWebAutoConfiguration 메서드 로딩 PASS.
- `:starter:studio-platform-starter-ai-web:test`, Team/Workspace starter 테스트: PASS.
- `:starter:studio-platform-starter:test --tests '*PlatformCapabilitiesTest'`: PASS.
- 프런트 타입 검사, 기능 가드/Team/파일 상태 테스트 및 production build: PASS.
- 실행 서버 `AiModelDeploymentConfigurationSnapshotTest`: PASS.
- 신규 GitHub Actions workflow는 아직 원격 실행하지 않았다.
- 실제 DB migration 테스트는 Docker 데몬 미실행으로 수행하지 않았다. H2 context 검증은 실제 Flyway 검증과 구분한다.

## Context Usage Report

| 항목 | 결과 |
|---|---|
| 전략 | 앞선 재현 결과 → 의존성·자동 설정 경계 확인 → 독립 소비자 검증 |
| 확인 소스 | Gradle/POM, AiWeb/Team 연결 설정, 기본 starter, 관리자 Layout/라우트/자료 초기 요청 |
| 원본 검증 | 선택 JAR 제거 검사, 새 Maven 저장소에서 6개 조합 실행, 실제 자동 탐색과 capability 선언 확인 |
| 변경 경계 | 이번 모듈 조립 관련 소스만 수정; 기존 .omx와 QA 산출물 유지 |
| 제외 범위 | 외부 모델 품질 평가와 실제 DB 신규 설치; 전자는 이번 목표 밖, 후자는 Docker 미실행 |
| 최종 상태 | 구현·자동화 검사 완료, 운영 DB 설치 및 원격 CI 실행은 별도 검증 경계 |
