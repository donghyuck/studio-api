# Studio Platform Markdown

Attachment를 안정적인 Markdown 지식 원본으로 변환하고 Revision 이력을 관리한다.

## 처리 방식

- DOCX/HTML은 `studio-platform-document-convert` application port를 통해 Pandoc 변환 Job을 생성한다.
- PDF/PPTX/Image/HWP/HWPX/Text는 `studio-platform-textract` 결과를 Markdown으로 저장한다.
- native 추출과 후속 Chunking/RAG/Skill 처리는 commit 이후 background executor에서 실행한다.
- 생성 요청은 `RUNNING` Revision을 먼저 반환하므로 클라이언트가 처리 중 상태를 조회할 수 있다.
- Pandoc 결과 Attachment와 Revision의 `markdownText`를 모두 보존한다.
- 성공한 Revision만 `MarkdownDocument.currentRevisionId`로 승격한다.
- 동일 원본 hash, extractor/version, options 조합의 완료 Revision은 재사용한다.
- 후속 Chunking/RAG/Skill 실패는 완료된 Markdown Revision 상태를 되돌리지 않는다.
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

지원 단계는 `CHUNKING`, `RAG_INDEX`, `SKILL_EXTRACTION`이다. 추출 단계가 완료되지 않았으면
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

`pipeline`은 신규 Revision에 대해 요청 단계가 없더라도 최종 `COMPLETED` 실행 정보를 저장하고
항상 non-null 응답을 반환한다. 실행 이력이 없던 기존 완료 Revision은 상태를 추측하지 않고
`UNKNOWN`, `errorCode=PIPELINE_HISTORY_UNAVAILABLE`로 반환한다.

## 응답 규약

- 성공 응답: `application/json`의 `{ "success": true, "data": ... }`
- 실패 응답: `application/problem+json`의 RFC 7807 `ProblemDetails`
- 변환 이력이 없는 `by-attachment` 조회: `404 Not Found`

클라이언트는 성공 응답만 `data`를 unwrap하고 오류 응답은 HTTP status와 `detail`, `code`,
`traceId`를 처리한다.

기존 Attachment, Document Convert, RAG API와 `objectType/objectId` 범위는 변경하지 않는다.
Markdown starter가 활성화되면 RAG object metadata의 `attachment/{attachmentId}` 응답에
`markdown` 상태가 추가된다. 기존 vector metadata와 `indexed` 필드는 유지된다.
