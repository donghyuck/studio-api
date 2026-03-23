# AI Development Policy

## 목적
- AI 보조 개발의 품질, 추적 가능성, 책임 범위를 표준화한다.

## 적용 범위
- 본 저장소의 코드, 스크립트, 문서, 설정 변경 전반.
- AI로 작성/수정된 모든 변경은 본 정책의 적용 대상이다.

## 필수 원칙
0. 지침 우선순위 원칙
- AI 작업 시 저장소의 `AGENTS.md` 및 활성화된 `SKILL` 지침을 우선 적용한다.
- 저장소에 `AGENTS.md`가 없으면 본 저장소의 정책 문서와 세션에 주어진 기본 지침을 적용 기준으로 삼는다.
- `SKILL` 지침과 일반 규칙이 충돌하면, 더 구체적이고 범위가 좁은 지침(`SKILL`)을 우선한다.
- `SKILL` 미준수로 발생한 변경은 리뷰에서 반려할 수 있다.

1. 사람 책임 원칙
- 최종 책임은 작성자/리뷰어에게 있으며, AI 출력은 그대로 신뢰하지 않는다.

2. 검증 원칙
- AI 변경은 최소 1개 이상의 실행 가능한 검증(테스트/빌드/스모크)을 남긴다.
- 검증 명령과 결과는 커밋 본문 또는 PR 본문에 기록한다.

3. 보안 원칙
- 비밀정보(API 키, 비밀번호, 토큰, 개인정보)를 프롬프트/로그/문서에 남기지 않는다.
- 민감값은 환경변수/비밀 저장소를 사용한다.

4. 최소 변경 원칙
- 범위를 벗어난 리팩터링/형식 변경을 함께 수행하지 않는다.
- 정책/절차 문서는 단일 소스 문서로 유지하고 중복 서술을 피한다.

5. 이슈 템플릿 기록 원칙
- 이슈 작성 시 `.gitlab/issue_templates/default.md`를 사용한다.
- `Size` 항목은 `Small/Medium/Large` 중 정확히 하나만 체크한다.
- `Size` 기준은 다음과 같다.
  - `Small (1)`: 단순 수정 / 단일 파일
  - `Medium (2)`: 기능 단위 변경 / 다중 파일
  - `Large (3)`: 구조 변경 / 복수 모듈
- `AI-Assisted` 항목은 `Yes/No` 중 정확히 하나만 체크한다.
- AI를 사용한 작업은 반드시 `Yes`를 체크한다.

6. Subagent 위임 원칙
- Subagent는 독립 검토가 가능한 명확한 하위 작업에만 사용한다.
- 위임 전 작업 경계(파일/모듈/책임)를 정의한다.
- Main author는 subagent 산출물을 통합하고 최종 책임을 가진다.
- Main author는 merge 전 subagent 결과를 직접 검증한다.
- Subagent 사용 시 이슈/PR(MR)에 위임 범위와 검증 결과를 기록한다.
- 역할별 agent를 사용하는 경우에도 공통 `SKILL.md`를 먼저 적용하고, 이후 `.codex/agents/*.toml`과 `docs/agents/*.md`를 적용한다.
- `.codex/config.toml`은 선택적 메타데이터이며 정책 강제의 기준이 아니다.
- Subagent를 명시하지 않으면 기존과 동일하게 main agent 기준으로 작업한다.

## AI 커밋 메시지 규칙
1. AI 보조 커밋 제목은 반드시 `[ai-assisted]`로 시작한다.
2. 형식:

```text
[ai-assisted] <type>(<scope>): <summary>
```

예시:
- `[ai-assisted] feat(attachment-egov): add mapper-based dao`
- `[ai-assisted] fix(sql): align image update query`
- `[ai-assisted] docs(contributing): add changelog update rule`

타입:
- `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

## CHANGELOG 업데이트 규칙
1. 아래 조건 중 하나라도 해당하면 같은 작업 단위에서 `CHANGELOG.md`를 함께 갱신한다.
- 사용자 동작/API/DB 스키마/운영 스크립트/개발 프로세스 규칙이 변경된 경우
- 회귀 방지 테스트가 추가되거나 검증 절차가 바뀐 경우

2. 문서 전용 오탈자 수정처럼 사용자 영향이 없는 변경은 생략 가능하다.

3. 항목 구성은 날짜별 섹션 내 `변경됨`/`검증`을 기본으로 한다.

## CI/리뷰 운영 원칙

AI 개발 정책은 문서 규칙과 저장소의 검증 태스크, PR/MR 리뷰 절차를 함께 사용해 운영한다.

### 1. 커밋 메시지 검사
다음 규칙은 리뷰 또는 자동화 수단으로 확인할 수 있다.

- AI 커밋은 `[ai-assisted]` prefix 사용
- 커밋 형식: `<type>(<scope>): <summary>`
- 허용 타입: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

예:
[ai-assisted] feat(user): add login API

### 2. 검증 기록 확인
AI로 생성된 변경은 다음 중 하나 이상의 검증을 포함해야 한다.

- build 실행
- unit test 실행
- smoke test 수행

검증 명령은 커밋 또는 PR 본문에 기록한다.
Subagent를 사용한 경우, 통합 이후 main author의 검증 항목을 포함해 기록한다.

### 3. PR/MR 검사
Pull Request(Merge Request) 단계에서 다음을 확인한다.

- 저장소 검증 태스크 또는 CI build 성공
- 최소 1명 이상의 리뷰 승인
- 정책 위반 커밋 여부

### 4. 보안 검사 권장
AI 생성 코드에 대해 다음 검사를 권장한다.

- dependency vulnerability scan
- static code analysis
- secret scanning

## 문서 관계(중복 방지)
- 정책 버전 기준: `POLICY_VERSION.md`
- 개발 참여 절차: `CONTRIBUTING.md`
- AI 상세 규칙: `AI_DEVELOPMENT_POLICY.md` (본 문서)
- AI 커밋 템플릿: `.gitmessage-ai-assisted.txt`
- VSCode 워크스페이스 Java snippet 기준: `.vscode/java.code-snippets` (`addCopyright`, `addDeveloper`)
- 개인 VSCode 적용 템플릿/절차: `docs/dev/vscode-snippets-guide.md`
