# Content Embedding Pipeline

첨부파일 텍스트를 추출하고 임베딩을 생성해 벡터 스토어 업서트 또는 RAG 인덱싱을 수행하는 모듈이다.

## 사용 요약
- attachment 모듈과 함께 사용한다.
- 임베딩 생성에는 `EmbeddingPort` 빈이 필요하다.
- 벡터 저장에는 `VectorStorePort` 빈이 필요하다.
- RAG 인덱싱에는 `RagPipelineService` 빈이 필요하다.

## 제공 기능
- 첨부 본문 텍스트 추출
- 임베딩 생성
- 벡터 존재 여부 확인
- RAG 인덱스 등록과 검색

## 전제 조건
- `FileContentExtractionService`
- `EmbeddingPort`
- 선택: `VectorStorePort`
- 선택: `RagPipelineService`

## 문서 바로가기
- 모듈 인덱스: `../README.md`
- 루트 개요: `../../README.md`
