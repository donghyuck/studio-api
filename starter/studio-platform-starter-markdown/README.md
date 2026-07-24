# Studio Platform Markdown Starter

기본값은 비활성화다.

```yaml
studio:
  markdown:
    enabled: true
    max-source-bytes: 64M
    pandoc-version: pandoc-3
    pandoc-formats: [docx, html]
    fallback-to-native-on-pandoc-failure: true
    result-cache-dir: var/lib/app/markdown
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

Markdown 생성은 내부적으로 `NormalizedDocument` snapshot을 먼저 만들고, 그 snapshot을 Markdown으로 렌더링해
`MarkdownRevision.markdownText`에 저장한다. Snapshot은 schema 변경 없이 `MarkdownResource`의
`resourceType=NORMALIZED_DOCUMENT`와 `metadataJson`으로 저장되며, 청킹 단계는 snapshot blocks를 우선
사용하고 없으면 기존 Markdown/locator 기반 입력으로 fallback한다.

청킹이 완료되면 같은 chunk text/id/order/metadata를 영속 `ChunkSet`으로 저장한다. Markdown에서 시작된
RAG 색인은 job에 기록된 `chunkSetId`만 사용하며, 임시 stage가 삭제된 뒤 재색인하더라도 원문을 다시
추출하거나 청킹하지 않는다. 지정된 ChunkSet이 없거나 scope·품질 상태가 유효하지 않으면 해당 RAG
단계는 실패한다. 일반 attachment RAG의 기존 추출/청킹 fallback은 이 strict 계약의 적용 대상이 아니다.

`pandoc-formats`는 비동기 Pandoc 변환 경로를 사용할 source format allowlist다. 기본값은 기존 동작과
같은 `docx`, `html`이며, Pandoc submit 또는 conversion 실패 시 `fallback-to-native-on-pandoc-failure`
가 켜져 있으면 같은 Revision을 native 추출로 전환한다.

`max-source-bytes`는 숫자만 쓰면 byte로 해석하고, `64M`, `64MB`, `67108864` 같은 값을 지원한다.

`result-cache-dir`는 Markdown 결과 보기/다운로드 API가 사용할 로컬 파일 캐시 위치다. 서버는
Revision의 content hash가 바뀌지 않으면 기존 `.md` 캐시 파일을 재사용하고, hash가 달라지거나 파일이
없으면 DB의 `markdownText`를 로컬 파일로 저장한 뒤 스트리밍 응답에 사용한다.

Normalized provenance 확인:

- `GET /api/markdown-documents/{id}/locators`
- `GET /api/markdown-documents/{id}/provenance`

두 API는 기존 locator table 결과에 더해 `NORMALIZED_DOCUMENT` snapshot의 block provenance를
`locatorType=NORMALIZED_BLOCK`으로 반환한다. 각 항목의 `metadataJson`에는 가능한 경우 `page`,
`sourceRef`, `bbox`, `blockId`, `blockType`, `order`가 포함된다.

수학 문서 전용 OCR은 `studio.textract.pdf.engines.math`에서 설정한다. 기본값은 비활성화이며,
Pix2Text self-host PoC를 먼저 사용하고 필요하면 provider만 Mathpix로 교체할 수 있다.

```yaml
studio:
  textract:
    pdf:
      engines:
        math:
          enabled: true
          provider: pix2text # pix2text | mathpix | none
          pix2text:
            endpoint: http://localhost:8503/extract/pdf
            timeout: 5m
            language: ko,en
          mathpix:
            app-id: ${MATHPIX_APP_ID:}
            app-key: ${MATHPIX_APP_KEY:}
```

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

- `chunkingStrategy`를 생략하거나 `documentProfile=AUTO`를 사용하면 normalized block의 구조를 검사해
  `structure-based` 또는 `recursive`를 자동 선택한다.
- 명시적 Chunking 전략은 호환성을 위해 `fixed-size`, `recursive`, `structure-based`, `blockify`,
  `knowledge-block`을 지원한다. `fixed-size`는 최종 fallback/관리 용도이고 `blockify`와
  `knowledge-block`은 opt-in 실험 기능이므로 자동 선택하지 않는다.
- 자동 선택 결과와 근거는 `chunkingStrategySelectionMode`, `chunkingStrategySelectionReason`,
  `selectedChunkingStrategy`, `structuredBlockCount` metadata에 기록한다.
- `embeddingProfileId`를 사용하거나 `embeddingProvider`와 `embeddingModel`을 직접 지정한다.
- `embeddingDimension`을 profile과 함께 보내면 profile dimension과 일치하는지 검증한다.
- RAG를 선택하면 Chunking이 자동 활성화된다.
- Skill 추출을 선택하면 RAG와 Chunking이 자동 활성화된다.
- Markdown 저장 이후 후속 파이프라인 실패는 완료된 Revision을 실패 상태로 되돌리지 않는다.

Blockify/IdeaBlock 품질 확인:

- `GET /api/markdown-documents/{id}/revisions/{revisionId}/ideablocks/summary`
- 응답에는 `coverage`, `ideaBlockCount`, `fallbackCount`, `fallbackReasonCounts`,
  `missingSourceBlocks`, `detectedDocumentType`, `blockifyProfile`, `ideaBlockSchemaVersion`,
  `typedFieldCounts`, `typedFieldCoverage`, `samples`가 포함된다.
- `samples`는 실제 저장된 IdeaBlock의 `criticalQuestion`, `trustedAnswer`, `sourceEvidence`,
  `sourceBlockRange`, `typedFields`, merge candidate 정보를 보여준다.
