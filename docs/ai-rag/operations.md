# AI/RAG 운영 및 문제 해결

이 문서는 개발·운영 인스턴스에서 AI/RAG 준비 상태, 품질 문제와 cache 장애를 확인하는 순서를 제공한다.
원문, 질문, credential, 개인정보를 로그에 남기지 않는다.

## 기동 전 확인

1. 모든 Studio artifact가 같은 3.x 버전인지 확인한다.
2. CHAT과 EMBEDDING 논리 deployment가 `ModelDeploymentRegistry`에 등록됐는지 확인한다.
3. embedding deployment의 model, dimension, input type이 기존 index와 같은지 확인한다.
4. PostgreSQL에 pgvector와 AI schema migration이 적용됐는지 확인한다.
5. `studio.features.ai.enabled`와 필요한 endpoint 설정을 확인한다.
6. attachment/Markdown을 사용하면 textract, chunking, source adapter Bean을 확인한다.

Provider API key는 환경변수나 secret store로 공급한다. repository, Actuator 응답, 예외 detail에
credential 또는 provider topology를 노출하지 않는다.

## 단계별 진단

### 1. Provider와 deployment

`GET /api/ai/info/providers`에서 활성 provider와 기본 deployment를 확인한다.

- deployment ID가 `unknown`이면 요청·저장 계약을 먼저 확인한다.
- 모델 capability와 workload가 맞지 않으면 catalog/deployment 설정을 수정한다.
- embedding 선택을 찾을 수 없을 때 default로 조용히 대체되는지 확인한다.

### 2. 색인

RAG job API에서 현재 단계와 로그를 확인한다.

- `CHUNKING`: normalized source와 청킹 설정 확인
- `EMBEDDING`: provider quota, deployment, dimension 확인
- `INDEXING`: DB schema, vector column dimension, object scope 확인
- `COMPLETED`: chunk/vector count와 warning 확인

원문 chunk를 확인할 때는 vector search 대신 chunk inspection API를 사용하면 query embedding provider를
호출하지 않는다.

### 3. Retrieval

- 요청의 `objectType`/`objectId`가 색인 metadata와 같은지 확인한다.
- 질의에 사용한 embedding deployment가 index의 embedding identity와 같은지 확인한다.
- `topK`, `minScore`, retrieval diagnostics와 실제 결과 수를 함께 확인한다.
- minScore를 임의의 고정값으로 올리지 말고 평가 세트로 조정한다.
- overview 질의와 특정 사실 질의를 구분한다.

### 4. Evidence packing과 답변

- 검색 결과가 있어도 `PackedEvidenceSet.evidence`가 비어 있는지 확인한다.
- context 문자/chunk 제한으로 gold evidence가 제외됐는지 확인한다.
- reference의 `exactText`가 해당 normalized chunk에 실제로 포함되는지 확인한다.
- `citationValidationStatus`가 `INDEX_VALID`인지 확인한다.
- SSE 화면이 draft가 아니라 `complete.canonicalContent`를 표시하는지 확인한다.

## 대표 증상

| 증상 | 우선 확인 | 일반적인 원인 |
|---|---|---|
| 근거 부족 응답 | packed evidence 수, object scope | 검색 결과 없음, 패킹 한도, 권한 범위 |
| 관련 문서를 찾지 못함 | embedding identity, topK/minScore | 다른 embedding space, 과도한 cutoff |
| 근거 excerpt가 비어 있음 | source span, normalized chunk | legacy metadata, 잘못된 locator |
| 인용 번호가 링크되지 않음 | final validation, SSE complete | draft 표시, invalid citation |
| 재색인 후 옛 내용 검색 | replace/delete 결과 | stale object chunk |
| embedding model이 unknown | deployment ID와 저장 metadata | legacy 요청 key, 불완전한 모델 설정 |
| 파일 상세가 느림 | metadata 조회 query | vector 목록을 상세 API에서 함께 조회 |
| Redis 중단 시 RAG 실패 | fail-open 설정과 로그 | cache 예외가 provider 경로로 전파됨 |

## Exact-answer cache

기본값은 `none`이다. Redis 승격은 다음 순서를 따른다.

1. cache `none`에서 sync/SSE canonical parity를 확인한다.
2. Redis ACL/TLS와 namespace를 확인한다.
3. 같은 principal/object/evidence 요청에서 `MISS → HIT`를 확인한다.
4. revision 또는 evidence 변경 후 `MISS`인지 확인한다.
5. Redis를 중단해도 provider 경로가 정상 응답하는지 확인한다.

상세 절차는 [Redis RAG cache rollout](../dev/redis-rag-cache-rollout.md)을 따른다.

## 품질 평가 최소 조건

- 같은 source revision과 normalized snapshot
- 같은 chunking 설정
- 같은 embedding deployment와 dimension
- 같은 topK/minScore
- 같은 chat model과 prompt version
- 정답 근거가 표시된 평가 질문

최소한 retrieval recall, evidence packing 유지율, citation support precision, citation coverage,
답변 불가 질의 abstention을 함께 측정한다. 모델 또는 embedding이 다른 결과는 순수 청킹 A/B로 해석하지 않는다.

## 로그 기준

허용:

- request/job/document/revision ID
- hash와 길이
- 단계, duration, count
- provider/model의 논리 ID
- error code와 error type

금지:

- 원문과 질문 전문
- exact excerpt와 sourceRef
- API key, token, password
- 이메일, 전화번호, 결제·정부 식별자
- provider base URL과 내부 topology
