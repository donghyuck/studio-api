# 문서 메타데이터와 RAG 근거 운영 가이드

## 계약

- 문서 처리 프로필, 문서 의미 유형, Blockify 유형은 서로 독립적인 값이다.
- `AUTO`는 요청값이며 감지 결과로 저장하지 않는다.
- 문서 의미 유형은 metadata schema, metadata intent, 명시적 검색 filter에만 사용한다.
- 청킹 기본값과 기존 embedding identity는 metadata 처리로 변경하지 않는다.
- 정확한 원문은 원본 binary byte가 아니라 현재 revision의 normalized chunk에 포함된 연속 문자열이다.

## 설정

서버별 설정은 logical deployment와 prompt 위치만 가진다.

```yaml
studio:
  markdown:
    metadata:
      llm-deployment-id: ${MARKDOWN_METADATA_LLM_DEPLOYMENT_ID:chat-default}
      prompt-resource: classpath:prompts/document-metadata.v1.prompt
      type-confidence-threshold: 0.80
      field-confidence-threshold: 0.85
      max-pages: 5
      max-blocks: 40
      max-characters: 24000
```

선택한 deployment는 `workload=CHAT`, `structuredOutput=true`여야 한다. Provider URL이나
실제 model topology는 metadata API에 노출하지 않는다.

## API

- `GET /api/document-metadata/schemas`
- `GET /api/markdown-documents/{id}/metadata?revisionId=...`
- `POST /api/mgmt/markdown/metadata-backfill-jobs`
- `GET /api/mgmt/markdown/metadata-backfill-jobs`
- `GET /api/mgmt/markdown/metadata-backfill-jobs/{jobId}`
- `GET /api/mgmt/markdown/metadata-backfill-jobs/{jobId}/items`
- `POST /api/mgmt/markdown/metadata-backfill-jobs/{jobId}/retry`
- `POST /api/mgmt/markdown/metadata-backfill-jobs/{jobId}/cancel`

Metadata 조회에는 `features:markdown/read`와 `features:attachment/read`가 모두 필요하다. Backfill 조회에는
`features:markdown/manage`, 생성·retry·cancel에는 추가로 `services:ai_rag/write`가 필요하다.

## Backfill

1. 현재 배포와 동일한 설정으로 `DRY_RUN` job을 만든다.
2. `PARTIAL_NO_NORMALIZED_SOURCE`, `BLOCKED_MODEL_CONFIGURATION`,
   `SKIPPED_REVISION_CHANGED` 항목을 검토한다.
3. 같은 settings fingerprint와 dry-run job ID로 `APPLY`를 만든다.
4. 실패 item만 retry하고 embedding deployment/space와 vector 수가 변하지 않았는지 확인한다.

Backfill은 현재 completed revision을 갱신하고 새 content revision을 만들지 않는다. Normalized
snapshot이 없으면 원본 재추출을 시작하지 않는다. Metadata patch를 지원하지 않는 vector 저장소는
자동 재임베딩하지 않는다. PostgreSQL vector 저장소는 기존 행의 compact document metadata key만
JSONB 병합하며 text, embedding, dimension, row identity는 변경하지 않는다. Canonical embedding
model/space identity가 없으면 `BLOCKED_MODEL_CONFIGURATION`으로 남긴다.

## RAG와 SSE

`PackedEvidenceSet`은 prompt context, 번호가 부여된 evidence, source spans, diagnostics와 context
fingerprint의 단일 원본이다. Citation index는 응답마다 1부터 시작하고 `evidenceId`는 안정적인
식별자다. Context expansion은 previous/seed/next chunk를 합치더라도 각 chunk ID와 locator를
별도 span으로 유지한다.

SSE delta는 검증 전 draft다. 클라이언트는 링크를 활성화하지 않고 `complete` 이벤트의
`canonicalContent`, validated references, validation status로 draft를 교체해야 한다. 대화 메모리에도
canonical content만 저장한다.

## 배포 검증

- 모든 개발 인스턴스에 동일한 logical deployment ID와 prompt 설정을 반영한다.
- DB migration 적용 후 서버를 업그레이드한다.
- `DRY_RUN` 결과를 검토한 다음에만 `APPLY`를 수행한다.
- Citation 범위 오류, packed evidence/API reference 불일치, excerpt substring 불일치는 모두 0이어야 한다.
- Metadata 상세 조회는 vector 조회 없이 수행되어야 한다.
