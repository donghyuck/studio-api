# 최소 RAG 구성과 업무 책임 분리

## 목표

기존 full 서버와 API 동작을 보존하면서 최소 팀 문서 RAG 서버의 선택 구성 계약을 마련한다.

## 범위

- runtime `studioComposition=full|rag-minimal` 의존성 구성과 대응 Spring profile.
- 최소 구성은 mail/wiki/template/skillgraph/avatar 확장을 제외한다. Team/Workspace, 인증/권한, 파일/URL, 문서 처리, AI/RAG는 유지한다.
- Vector Map 생성기·executor·저장소·controller를 `studio.ai.vector.projection.enabled` 조건으로 격리한다. 기본 true로 호환성을 유지한다.
- 관리자 capability에 시각화 전용 key를 추가하고 중복 경로 제거.
- ChatController의 답변 후처리를 HTTP 비의존 서비스로 추출한다. 검색/캐시/스트리밍 전체 분리는 후속 단계이며 이번에 전면 재작성하지 않는다.
- 기존 DB/자료 삭제, 런타임 재시작, 커밋/PR 등록은 범위 밖이다.

## 수용 기준

1. full 기본 구성의 의존성과 동작은 유지된다.
2. rag-minimal 런타임 classpath에 제외된 확장 artifact가 없고 필수 RAG artifact는 존재한다.
3. 시각화 false에서 관련 bean/controller/capability가 없으며 일반 RAG는 존재한다.
4. 후처리 서비스는 servlet, ResponseEntity, 요청 DTO에 의존하지 않고 검증 결과를 보존한다.
5. 기존 RAG 회귀 테스트, 시각화 토글 테스트, 관리자 capability 테스트, 구성 검증이 통과한다.
6. 최소 구성은 신규 DB/별도 schema를 전제로 한다. full DB의 migration 이력을 숨기거나 validate를 해제하지 않는다.

## 검증 계획

Gradle 선택 테스트, 독립 Maven 소비자 matrix, runtime 구성 계약 검증, 프론트 typecheck 및 Vitest.
실제 DB 설치는 실행 환경 가능 여부를 별도 기록하며, 미실행을 성공으로 간주하지 않는다.

## 구현 및 검증 결과 (2026-09-15)

- 기존 `codex/modular-composition` 작업 브랜치와 선행 미커밋 변경을 보존했다.
- runtime full/rag-minimal classpath 검증, 최소 프로필 덮어쓰기·모델 설정 테스트 통과.
- AI Web 선택 구성·JDBC 등록 순서·HTTP 비의존 후처리·ChatController/RAG 회귀 12개 클래스 139개 테스트 통과.
- 독립 Maven 소비자 7개 조합 × 3개 테스트 통과. evidence: `/var/folders/4k/bhvzgr9d3cz80nw2xz_3sf100000gn/T/studio-consumer-matrix.Pbxd3O`.
- 프론트 typecheck 및 ServerFeaturesProvider Vitest 4개 통과. 세 저장소 diff check 통과.
- 독립 검토 `module_dependency_check`의 프로필 안내·avatar-image 키·JDBC 순서 검증 요청 반영.
- Docker daemon 미실행으로 실제 신규 DB migration은 미검증이다. 기존 DB에 최소 프로필을 적용하지 않았다.
- runtime 재시작, 커밋·PR은 하지 않았다. 후처리만 분리한 첫 단계이며 검색·캐시·SSE의 전체 application service 이전은 후속 과제다.

검증 명령:

```sh
# studio-api
./gradlew -q :starter:studio-platform-starter-ai-web:test --tests '*RagAnswerProcessingServiceTest' --tests '*OpenAiProviderAutoConfigurationTest' --tests '*ChatController*Test' --tests '*RagAnswerFinalizerTest' --tests '*TeamRag*Test'
bash scripts/verify-modular-consumers.sh
# studio-one-api-server
./gradlew -q -PstudioComposition=rag-minimal verifyStudioComposition test --tests '*RagMinimalCompositionConfigurationTest'
./gradlew -q verifyStudioComposition test --tests '*RagMinimalCompositionConfigurationTest' --tests '*AiModelDeploymentConfigurationSnapshotTest'
# studio-api-frontend
npm run typecheck
npx vitest run src/react/features/platform/ServerFeaturesProvider.test.tsx --pool=threads --maxWorkers=1 --no-file-parallelism
```

## Context Usage Report

### Investigation Summary

| Item | Result |
|---|---|
| Task type | 모듈 구성 변경 및 책임 분리 |
| Main target | runtime 구성, 선택 시각화, 답변 후처리, 관리자 route |
| Strategy | 직전 CodeGraph 검토를 시작점으로 구간 검색·회귀 테스트 |
| Final status | 명시한 1차 범위 구현 완료 |
| Verification status | 코드/자동 테스트 확인, 신규 DB 미확인 |

### Search And CodeGraph Usage

| Category | Details |
|---|---|
| Search terms | VectorProjection, finalizeRagResponse, ensureInterpretiveFallbackLimitation, studioComposition, schema, capabilities |
| CodeGraph entry points | 앞선 검토에서 확인한 ChatController와 RAG 서비스 |
| Path traced | HTTP controller → 답변 후처리 서비스 → 기존 finalizer |
| Candidate files | auto config, ChatController, runtime build/config, frontend route/capability, 관련 테스트 |
| Selected files | 위 책임 경계만 변경; 별도 DB/업무 도메인 수정 없음 |

### File Inspection Scope

| File / Area | Read Scope | Reason | Key Evidence |
|---|---|---|---|
| AiWebAutoConfiguration | Partial | 시각화 bean 경계 | 생성기/executor/JDBC/controller |
| ChatController | Partial | 순수 후처리 추출 | 동일 메서드 본문 이동 |
| runtime build/profile | Partial | 선택 의존성과 schema | full 기본 유지/minimal overlay |
| frontend routes/capability | Partial | 메뉴 계약 | 중복 제거와 전용 key |
| consumer/test files | Full | 회귀 및 조합 보증 | 실제 artifact classpath/조건부 bean |
| 정책·모듈 조합 문서 | Full | 실행 절차와 경계 기록 | 기본 호환과 DB 전환 제한 |

### Evidence Checked

| Evidence Type | Checked Item | Result |
|---|---|---|
| Method | 기존/신규 후처리 본문 | 의미 보존, 직접 테스트 통과 |
| SQL | 제외 모듈 참조 키워드 검색 | 관련 FK 검색 결과 없음; 전체 DB 실행 대체 아님 |
| Test | API/runtime/frontend/consumer | 통과 |
| Diff | 세 저장소 diff check | 통과 |

### Files Or Areas Intentionally Not Read

| Area | Reason | Risk |
|---|---|---|
| 외부 모델 adapter 구현 | 모델 변경·LLM 호출 없음 | Low |
| ChatController의 모든 검색/캐시 로직 | 후처리 외 동작을 유지 | Medium |
| 전체 migration 실행 | Docker 미실행, 기존 DB 변경 금지 | Medium |

### Original Source Verification

| Item | Method | Result |
|---|---|---|
| Source | 직접 구간 읽기와 독립 검토 | Verified |
| SQL/DB | 제한 검색, 실행 없음 | Partially verified |
| Runtime logs | 서버 재시작 없음 | Not verified |
| Tests | 위 명령 | Passed |
| Diff | git diff --check | Passed |

### Context Efficiency Metrics

| Metric | Count |
|---|---:|
| 새 CodeGraph 호출 | 0 (앞선 검토 재사용) |
| 독립 검토 에이전트 | 1 |
| API 테스트 클래스/케이스 | 12 / 139 |
| 소비자 조합/케이스 | 7 / 21 |
| 프론트 테스트 케이스 | 4 |

### Final Assessment

| Rule | Result | Comment |
|---|---|---|
| Search before reading | Pass | 이전 그래프 경로에서 시작 |
| Candidate files narrowed | Pass | 변경 책임 경계 고정 |
| Large files avoided | Pass | ChatController 부분만 읽음 |
| Partial reads | Pass | 메서드 이동에 필요한 구간 |
| Skipped areas documented | Pass | DB/운영 한계 기록 |
| Original verification | Pass | 선택 테스트 및 소스 확인 |

Conclusion: Context usage was controlled. The investigation followed the Large Context Analysis process.
