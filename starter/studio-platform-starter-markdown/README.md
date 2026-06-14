# Studio Platform Markdown Starter

기본값은 비활성화다.

```yaml
studio:
  markdown:
    enabled: true
    max-source-bytes: 26214400
    pandoc-version: pandoc-3
    textract-version: native-1
  document-convert:
    enabled: true
```

필수 구성:

- `NamedParameterJdbcTemplate`
- `AttachmentService`
- `FileContentExtractionService`
- `DocumentConvertService`

RAG와 SkillGraph bean이 존재하면 요청 옵션에 따라 후속 파이프라인을 실행한다.

Attachment 추출 요청은 항상 Markdown Revision을 저장하며 후속 단계는 선택 사항이다.

```json
{
  "attachmentId": 42,
  "runChunking": true,
  "runRagIndex": true,
  "runSkillExtraction": false,
  "force": false,
  "chunkingStrategy": "structure-based",
  "chunkMaxSize": 800,
  "chunkOverlap": 100,
  "chunkUnit": "TOKEN",
  "embeddingProfileId": "retrieval",
  "embeddingProvider": null,
  "embeddingModel": null,
  "embeddingDimension": 768
}
```

- Chunking 전략은 `fixed-size`, `recursive`, `structure-based`를 지원한다.
- `embeddingProfileId`를 사용하거나 `embeddingProvider`와 `embeddingModel`을 직접 지정한다.
- `embeddingDimension`을 profile과 함께 보내면 profile dimension과 일치하는지 검증한다.
- RAG를 선택하면 Chunking이 자동 활성화된다.
- Skill 추출을 선택하면 RAG와 Chunking이 자동 활성화된다.
- Markdown 저장 이후 후속 파이프라인 실패는 완료된 Revision을 실패 상태로 되돌리지 않는다.
