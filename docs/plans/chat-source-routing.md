# 챗봇 정보 출처 라우팅 개선 계획

## 목표

RAG 품질을 유지하면서 질문별로 서버 맥락·문서 근거·일반 모델·실시간 도구를 선택한다.
모든 정보를 임베딩하거나 모든 질문에서 LLM 분류를 추가 호출하지 않는다.
이번 문서는 계획이며 구현 승인이나 도구 연결/데이터 외부 전송을 실행하지 않는다.

## 현재 코드 근거

- `starter/studio-platform-starter-ai-web/src/main/java/studio/one/platform/ai/web/controller/RagQueryIntentClassifier.java:17`: CONTENT_QA, DOCUMENT_METADATA, DOCUMENT_SUMMARY, KEY_POINTS, INTERPRETIVE_ANALYSIS, FACTUAL_LIST. 문서 내부 처리 의도 분류이며 일반 대화 라우터가 아니다.
- 같은 파일 :106 부근의 기본값 CONTENT_QA 때문에 날짜 질문도 문서 질문으로 취급될 수 있다. 해당 경로의 실요청 재현은 이번 계획에서 수행하지 않았다.
- 같은 controller 디렉터리 `RagSourcePolicyResolver.java:48`: 서버 maximumScope 및 외부 provider 가용성으로 요청 출처를 제한한다. 새 라우터가 이를 우회하면 안 된다.
- 같은 디렉터리 `RagExternalEvidenceService.java`: Clock을 외부 근거의 asOfDate에 사용한다. 이 코드는 일반 날짜 응답 계약과 다르다.
- 기존 `web/service/RagAnswerCacheService.java`: 근거·정책 기반 캐시 유효성을 검증한다. 시간 의존 응답에는 추가 격리 또는 우회가 필요하다.

## 요구사항 및 제안 기본값

- 팀 채팅: 혼합형 허용. 문서 Q&A: 기존 문서 중심 정책 유지.
- 순수 현재 날짜·시간은 서버 시계로 직접 응답한다. 시간대는 검증한 사용자 설정, 없으면 서버 기본값으로 결정한다.
- 요청 시작 때 기준 시각을 한 번 고정하여 분류·검색·생성·표시가 동일 기준을 사용한다.
- 혼합 질문은 시간과 문서/도구 근거를 결합한다. '오늘 연차 신청 가능한가'를 단순 날짜 질문으로 분류하지 않는다.
- 문서 검색 실패나 낮은 분류 확신은 일반 지식으로 사내 사실을 만들어내는 fallback 사유가 아니다.
- 사용자/문서 텍스트가 서버 시각·권한·허용 도구·출처 정책을 덮어쓸 수 없다.

## 구현 순서

### 1. 실행 시각 맥락

AI core 쪽에 작은 실행 맥락 값과 provider 계약을 두고 starter에서 Clock/ZoneId 구현을 제공한다.
Clock 주입으로 테스트하며 값은 ISO 기준 시각, 시간대, 로컬 날짜 정도로 최소화한다.
순수 날짜·시간 질문은 서버가 생성한 답변을 반환하고 LLM·임베딩을 호출하지 않는다.
문서 생성 프롬프트에 전달할 때는 문서 사실이 아닌 실행 맥락으로 분리한다.
캐시는 시간 전용 응답을 우회한다. 시간 의존 혼합 질문은 우선 우회하며 추후 날짜·시간대·policy version 키를 검토한다.

### 2. 상위 출처 라우팅

기존 RagQueryIntentClassifier를 유지하고 그 앞에 ChatRoutingService를 둔다.
경로는 SYSTEM_CONTEXT, DOCUMENT_RAG, GENERAL_MODEL, LIVE_TOOL, MIXED로 제한한다.
모호하면 선택된 문서 모드와 대화 맥락을 우선하거나 확인 질문을 한다.
규칙으로 명확한 경로부터 지원하고 낮은 확신 사례가 실제로 누적될 때만 LLM 분류를 검토한다.
Controller에는 새 분기 구현을 쌓지 않고 application service에서 실행 계획을 결정한다.

### 3. 답변 출처 계약 및 관리자 표시

기존 answer/citation 계약에 가산적으로 answerSource, effectiveTimezone, asOf, routeReason을 추가한다.
시스템·문서·일반 지식·업무 조회를 구분하고 MIXED는 주장 단위로 근거를 연결한다.
시스템 날짜에 가짜 문서 인용을 붙이지 않는다. 일반 지식을 '문서 근거 답변'으로 표시하지 않는다.
서버 capability가 있을 때만 새 모드 UI를 노출한다. 출처 표시와 모드 선택의 기본값은 서버 정책이 결정한다.

### 4. 실시간 도구 확장

실제 필요한 조회 한 종류부터 provider 구현을 추가한다. 범용 자율 에이전트는 초기 범위에서 제외한다.
도구마다 사용자 권한, 입력 검증, 타임아웃, 조회 시각, 결과 출처, 실패 응답을 정의한다.
외부 검색은 서버가 허용한 provider에서만 실행하고, 사내 질문/문서 내용을 외부로 자동 전송하지 않는다.
일정·신청·수정 등 쓰기 작업은 별도 권한과 확인 정책을 설계하기 전까지 제공하지 않는다.

## 수용 기준

| 사례 | 기대 결과 |
|---|---|
| 오늘 며칠인가 | 고정 Clock과 사용자 시간대에 맞는 날짜, LLM/벡터 호출 0회 |
| 자정 직전/직후, 서로 다른 시간대 | 각 요청 기준으로 정확하고 전날 캐시 재사용 없음 |
| 문서의 발행일은 | 문서 메타데이터/원문 근거, 시스템 날짜로 대체 금지 |
| 오늘 기준으로 규정 적용 가능한가 | 기준 날짜 + 시행일/개정일 근거, 부족하면 한계 표시 |
| 오늘 휴가 신청 가능한가 | 문서/업무 조회 또는 확인 질문, 날짜만 답하지 않음 |
| 해당 팀 규정 검색 실패 | 일반 상식으로 규정을 단정하지 않음 |
| 일반 개념 질문 | 팀 혼합 모드에서 GENERAL_MODEL 표시, 엄격 문서 모드는 유지 |
| 도구 미설치/권한 없음/타임아웃 | 안전한 안내, 결과 추측이나 다른 출처 무단 전환 없음 |
| 문서에 가짜 시스템 날짜 명령 | 서버 기준 날짜 유지 |
| sync/stream/cache | 같은 출처 및 정책 결과, 미검증 delta 미노출 유지 |

## 위험과 검증

- 오분류: 순수 시간 질문만 deterministic 경로로 허용하고 혼합·후속 질문 회귀셋 추가.
- 권한 우회: source-policy 상한과 기존 object authorization을 라우팅 전후 유지.
- 시간 캐시: 초기에는 시간 의존 응답 우회; 자정·시간대·일광절약시간 테스트.
- 과도한 비용: 최초 단계 분류용 LLM 호출 0회, 요청별 retrieval/model/tool 호출 수 기록.
- 최신성 착각: asOf는 답변 평가 시점이지 문서 최신성 보증이 아님을 계약에 명시.
- 배포 불일치: 먼저 현재 서버의 오래된 JVM/JAR 문제를 정상화한 후 실제 동작을 평가한다. 이 계획에서 재시작하지 않는다.

단위: fixed Clock/ZoneId/라우팅/캐시/출처 메타데이터.
통합: 팀·문서 모드, 권한 거부, 일반 답변과 RAG 결과, sync/SSE 동등성.
E2E: 날짜 → 문서 발행일 → 오늘 기준 적용 여부의 연속 질문과 화면 출처 확인.
운영: route별 오류율, 근거 유무, 호출 수, p50/p95 지연 측정. 현재 품질 수치를 확인한 것으로 주장하지 않는다.

## 우선 실행 범위

1차는 실행 시각 맥락 + 순수 날짜/시간 경로 + 출처 표시 + 캐시 우회만 구현한다.
2차는 상위 라우팅과 팀 혼합/문서 엄격 모드, 3차는 권한 통제된 실시간 조회 도구다.

## 1차 구현 결과 (2026-09-18)

- core `ChatRuntimeContext`와 Clock/ZoneId 기반 `ChatRuntimeContextService` 추가.
- 서버 설정 `studio.ai.chat.runtime.time-zone`, 기본 `Asia/Seoul`. 사용자별 시간대는 후속 계약이다.
- 일반/RAG 동기 및 SSE에서 whitelist 날짜·시간 질문은 LLM/검색을 거치지 않고 응답한다.
- 시스템 출처/asOf/시간대 전달, 클라이언트 '시스템 기준' 표시 및 잘못된 모델 표시 방지.
- 시간 의존 질의 또는 전달된 사용자 메시지가 있으면 exact-answer cache get/put 우회.
- 기존 object 권한·재생성 소유권 검사는 유지한다. 독립 리뷰의 SSE shortcut 예외 경계 지적을 수정하고 오류 비노출 테스트 추가.
- 서버 21개 클래스 189개 테스트 통과, 프론트 typecheck 및 2개 파일 3개 테스트 통과, diff check 통과.
- full 로컬 서버 재시작 완료. 실제 팀 채팅에 '오늘 날짜 알려줘'를 전송하여 2026-09-18 금요일/Asia-Seoul 및 시스템 출처 표시 확인.
- 로그: `/tmp/studio-runtime-context-20260918.log`. DB 구조 변경·재색인·외부 도구 연결·커밋/PR 없음.
- 혼합 업무 질문의 날짜 주입/계산, 일반 지식/도구 라우팅은 아직 구현하지 않았다.

### 검증 명령

```sh
./gradlew -q :starter:studio-platform-starter-ai-web:test --tests '*ChatRuntimeContextServiceTest' --tests '*ChatController*Test' --tests '*RagAnswer*Test' --tests '*OpenAiProviderAutoConfigurationTest' --tests '*TeamRag*Test'
# frontend
npm run typecheck
npx vitest run src/react/pages/ai/components/AssistantMessageBubble.runtime.test.tsx src/react/pages/teams/TeamChatPanel.test.tsx --pool=threads --maxWorkers=1 --no-file-parallelism
```

## Context Usage Report

### Investigation Summary

| 항목 | 결과 |
|---|---|
| 유형/범위 | 날짜·시간 실행 맥락 및 채팅 4개 경로 연결 |
| 전략 | 기존 경로 부분 읽기 → 순수 서비스 → 회귀/화면 검증 |
| 최종 상태 | 1차 범위 구현 및 검증 완료 |

### Search And CodeGraph Usage

| 항목 | 결과 |
|---|---|
| 검색어 | chatInternal, streamWithRag, writeRagStreamRequest, ragAnswerCacheKey, canonicalContent, normalizeStreamComplete |
| CodeGraph | 이전 controller 맵 재사용 |
| 선택 경로 | 서버 context → controller 단축 경로 → response metadata → 공통 Assistant bubble |

### File Inspection Scope

| 파일/영역 | 범위 | 이유/근거 |
|---|---|---|
| ChatController | Partial | 엔드포인트, 메모리, 캐시, SSE |
| AiWebAutoConfiguration | Partial | Clock/Zone 서비스 주입 |
| core ChatResponseMetadata | Partial | 임의 출처 필드 보존 확인 |
| frontend ChatPage/Bubble/types | Partial | 출처 전달/표시 |
| 새 서비스/테스트 | Full | 시간 경계와 비LLM 처리 검증 |

### Evidence Checked

| 유형 | 결과 |
|---|---|
| 서버 테스트 | 189 passed |
| 프론트 | typecheck 및 3 passed |
| 실제 UI | 팀 채팅 날짜/요일/시간대/출처 확인 |
| 독립 리뷰 | module_dependency_check, 오류 처리 지적 반영 |

### Files Or Areas Intentionally Not Read

| 영역 | 이유 | 위험 |
|---|---|---|
| 문서 parser/DB schema | 변경 없음 | Low |
| 모든 외부 모델 adapter | 직접 날짜 응답에 불필요 | Low |
| 전체 mixed 업무 판단 | 2차 범위 | Medium |

### Original Source Verification

| 항목 | 결과 |
|---|---|
| 원본 소스 | 직접 읽기 및 독립 검토 |
| SQL | 변경/실행 검증 대상 아님 |
| 로그 | 재시작 및 8080 기동 확인 |
| tests/diff | Passed |

### Context Efficiency Metrics

| 항목 | 수 |
|---|---:|
| 신규 핵심 클래스 | 2 |
| 서버 테스트 클래스/케이스 | 21 / 189 |
| 프론트 테스트 파일/케이스 | 2 / 3 |
| 독립 리뷰 | 1 |

### Final Assessment

| 규칙 | 결과 | 설명 |
|---|---|---|
| 검색 후 읽기 | Pass | 기존 경로에서 관련 부분만 확인 |
| 후보 축소 | Pass | 날짜·출처·캐시에 한정 |
| 큰 파일 제한 | Pass | controller 부분 읽기 |
| 제외 범위 기록 | Pass | 혼합 질문·DB 등 명시 |
| 원본 검증 | Pass | 소스/테스트/UI 대조 |

Conclusion: Context usage was controlled. The investigation followed the Large Context Analysis process.
