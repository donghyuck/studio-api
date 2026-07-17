# ChunkSet 중심 RAG 모듈화 구현 스펙

## 목표

- Markdown/NormalizedDocument 청킹 결과를 영속 `ChunkSet`으로 저장한다.
- Markdown pipeline의 RAG 단계는 지정된 `ChunkSet`만 임베딩하고 원문 재추출·재청킹을 하지 않는다.
- 임베딩 profile 변경 재색인은 기존 `ChunkSet`을 재사용한다.
- 기존 OCR, normalization, renderer, chunking 전략·fallback·품질 판정은 변경하지 않는다.
- 일반 attachment RAG의 legacy 추출·청킹 fallback은 유지한다.
- MCP와 채팅 기능은 범위에서 제외한다.

## 계약

- `studio-platform-chunking`에 `ChunkSet`, `ChunkSetItem`, `ChunkSetStatus`, `ChunkSetStore`를 추가한다.
- `ChunkSet`은 `chunkSetId`, source revision/content hash, strategy hash, 품질 상태와 chunk item을 보존한다.
- `RagIndexJobSourceRequest`에는 optional `chunkSetId`, `requirePreparedChunks`를 additive하게 추가한다.
- `requirePreparedChunks=true`이면 ChunkSet 부재·불일치·비유효 상태를 실패 처리한다.

## 저장

- `tb_ai_chunk_set`, `tb_ai_chunk_item`을 PostgreSQL/MySQL/MariaDB에 추가한다.
- ChunkSet header와 item은 트랜잭션으로 교체 저장한다.
- 기존 `tb_ai_rag_chunk_stage`는 legacy 호환 경로를 위해 유지한다.

## 품질 비회귀 기준

- 기존 `ChunkingOrchestrator` 입력과 결과는 바꾸지 않는다.
- 생성된 `Chunk`의 text, id, order, metadata를 그대로 `ChunkSetItem`으로 저장한다.
- normalized snapshot, provenance, fallback/quality metadata를 손실하지 않는다.
- ChunkSet 저장 전후 chunk text/hash/order가 동일해야 한다.
- 기존 테스트와 6번 수학 PDF 품질 관련 테스트가 모두 통과해야 한다.

## 완료 조건

- Markdown RAG job metadata에 `chunkSetId`, `ragRechunkApplied=false`가 기록된다.
- RAG 재색인은 같은 `chunkSetId`를 사용한다.
- ChunkSet이 없으면 strict Markdown RAG job은 재청킹하지 않고 명확히 실패한다.
- legacy attachment RAG는 기존 동작을 유지한다.
