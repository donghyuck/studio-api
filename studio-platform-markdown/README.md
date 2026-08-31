# Studio Platform Markdown

Attachment를 안정적인 Markdown 지식 원본으로 변환하고 Revision 이력을 관리한다.

전체 AI/RAG 흐름은 [AI/RAG 아키텍처 가이드](../docs/ai-rag/README.md), 문서 metadata 계약은
[studio-platform-document-metadata](../studio-platform-document-metadata/README.md)를 참고한다.

## 처리 방식

- DOCX/HTML은 `studio-platform-document-convert` application port를 통해 Pandoc 변환 Job을 생성한다.
- PDF/PPTX/Image/HWP/HWPX/Text는 `studio-platform-textract` 결과를 Markdown으로 저장한다.
- native 추출과 후속 Chunking/RAG/Skill 처리는 commit 이후 background executor에서 실행한다.
- 생성 요청은 `RUNNING` Revision을 먼저 반환하므로 클라이언트가 처리 중 상태를 조회할 수 있다.
- Pandoc 결과 Attachment와 Revision의 `markdownText`를 모두 보존한다.
- 성공한 Revision만 `MarkdownDocument.currentRevisionId`로 승격한다.
- 동일 원본 hash, extractor/version, options 조합의 완료 Revision은 재사용한다.
- 후속 Chunking/RAG/Skill 실패는 완료된 Markdown Revision 상태를 되돌리지 않는다.
- 명시적인 후속 단계 순서는 `METADATA_ENRICHMENT -> CHUNKING -> RAG_INDEX -> SKILL_EXTRACTION`이다.
- metadata enrichment는 native·구조 기반 값을 우선하고 설정된 모드에 따라 조건부 LLM 보강을 사용한다.
- Markdown RAG 색인은 `RagIndexJobService`를 통해 실행하며 기존 RAG Job 이력과 로그에
  `sourceType=markdown-revision`으로 기록한다.

## API

- `POST /api/markdown-documents/from-attachment`
- `GET /api/markdown-documents/{id}`
- `GET /api/markdown-documents/by-attachment/{attachmentId}`
- `GET /api/markdown-documents/{id}/revisions`
- `GET /api/markdown-documents/{id}/pipeline`
- `GET /api/markdown-documents/{id}/locators`
- `GET /api/markdown-documents/{id}/resources`
- `GET /api/markdown-documents/{id}/metadata?revisionId=...`
- `GET /api/markdown-documents/{id}/metadata/summary?revisionId=...`
- `POST /api/markdown-documents/{id}/metadata/reextract?revisionId=...`
- `GET /api/document-metadata/schemas`
- `POST /api/markdown-documents/{id}/reextract`
- `POST /api/markdown-documents/{id}/resume`
- `POST /api/markdown-documents/{id}/rag/reindex`
- `DELETE /api/markdown-documents/{id}/extraction`

`resume` 요청 body를 생략하면 저장된 pipeline 상태를 기준으로 자동 재개한다. 특정 단계부터
수동 재실행하려면 다음과 같이 요청한다.

```json
{
  "fromStage": "RAG_INDEX"
}
```

지원 단계는 `METADATA_ENRICHMENT`, `CHUNKING`, `RAG_INDEX`, `SKILL_EXTRACTION`이다.
추출 단계가 완료되지 않았으면
`resume`은 native 추출을 다시 실행하며, 실패한 Pandoc 추출은 새 Revision과 변환 Job으로 재시작한다.

embedding 모델만 변경해 다시 색인하려면 `rag/reindex`를 사용한다.

```json
{
  "embeddingProfileId": "retrieval-ko-kure",
  "runSkillExtraction": false
}
```

`embeddingProfileId` 대신 `embeddingProvider`, `embeddingModel`, `embeddingDimension`을 지정할 수 있다.
이 API는 기존 Markdown 본문과 locator/resource를 복제한 새 Revision을 생성하고, 기존 chunking 설정으로
`CHUNKING -> RAG_INDEX`를 다시 실행한다. 원본 Attachment 추출과 Pandoc 변환은 실행하지 않는다.

Markdown 생성, 재추출, resume, RAG reindex 요청은 다음 선택 항목을 지원한다.

- `useLlmKeywordExtraction`: RAG 색인 중 LLM keyword extraction 사용 여부
- `documentSemanticType`: `AUTO`, `GENERAL`, `BOOK`, `ACADEMIC_PAPER`, `THESIS`, `REPORT`,
  `POLICY`, `MANUAL`, `PRESENTATION`
- `metadataEnrichmentMode`: `OFF`, `AUTO`, `REQUIRED`
- `skillExtractionMode`: Skill 후보 추출 방식(`regex`, `llm`). 생략하면 서버의
  `studio.skillgraph.extraction.mode` 설정을 사용한다.
- `generateSkillEmbeddings`: Skill 후보 추출 완료 후 후보 embedding 생성 여부
- `skillEmbeddingProvider`, `skillEmbeddingModel`, `skillEmbeddingDimension`: Skill 후보 embedding 설정

Skill 후보를 추출하는 생성형 LLM 사용 여부는 요청별 옵션이 아니라
`studio.skillgraph.extraction.mode=llm` 서버 설정으로 결정된다. `skillEmbedding*` 값은
Skill 추출 LLM이 아니라 추출된 후보의 후속 embedding에만 사용된다.

`pipeline`은 신규 Revision에 대해 요청 단계가 없더라도 최종 `COMPLETED` 실행 정보를 저장하고
항상 non-null 응답을 반환한다. 실행 이력이 없던 기존 완료 Revision은 상태를 추측하지 않고
`UNKNOWN`, `errorCode=PIPELINE_HISTORY_UNAVAILABLE`로 반환한다.

## 문서 metadata와 backfill

metadata artifact는 revision별 `DOCUMENT_METADATA` resource로 한 번 저장한다. vector에는 전체 artifact가
아니라 `docMetadataId`, 의미 유형, 제목, 제한된 저자, 발간 연도, 조직, bounded `docKeywords`,
`docSummary`만 projection한다.
RAG metadata 질의에는 source-verified field만 사용한다.

현재 완료 revision의 요약·키워드 보강만 다시 시도하려면 `metadata/reextract`를 사용한다. 이 API는 기존
artifact fingerprint가 같아도 LLM enrichment를 강제로 다시 실행하고 성공 결과와 bounded vector metadata를
갱신한다. 새 revision, Markdown 변환, chunking, embedding은 실행하지 않으며 Markdown manage와 AI RAG write
권한을 모두 요구한다. 과거 revision ID를 지정한 요청은 현재 문서 결과를 덮어쓰지 않도록 거부한다.
Google GenAI 요청에는 `application/json` MIME을 사용한다. 일반 enrichment는 감지된 문서 유형의 metadata
field를 허용하고, 재추출은 `summary`·`keywords`만 허용한 response schema와 문서 주 언어를 전달한다.
요약은 80단어 이내, 키워드는 3~8개로 제한하며 최대 출력은 4,096 tokens로 제한한다.
Provider가 JSON 앞뒤에 설명을 추가해도 문자열 내부 중괄호를 보존하면서 첫 번째 완결된 JSON object만 파싱한다.
모델 설정 오류는 `error.markdown.metadata.model-configuration`, provider 호출 실패는
`error.markdown.metadata.upstream-unavailable`, JSON/schema 해석 실패는
`error.markdown.metadata.invalid-response`로 구분해 반환한다. 로그에는 deployment와 cause type만 남기며
원문 dossier, provider message, credential은 기록하지 않는다.

원문 언어 summary·keywords는 canonical metadata로 유지한다. 사용자가 한국어 표시를 요청하면 다음 API가
summary·keywords만 한국어로 번역해 별도 `DOCUMENT_METADATA_TRANSLATION_KO` resource에 저장한다.

- `GET /api/markdown-documents/{id}/metadata/translations?revisionId=...&language=ko`
- `POST /api/markdown-documents/{id}/metadata/translations?revisionId=...&language=ko`

번역 cache key는 revision, source artifact, source summary·keywords hash, target language로 구성한다. 원문이
한국어면 provider를 호출하지 않으며, 같은 source hash의 POST는 저장본을 반환한다. 번역본은 표시 전용으로
vector projection, chunking, embedding, RAG evidence, 추천 질문 keyword를 변경하지 않는다.

기존 완료 revision은 관리 API로 metadata-only backfill할 수 있다.

- `POST /api/mgmt/markdown/metadata-backfill-jobs`
- `GET /api/mgmt/markdown/metadata-backfill-jobs`
- `GET /api/mgmt/markdown/metadata-backfill-jobs/{jobId}`
- `GET /api/mgmt/markdown/metadata-backfill-jobs/{jobId}/items`
- `POST /api/mgmt/markdown/metadata-backfill-jobs/{jobId}/retry`
- `POST /api/mgmt/markdown/metadata-backfill-jobs/{jobId}/cancel`

backfill은 normalized snapshot이 있는 현재 완료 revision만 처리하며 새 revision, chunk 또는 embedding을
자동 생성하지 않는다. 변경 작업은 Markdown manage와 AI RAG write 권한을 모두 요구한다.

## 응답 규약

- 성공 응답: `application/json`의 `{ "success": true, "data": ... }`
- 실패 응답: `application/problem+json`의 RFC 7807 `ProblemDetails`
- 변환 이력이 없는 `by-attachment` 조회: `404 Not Found`

클라이언트는 성공 응답만 `data`를 unwrap하고 오류 응답은 HTTP status와 `detail`, `code`,
`traceId`를 처리한다.

기존 Attachment, Document Convert, RAG API와 `objectType/objectId` 범위는 변경하지 않는다.
Markdown starter가 활성화되면 RAG object metadata의 `attachment/{attachmentId}` 응답에
`markdown` 상태가 추가된다. 기존 vector metadata와 `indexed` 필드는 유지된다.
