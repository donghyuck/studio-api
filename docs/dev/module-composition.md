# 모듈 선택 구성 계약

플랫폼은 기능별 JAR와 starter를 제공하고 실행 서버가 필요한 기능·인프라·provider를 선택한다.
관리자 프런트는 서버 기능을 관리하고 시험하는 API 클라이언트다.

| 기능 | artifact | 필수 구성 | 선택 연결 |
|---|---|---|---|
| 기반 | studio-platform-starter | 사용 경로의 MVC/validation/security/persistence 인프라 | 문서 추출·썸네일 |
| Team | studio-platform-starter-team | identity, JPA, validation/security, user 선행 schema | Company 업무 서비스, Workspace, AI |
| Team Workspace | Team + studio-platform-starter-workspace | Team 활성화, user/team/workspace schema | 첨부파일, Wiki, URL, RAG |
| AI Web | studio-platform-starter-ai-web | MVC, validation/security, JDBC/data-commons, 선택 provider | Team, Workspace, realtime, Micrometer |
| Team RAG | Team + Workspace + AI Web | PrincipalResolver, TeamAuthorizationPort, WorkspaceTreeService, RagPipelineService | 자료별 contributor |

## 의존성 및 호환성

Team은 Workspace artifact를 전이하지 않는다. Team 단독 생성은 `provisionRootWorkspace=false`를 사용한다.
현재 Workspace 관리 API는 Team 기반이며 Team starter와 `studio.features.team.enabled=true`를 함께 구성한다.
공통 starter는 textract/thumbnail을 더 이상 전이하지 않는다. 이전 소비자가 두 기능에 의존했다면
`studio-platform-textract-starter`와 `studio-platform-thumbnail-starter`를 명시적으로 추가한다.
PDFBox/POI/OCR 및 외부 AI provider 라이브러리도 선택한 기능에 맞게 서버에서 명시한다.

AI Web의 Team 연결은 `AiTeamRagAutoConfiguration`으로 격리한다.
필수 클래스와 서비스 Bean이 모두 있을 때에만 Team RAG를 등록한다.
Micrometer가 없는 구성도 별도 조건부 설정으로 지원한다.

## DB 설치 계약

- user → Team → Workspace 순서로 schema 위치를 구성한다.
- Team Company FK 때문에 user schema는 선행 요구다. Company 업무 서비스 활성화는 선택이다.
- 적용된 V1800/V1801은 checksum 호환을 위해 수정하지 않는다.
- H2 create-drop 검증은 실제 PostgreSQL/MySQL/MariaDB migration 검증을 대신하지 않는다.
- 기존 DB schema 테스트는 `-PrunDbTests=true`로 활성화하며 Testcontainers 환경이 필요하다.

## 관리자 기능 가용성

인증된 사용자는 `GET /api/platform/capabilities`에서 `contractVersion: "1"`과
`features: { "team": true, "workspace": false }`를 ApiResponse의 data로 받는다.
각 모듈이 `META-INF/studio/features.properties`에 기능 key와 대표 Controller의 완전한 클래스명을 선언한다.
공통 API는 선언 파일을 합치고 실제 MVC handler 등록 여부로 가용성을 판정한다.
응답에 Controller 이름, URL mapping, 설정값이나 비밀정보는 포함하지 않는다.
중복 key의 서로 다른 Controller 선언은 설치 오류다.

key: team, workspace, attachment, attachment-audit, mail, template, wiki, markdown,
web-knowledge, objecttype, objectstorage, user, group, role, company, acl, login-audit,
ai-chat, ai-rag, ai-vector, team-rag.
독립 document/forum 모듈을 추가하는 서버는 같은 형식으로 `document`, `forum` descriptor를 제공한다.
Controller 교체 시 해당 모듈 descriptor도 구현과 일치시킨다.

가용성은 권한이 아니다. 각 업무 API는 서버 권한 검사를 유지한다.
관리자는 기능 확인 전 하위 화면을 마운트하지 않고, 확인 실패 시 재시도를 안내한다.
Team 단독 구성은 Workspace/RAG 자동 요청을 보내지 않는다.
AI·markdown 조합이 없는 파일 서버에서는 기본 파일 상세를 제공한다.

## 독립 소비자 검증

`bash scripts/verify-modular-consumers.sh`는 임시 Maven 저장소에 artifact를 게시하고
`verification/modular-consumer`에서 7개 구성의 classpath와 자동 설정 context를 검증한다.
소비자는 `includeBuild`나 `project(...)` 의존성을 사용하지 않는다.
외부 AI 포트는 테스트 대역이며 운영 연결·답변 품질은 별도 검증한다.
결과 XML은 출력된 임시 경로에 구성별로 보관한다.

## 최소 RAG와 선택 시각화

`rag-minimal` 검증 조합은 Team/Workspace/AI Web을 함께 사용하면서 시각화를 제외한다.
이는 서비스 포트를 사용하는 독립 소비자 테스트이며, 첨부파일 처리 전체와 실제 DB 설치를 대신하지 않는다.
실행 서버의 실제 파일/URL RAG 구성은 `studio-one-api-server`의 `studioComposition=rag-minimal`로 검증한다.

`studio.ai.vector.projection.enabled=false`는 기본 제공 시각화 생성기, executor, JDBC 저장소,
시각화 서비스와 관리 API를 등록하지 않는다. 일반 벡터/임베딩/RAG API는 유지한다.
속성을 생략하면 기존 full 구성과 동일하게 활성화한다. 직접 등록한 사용자 bean까지 제거하지는 않는다.
`ai-vector`는 기존 벡터 API 가용성이며 새 `ai-vector-visualization`은 시각화 관리 API 가용성이다.
프론트는 새 key가 없으면 시각화 화면을 숨긴다. 서버 capability 변경과 함께 배포해야 한다.

ChatController의 답변 선택·한계 안내·검증 처리는 `RagAnswerProcessingService`에 위임한다.
해당 서비스는 HTTP/Servlet/요청 DTO에 의존하지 않으나 기존 AI Web의 정책·근거 타입을 사용한다.
따라서 이번 작업은 책임 분리의 첫 단계이며 별도 core artifact 분리, 검색·캐시·SSE 전체 이전을 완료한 것은 아니다.

2단계에서는 `RagAnswerCacheService`, `RagContextCandidateService`, `RagStreamCollector`를 추가했다.
캐시 서비스는 이미 권한·사용자 범위를 반영한 key를 전달받아 만료/근거/정책/인용을 검증한다.
후보 확장 서비스는 호출자가 확인한 단일 object scope 내에서만 후보를 가져오며 전역 검색으로 fallback하지 않는다.
스트림 수집기는 provider의 delta를 모으고 리소스를 닫으며, controller가 최종 검증 후 canonical COMPLETE만 전송한다.
provider 스트림 close가 실패하면 완료 처리 전에 오류로 끝내므로 검증/저장과 리소스 종료 경계가 명확해진다.
권한 결정, 캐시 key 생성, 대화 저장, HTTP/SSE 직렬화와 전체 검색 조정은 아직 controller에 남아 있다.

## 현재 날짜·시간 응답

`studio.ai.chat.runtime.time-zone`은 IANA 시간대이며 기본값은 `Asia/Seoul`이다.
사용자별 시간대 계약은 아직 제공하지 않는다. 서버에서 `Clock` bean 또는 `ChatRuntimeContextService`를
교체할 수 있으며, 잘못된 ZoneId는 기동 시 오류로 처리한다.

일반/RAG 채팅 및 SSE 경로에서 명확한 날짜/시간 질문만 서버가 직접 응답한다.
`오늘 날짜 알려줘`, `오늘이 며칠이야?`, `지금 몇 시야?` 등을 지원한다.
문서 발행일·오늘 휴가 가능 여부 같은 혼합 질문은 기존 RAG 경로를 유지한다.
날짜를 모르는 모델에 모든 지식을 주입하거나 검색 실패를 일반 지식으로 전환하지 않는다.
기존 endpoint 권한 검사는 유지되므로 RAG 권한이 없는 사용자가 RAG endpoint로 날짜를 조회할 수는 없다.

응답 metadata는 `answerSource=SYSTEM_CONTEXT`, `asOf`(UTC ISO 시각), `effectiveTimezone`,
`routeReason=EXACT_SYSTEM_TIME_QUERY`, `canonicalContent`, `ragAnswerCache=BYPASS`를 제공한다.
문서 인용이나 사용하지 않은 모델 이름을 만들어 붙이지 않는다. 서버 확정 문장은 SSE delta/complete로
전달하여 기존 일반 채팅 클라이언트와 호환하고, 모델이 생성한 미검증 RAG delta의 차단 정책은 유지한다.
클라이언트는 이 출처를 '시스템 기준'으로 표시한다.

현재/오늘/내일/최근 등의 시간 의존 신호가 현재 질의 또는 전달된 사용자 메시지에 있으면 RAG answer cache를
읽거나 쓰지 않는다. 이는 보수적 어휘 규칙이며 모든 암시적 시간 의존성을 판별하는 범용 분류기는 아니다.
혼합 질문에 현재 시각을 주입하여 업무 판단까지 수행하는 기능과 일반 지식/외부 도구 라우팅은 후속 단계다.
