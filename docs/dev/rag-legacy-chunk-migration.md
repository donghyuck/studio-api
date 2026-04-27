# Legacy RAG chunk metadata 재색인 가이드

이 문서는 embedding profile metadata가 없는 기존 RAG chunk를 운영 중 식별하고 재색인하는 절차를 정리한다.

## 배경

신규 RAG 색인은 가능한 범위에서 아래 metadata를 `VectorRecord`에 기록한다.

- `embeddingProvider`
- `embeddingModel`
- `embeddingDimension`
- `embeddingProfileId`
- `embeddingInputType`

기존 legacy chunk에는 이 metadata가 없을 수 있다. minimal legacy 검색 요청은 호환성을 위해 이 metadata filter를
강제하지 않지만, `embeddingProfileId` 또는 `embeddingProvider`/`embeddingModel`을 명시한 검색은 같은 embedding
space만 조회하기 위해 metadata filter를 적용한다. 따라서 profile 기반 운영으로 전환하기 전에 대상 object를
재색인해야 한다.

## 식별 기준

object scope 단위로 metadata를 확인한다.

```http
GET /api/mgmt/ai/rag/objects/{objectType}/{objectId}/metadata
Authorization: Bearer <token>
```

다음 중 하나라도 없으면 legacy chunk로 취급한다.

- `embeddingModel`
- `embeddingDimension`
- `embeddingInputType`

`embeddingModel=unknown`은 legacy fallback placeholder다. 이 값은 explicit model/profile 검색과 같은
embedding space를 보장하지 않으므로 profile 전환 대상에서는 legacy로 취급한다.

profile 기반 격리가 필요한 운영에서는 다음 값도 확인한다.

- `embeddingProvider`
- `embeddingProfileId`

chunk 단위 확인이 필요하면 page API를 사용한다.

```http
GET /api/mgmt/ai/rag/objects/{objectType}/{objectId}/chunks/page?offset=0&limit=50
Authorization: Bearer <token>
```

## 재색인 절차

### Raw text RAG

원문 text를 알고 있는 경우 신규 job API로 재색인한다.

```http
POST /api/mgmt/ai/rag/jobs
Authorization: Bearer <token>
Content-Type: application/json

{
  "objectType": "article",
  "objectId": "article-123",
  "documentId": "article-123",
  "sourceType": "manual",
  "text": "색인할 본문",
  "embeddingProfileId": "retrieval-ko"
}
```

기존 호환 API를 계속 사용할 수도 있다.

```http
POST /api/mgmt/ai/rag/index
Authorization: Bearer <token>
Content-Type: application/json

{
  "documentId": "article-123",
  "text": "색인할 본문",
  "metadata": {
    "objectType": "article",
    "objectId": "article-123"
  },
  "embeddingProfileId": "retrieval-ko"
}
```

### Attachment RAG

attachment 기반 색인은 attachment 전용 API 또는 source job을 사용한다.

```http
POST /api/mgmt/attachments/{attachmentId}/rag/index
Authorization: Bearer <token>
Content-Type: application/json

{
  "documentId": "{attachmentId}",
  "objectType": "attachment",
  "objectId": "{attachmentId}",
  "embeddingProfileId": "retrieval-ko"
}
```

source job으로 실행할 때는 `sourceType=attachment`를 보낸다. `metadata.attachmentId`는 권장 필드지만,
현재 구현은 값이 없으면 `objectId`를 attachment id로 해석하고 일부 요청에서는 서버가 자동 보강한다.
`forceReindex`는 job 요청 metadata로 보존되는 호환 필드이며, 현재 overwrite 여부를 바꾸는 보호장치가 아니다.
같은 object scope로 색인하면 `RagPipelineService`가 기존 chunk를 교체한다.

```http
POST /api/mgmt/ai/rag/jobs
Authorization: Bearer <token>
Content-Type: application/json

{
  "objectType": "attachment",
  "objectId": "123",
  "documentId": "123",
  "sourceType": "attachment",
  "metadata": {
    "attachmentId": "123"
  },
  "embeddingProfileId": "retrieval-ko"
}
```

## 검증 절차

1. job 기반 재색인을 사용했다면 `GET /api/mgmt/ai/rag/jobs/{jobId}` 응답의
   `data.status`가 `SUCCEEDED` 또는 `WARNING`인지 확인한다.
2. `GET /api/mgmt/ai/rag/jobs/{jobId}/logs` 응답의 `data[]`에서 `level=ERROR` log가 없는지 확인한다.
3. object metadata API 응답의 `data`에 `embeddingProfileId`, `embeddingModel`, `embeddingDimension`,
   `embeddingInputType`이 기록됐는지 확인한다.
4. chunk page API 응답의 `data.items[]`에서 표본 chunk의 metadata를 확인한다.
5. explicit profile 검색을 실행해 `results[]`가 반환되는지 확인한다. `/api/mgmt/ai/rag/search`는
   legacy 호환을 위해 `ApiResponse.data` 래퍼가 아니라 bare search response를 반환한다.

```http
POST /api/mgmt/ai/rag/search
Authorization: Bearer <token>
Content-Type: application/json

{
  "query": "검증 질의",
  "topK": 3,
  "objectType": "attachment",
  "objectId": "123",
  "embeddingProfileId": "retrieval-ko"
}
```

## 운영 화면 권장 표시

운영 화면은 object metadata 또는 chunk metadata에서 아래 상태를 계산해 표시한다.

| 상태 | 조건 | 조치 |
|---|---|---|
| `Profile indexed` | `embeddingProfileId`, `embeddingModel`, `embeddingDimension` 있음 | explicit profile 검색 사용 가능 |
| `Model indexed` | `embeddingModel`이 있고 `unknown`이 아니며 `embeddingDimension` 있음, `embeddingProfileId` 없음 | provider/model 기반 검색은 가능하지만 profile 격리 전환 시 재색인 권장 |
| `Legacy indexed` | `embeddingModel`이 없거나 `unknown`이거나 `embeddingDimension` 없음 | minimal legacy 검색만 권장, profile 전환 전 재색인 필요 |

## 주의사항

- 같은 `objectType`/`objectId`를 재색인하면 기존 chunk는 교체된다. 현재 `forceReindex` 값은 이 동작을 막거나
  변경하지 않는다.
- explicit profile 검색은 legacy metadata가 없는 chunk를 의도적으로 제외할 수 있다.
- legacy chunk까지 함께 검색해야 하는 화면은 `embeddingProfileId`, `embeddingProvider`, `embeddingModel`을 보내지 않는다.
- DB-specific bulk migration script나 외부 queue/worker는 이 범위에 포함하지 않는다.
- 장기적으로는 object metadata 응답에 legacy/profile 상태를 서버가 계산해 주는 diagnostics를 추가할 수 있다.
