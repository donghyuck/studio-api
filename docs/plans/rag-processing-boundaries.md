# RAG 처리 책임 분리 2단계

## 목표와 범위

기존 API·권한·캐시 키 격리를 보존하며 검색 근거 확장, 캐시 조회/저장 검증,
provider 스트림 수집을 HTTP와 독립된 서비스로 이동한다.
권한 범위 결정, 캐시 키 구성, 대화 저장, SSE 직렬화는 controller에 유지한다.
이번 단계는 전체 ChatController 분리를 완료하는 전면 재작성은 아니다.

## 수용 기준

- 캐시 hit의 만료·근거 fingerprint·정책·인용 재검증 유지; 검증 실패 답변 저장 금지.
- 스트림 delta는 검증 전 사용자에게 노출하지 않으며 provider error/예외와 정상 종료를 구분.
- 검색 확장의 동일 object scope·후보 수 상한·실패 시 원래 결과 fallback 유지.
- 새 서비스 직접 테스트와 기존 RAG/cache/stream 회귀 테스트 통과.
- 기존 DB 변경·서버 재시작 없음. Docker 미실행 시 신규 DB 설치를 성공으로 기록하지 않는다.

## 구현 결과

- 최종 검증: 21개 클래스, 152개 테스트 통과. `git diff --check` 통과.
- RagAnswerCacheService: key 생성은 기존 권한 경계에 두고 캐시의 유효성 검사와 저장을 추출.
- RagContextCandidateService: 승인된 단일 자료 안에서만 후보 수 상한을 적용하여 확장.
- RagStreamCollector: provider 이벤트 수집·종료를 HTTP 전송과 분리. 미검증 delta 외부 노출 없음.
- close 예외 시 canonical 답변 확정/저장 전에 실패하도록 종료 순서를 명확히 했다.
- 독립 코드 검토(module_dependency_check)에서 확정 CRITICAL/HIGH 없음. 권장 close 예외 테스트 추가.
- Docker daemon 미실행. 신규 DB 설치는 미검증이며 기존 서버·DB는 변경하지 않았다.
- 코드 변경은 기존 브랜치에서 수행했으며 커밋/PR은 만들지 않았다.

검증 명령:

```sh
./gradlew -q :starter:studio-platform-starter-ai-web:test --tests '*ChatController*Test' --tests '*RagAnswer*Test' --tests '*TeamRag*Test' --tests '*RagStreamCollectorTest' --tests '*RagContextCandidateServiceTest'
git diff --check
```

## Context Usage Report

### Investigation Summary

| 항목 | 결과 |
|---|---|
| 유형/범위 | RAG 업무 책임 분리 2단계 |
| 전략 | 기존 코드 맵 → 대상 메서드 구간 → 서비스 추출 → 직접·통합 테스트 |
| 확인 경계 | 코드 동작, 권한/캐시 key 경계 유지; 실DB 미확인 |

### Search And CodeGraph Usage

| 항목 | 결과 |
|---|---|
| 검색어 | cachedRagAnswer, cacheRagAnswer, contextExpansionCandidates, writeRagStreamEvents, ChatStreamEvent |
| CodeGraph | 앞선 동일 controller 조사 재사용; 추가 호출 없음 |
| 선택 파일 | ChatController, 신규 서비스 3개, 서비스 테스트 3개, 기존 cache/stream domain 타입 |

### File Inspection Scope

| 파일 | 읽기 범위 | 이유/근거 |
|---|---|---|
| ChatController | Partial | 대상 메서드 및 호출부, 다른 업무 처리 유지 |
| RagCachedAnswer/CacheKey | Partial | 기존 TTL/fingerprint 계약 |
| ChatStreamEvent | Full | provider-neutral 이벤트 계약 |
| RagSearchResult | Full | 후보 서비스 입력 타입 |
| 신규 서비스/테스트 | Full | 변경 책임 검증 |

### Evidence Checked

| 유형 | 내용 | 결과 |
|---|---|---|
| 코드 | key/권한/SSE 경계와 이전 메서드 의미 | 확인 |
| 테스트 | 서비스 직접 테스트 및 RAG 회귀 | 실행 |
| DB | docker version | daemon 미실행 |
| diff | whitespace 검증 | 실행 |

### Files Or Areas Intentionally Not Read

| 영역 | 이유 | 위험 |
|---|---|---|
| 전체 provider 구현 | provider 변경 없음 | Low |
| DB migration 전체 | 데이터 변경 없음, 신규 DB는 환경 미충족 | Medium |
| 프론트 | HTTP 계약 변화 없음 | Low |

### Original Source Verification

| 항목 | 방법 | 결과 |
|---|---|---|
| 소스 | 원본 메서드 읽기/독립 검토 | Verified |
| SQL/운영 로그 | 기존 런타임 미변경 | Not verified |
| 테스트/diff | 위 명령 | 별도 최종 결과 기록 |

### Context Efficiency Metrics

| 항목 | 수 |
|---|---:|
| 신규 서비스 | 3 |
| 신규 서비스 테스트 클래스 | 3 |
| 독립 리뷰 | 1 |
| 신규 graph 호출 | 0 |

### Final Assessment

| 규칙 | 결과 | 설명 |
|---|---|---|
| 검색 후 읽기 | Pass | 기존 코드 경로와 좁은 메서드 구간 |
| 후보 축소 | Pass | 3개 책임 경계 |
| 큰 파일 제한 | Pass | controller 부분만 읽기 |
| 부분 읽기 | Pass | 필요 타입만 확인 |
| 제외 범위 기록 | Pass | DB/provider/프론트 |
| 원본 검증 | Pass | 기존 계약과 테스트 대조 |

Conclusion: Context usage was controlled. The investigation followed the Large Context Analysis process.
