# Team Chat 품질·성능 개선 계획

## 목표

Team 전체 또는 Workspace 범위 질문에서 관련 자료를 먼저 좁히고, 대화 맥락을 반영한 복수 검색 결과를 전역 정렬하여 근거 기반 답변의 정확도와 체감 응답 속도를 개선한다.

## 범위

- Team 권한 manifest는 요청당 한 번만 해석한다.
- 해석형 질문은 최근 사용자 질문을 포함한 복수 검색어를 사용한다.
- 1차 검색 결과로 관련 object scope를 좁힌 뒤 추가 검색을 수행한다.
- 복수 검색 결과는 중복을 제거하고 reciprocal-rank 기반으로 재정렬한다.
- 여러 object scope 검색은 가능한 경우 object type별 aggregate 검색으로 묶는다.
- 근거가 부족하면 제한된 Team 대표 근거 fallback을 사용하고 한계를 표시한다.
- Team Chat은 기존 SSE 계약을 사용해 검색 상태와 생성 결과를 점진적으로 표시한다.
- 답변 캐시는 Team corpus fingerprint와 permission version을 포함하는 기존 키 계약을 유지한다.

## 비범위

- 외부 모델 또는 embedding 모델 교체
- 권한 모델 변경
- 전체 문서의 무제한 컨텍스트 투입
- 새로운 vector database 도입
- 근거가 없는 일반 지식 답변 허용

## 수용 기준

1. Team 해석형 질문은 원 질문 외에 대화 맥락 검색어를 최대 4개까지 실행한다.
2. 1차 검색에서 관련 자료가 확인되면 추가 검색은 해당 자료 범위로 제한된다.
3. partition 제한이 없는 동일 object type 자료는 단일 aggregate vector/hybrid 검색으로 처리된다.
4. 다중 검색 결과는 object/chunk 중복을 제거하고 안정적으로 재정렬된다.
5. 검색 결과가 없을 때 Team 범위의 대표 근거 fallback 여부가 응답 metadata에 기록된다.
6. Team Chat UI는 동기식 완료 대기 대신 SSE 상태·완료 이벤트를 사용한다.
7. 기존 단일 문서 RAG, Team 권한 필터, citation guard 계약은 유지된다.
8. 서버 집중 테스트와 클라이언트 타입 검사·단위 테스트·빌드가 통과한다.

## 검증 지표

- `ragTiming.retrievalMs`, `generationMs`, `totalMs`
- 실행 검색어 수와 후보/최종 object scope 수
- fallback 적용 여부
- 인용 검증 상태와 `ragAnswerOutcome`
- SSE 상태·완료 이벤트 수신 여부
