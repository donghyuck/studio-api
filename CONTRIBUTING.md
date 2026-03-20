# Contributing

## 기본 원칙
- 작은 단위로 변경하고, 변경 이유와 검증 결과를 남긴다.
- 관련 없는 수정은 분리 커밋으로 처리한다.
- 정책성 내용은 `README.md`에 중복 작성하지 않는다.
- 기능 개발 및 버그 수정은 Issue 기반 진행을 원칙으로 하며, Merge Request는 표준 템플릿을 사용한다.
- 긴급 수정이나 저장소 관리자 직접 반영처럼 Issue 생성이 어려운 경우에는 커밋 또는 MR 본문에 변경 배경을 남긴다.
- 이슈/머지리퀘스트 작성 시 저장소 템플릿을 기본으로 사용한다.
  - Issue: `.gitlab/issue_templates/default.md`
  - Merge Request: `.gitlab/merge_request_templates/default.md`
- Issue 템플릿의 `Size`는 `Small/Medium/Large` 중 정확히 하나를 체크한다.
  - `Small (1)`: 단순 수정 / 단일 파일
  - `Medium (2)`: 기능 단위 변경 / 다중 파일
  - `Large (3)`: 구조 변경 / 복수 모듈
- Issue 템플릿의 `AI-Assisted`는 `Yes/No` 중 정확히 하나를 체크한다.
  - AI 사용 작업은 반드시 `Yes`를 체크한다.

## 브랜치/커밋
- 기본 브랜치: `main`
- 작업 브랜치 사용을 권장하며, 직접 `main`에 반영할 때는 변경 배경과 검증 기록을 더 엄격히 남긴다.
- 브랜치 네이밍 규칙:
  - `feature/*`: 기능 추가/개선
  - `bugfix/*`: 일반 버그 수정
  - `hotfix/*`: 운영 긴급 장애/결함 수정
  - `refactor/*`: 동작 변경 없는 구조 개선
  - 예시:
    - `feature/issue-3-vclass-gooroomee`
    - `bugfix/login-null-check`
    - `hotfix/payment-timeout`
    - `refactor/srv-query-structure`
- 커밋은 논리 단위로 분리한다.
- 커밋 타입은 `feat`, `fix`, `refactor`, `test`, `docs`, `chore`를 사용한다.
- AI 보조 커밋은 `AI_DEVELOPMENT_POLICY.md`의 `[ai-assisted]` 규칙을 따른다.
- AI 보조 커밋 작성 시 `.gitmessage-ai-assisted.txt` 템플릿 사용을 권장한다.
- AI 보조 변경은 merge 전에 human review를 반드시 거친다.

## 코드 변경 규칙
- 기존 패턴/설계 의도를 우선 존중한다.
- 리팩터링은 동작 변경과 분리한다.
- 설정/스크립트 변경 시 로컬 재현 절차를 함께 갱신한다.

## 테스트/검증 규칙
- 최소 1개 이상 검증을 수행한다.
- 변경 범위에 맞는 테스트를 우선 실행한다.
- 실행한 명령과 결과를 커밋 본문 또는 PR에 남긴다.

## CHANGELOG 업데이트 규칙
1. 아래 변경은 템플릿 저장소에서 `CHANGELOG.md`를 필수로 갱신한다.
- 기능 추가/수정, 버그 수정, 운영 스크립트 변경, 정책 변경
- DB 관련 변경(쿼리, 스키마, 마이그레이션, 데이터 동기화 절차)
- 검증 절차/테스트 전략 변경

2. 경미한 오탈자/주석 수정만 있을 때는 생략 가능하다.

3. 템플릿 저장소에서는 같은 작업 단위에서 코드 + `CHANGELOG.md`를 함께 커밋한다.

4. 대상 프로젝트에는 `CHANGELOG.md`를 배포하지 않으므로, 정책 변경 이력은 대상 저장소의 커밋/MR/릴리즈 노트로 관리한다.

## 문서 규칙
- `README.md`: 정책 저장소의 목적, 적용 방법, 빠른 시작, 핵심 링크를 설명한다.
- 상세 규칙/절차/강제 기준: 본 문서, `AI_DEVELOPMENT_POLICY.md`, 템플릿 문서에 작성한다.
- 동일 규칙은 여러 문서에 중복 서술하지 않고, 필요 시 참조 링크로 연결한다.
- 정책 파일(규칙/템플릿/절차) 변경 시 `POLICY_VERSION.md`를 같은 작업 단위에서 함께 갱신한다.
- VSCode 워크스페이스 Java snippet은 `.vscode/java.code-snippets`를 기준으로 관리한다 (`addCopyright`, `addDeveloper`).
- 개인 VSCode 적용 템플릿/절차는 `docs/dev/vscode-snippets-guide.md`를 따른다.
