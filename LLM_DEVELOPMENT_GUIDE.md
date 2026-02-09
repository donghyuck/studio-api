# LLM Development Guide

## 1. Purpose
이 문서는 본 프로젝트를 LLM 기반으로 안정적으로 개발/운영하기 위한 실행 규약이다.
목표는 속도보다 일관성, 재현성, 안전성을 우선하는 것이다.

## 2. Scope
- 대상: 백엔드 코드, 설정, SQL/Flyway, 문서 변경
- 비대상: 임의 UI 개편, 무관 리팩터링, 운영 비밀정보 취급

## 3. Working Contract (Task Input)
작업 시작 전 아래 입력이 없으면 먼저 사용자에게 확인한다.
- 목표: 무엇을 바꾸는지 1문장
- 범위: 변경 가능한 모듈/파일
- 완료 조건: 빌드/테스트/동작 기준
- 제외 조건: 이번 작업에서 하지 않을 것
- 위험도: low/medium/high

## 4. Repository Principles
- 기존 모듈 경계를 존중한다.
- 문제의 근본 원인을 우선 수정하고, 임시 우회는 명시한다.
- 요청되지 않은 리네임/대규모 포맷 변경은 금지한다.
- 기존 패턴(JPA/JDBC, starter 구성, property namespace)을 우선 따른다.

## 5. Do / Don’t
### Do
- 변경 이유를 코드/설정 기준으로 설명한다.
- 수정 파일을 최소화한다.
- 결과 보고에 파일 경로와 검증 명령을 포함한다.

### Don’t
- 무관 파일 수정
- 동작 검증 없이 완료 선언
- 비밀값/토큰/패스워드 문서화
- 대화형/파괴적 git 명령 사용

## 6. Configuration Rules
- 설정 키는 `studio.*` 네임스페이스를 우선 사용한다.
- 환경별 설정은 `application-<profile>.yml`로 분리한다.
- 기본값은 안전한 방향(off 또는 least privilege)으로 둔다.
- 변경 시 영향 모듈과 활성 조건(`enabled`, `@ConditionalOnProperty`)을 함께 점검한다.

## 7. Flyway & Schema Rules
### 7.1 Folder Convention
- DB 스키마 파일 경로:
  - `src/main/resources/schema/<domain>/postgres/`
  - `src/main/resources/schema/<domain>/mysql/`

### 7.2 File Naming
- 최초 베이스라인: `V0__<description>.sql`
- 후속 변경: `V1__...`, `V2__...` (또는 팀 합의한 버전 정책)
- 반복 실행 스크립트: `R__<description>.sql`

### 7.3 Current Server Location Policy
- Flyway `locations`는 도메인별로 명시한다.
- 단일 경로 `classpath:/schema/postgres` 또는 `classpath:/schema/mysql` 방식은 사용하지 않는다.

### 7.4 Migration Safety
- 모듈 간 테이블 의존(FK)이 있으면 존재 여부 가드 또는 실행 순서를 보장한다.
- 파괴적 DDL(drop/rename)은 사전 승인 없이는 금지한다.
- 변경 시 반드시 롤백/복구 시나리오를 기록한다.

## 8. Coding Rules for LLM Tasks
- 신규 코드보다 기존 패턴 재사용을 우선한다.
- API 변경 시 DTO/Controller/Service 경로를 일관되게 반영한다.
- 예외/에러 응답은 기존 프로젝트 규약(`ApiResponse`, 예외 타입)을 따른다.
- 성능/보안 영향이 있는 변경은 근거를 명시한다.

## 9. Validation Gate
최소 검증 기준(작업 유형에 맞게 선택):
- 컴파일: `./gradlew <module>:compileJava`
- 테스트: 변경 모듈의 단위/통합 테스트
- 설정 변경: 대상 profile 부팅 검증
- SQL 변경: Flyway 적용 가능성(파일 경로/명명 규칙/의존성) 점검

검증 실패 시:
1. 실패 원인 분류(내 변경/기존 이슈)
2. 내 변경이면 수정 후 재검증
3. 기존 이슈면 분리 보고 후 영향만 명확히 전달

## 10. Output Template (Required)
작업 완료 보고는 아래 형식을 따른다.

1. Summary
- 무엇을 왜 바꿨는지

2. Changed Files
- 절대경로 또는 저장소 기준 경로 나열

3. Verification
- 실행한 명령
- 성공/실패 결과

4. Risks & Follow-ups
- 남은 리스크
- 후속 권장 작업(선택)

## 11. Decision Tree (Quick)
### Persistence
- 기존 모듈이 JPA 중심이면 JPA 우선
- 기존 모듈이 JDBC 중심이면 JDBC 우선
- 혼합 시 기존 서비스 공개 계약을 깨지 않는 쪽 선택

### API Exposure
- starter 자동구성이 있으면 조건부 빈 방식 유지
- 커스텀 컨트롤러가 있으면 기본 컨트롤러 중복 등록 여부 확인

### DB Changes
- 새 도메인 테이블이면 `schema/<domain>/<db>/V0__...`
- 기존 도메인 수정이면 해당 도메인 다음 버전 파일 추가

## 12. Prompt Template for LLM Sessions
아래 템플릿을 복사해 작업 요청 시 사용한다.

```text
[Goal]
<무엇을 바꿀지 1~2문장>

[Scope]
Allowed: <모듈/파일>
Disallowed: <건드리면 안 되는 영역>

[Constraints]
- Keep changes minimal
- Follow existing project patterns
- Do not modify unrelated files

[Definition of Done]
- <필수 검증 명령 1>
- <필수 검증 명령 2>
- 결과 보고는 Summary/Changed Files/Verification/Risks 형식
```

## 13. Review Checklist
- 요구사항 충족 여부가 명확한가
- 무관 변경이 없는가
- 설정/자동구성 조건이 깨지지 않았는가
- Flyway 경로/버전 규칙을 지켰는가
- 검증 명령과 결과가 재현 가능한가

## 14. Change Management
이 문서는 프로젝트 구조 변화(모듈 추가, 스키마 정책 변경, 빌드 체계 변경) 시 함께 갱신한다.
