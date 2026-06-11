# SkillGraph Phase 1 Client Changes

## 목적

PR #510의 Phase 1 변경은 SkillGraph projection/cluster 결과를 어떤 조건으로 생성했는지 추적하고, cluster 구성원을 별도로 조회할 수 있게 하는 API 확장이다.

기존 projection 생성, 목록, point, cluster 조회 흐름은 유지된다. 다만 projection 생성 요청 필드와 projection/cluster 응답 필드가 늘었고, cluster member 조회 API가 새로 추가됐다.

기본 base path:

```text
/api/mgmt/skillgraph/visualization
```

## Projection 생성 요청

Endpoint:

```http
POST /api/mgmt/skillgraph/visualization/projections
```

권한:

```text
features:skillgraph/manage
```

요청 필드:

| Field | Type | Required | Description |
|---|---:|---:|---|
| `projectionId` | string | no | projection 식별자. 미지정 시 서버가 생성한다. |
| `limit` | number | no | projection 대상 최대 개수. 서버 상한이 적용된다. |
| `skillType` | string | no | projection 대상 Skill Type 필터. 미지정 시 전체 대상이다. |
| `projectionType` | string | no | projection 용도. 예: `CLUSTERING`, `VISUALIZATION`. |
| `reductionAlgorithm` | string | no | 차원 축소 알고리즘. 예: `PCA`, `UMAP`. |
| `projectionDimension` | number | no | projection 차원. |
| `clusteringAlgorithm` | string | no | clustering 알고리즘. 예: `HDBSCAN`. |
| `embeddingProvider` | string | no | embedding provider 필터. |
| `embeddingModel` | string | no | embedding model 필터. |
| `embeddingDimension` | number | no | embedding dimension 필터. |
| `parameters` | string | no | 실행 파라미터 문자열. 최대 4000자. |

지원 `skillType`:

```text
CONCEPT_SKILL
TECH_SKILL
TOOL_SKILL
TASK_SKILL
DOMAIN_SKILL
SOFT_SKILL
UNKNOWN
```

예시 요청:

```json
{
  "projectionId": "task-kure-pca",
  "limit": 1000,
  "skillType": "TASK_SKILL",
  "projectionType": "CLUSTERING",
  "reductionAlgorithm": "PCA",
  "projectionDimension": 2,
  "clusteringAlgorithm": "HDBSCAN",
  "embeddingProvider": "kure",
  "embeddingModel": "KURE-v1",
  "embeddingDimension": 1024,
  "parameters": "{\"minClusterSize\":8,\"minSamples\":3}"
}
```

응답은 즉시 projection 결과가 아니라 batch job 정보다.

예시 응답:

```json
{
  "jobId": "projection_generation_...",
  "jobType": "PROJECTION_GENERATION",
  "status": "CREATED",
  "totalCount": 1200,
  "requestedCount": 1200,
  "processedCount": 0,
  "resultCount": 0,
  "failedCount": 0,
  "skippedCount": 0,
  "embeddingProvider": "kure",
  "embeddingModel": "KURE-v1",
  "embeddingDimension": 1024,
  "requestSnapshot": "{...}",
  "errorMessage": null,
  "createdBy": null,
  "createdAt": "2026-06-01T00:00:00Z",
  "startedAt": null,
  "updatedAt": "2026-06-01T00:00:00Z",
  "completedAt": null
}
```

## Projection 목록

Endpoint:

```http
GET /api/mgmt/skillgraph/visualization/projections
```

권한:

```text
features:skillgraph/read
```

응답 항목에 실행 조건 metadata가 추가된다.

주요 필드:

| Field | Type | Description |
|---|---:|---|
| `projectionId` | string | projection 식별자 |
| `itemCount` | number | projection point 수 |
| `clusterCount` | number | cluster 수 |
| `algorithm` | string | clustering algorithm |
| `skillType` | string or null | projection 대상 skill type |
| `jobId` | string or null | projection 생성 job id |
| `projectionType` | string or null | projection 용도 |
| `reductionAlgorithm` | string or null | 차원 축소 알고리즘 |
| `projectionDimension` | number or null | projection 차원 |
| `embeddingProvider` | string or null | embedding provider |
| `embeddingModel` | string or null | embedding model |
| `embeddingDimension` | number or null | embedding dimension |
| `metadata` | string or null | 실행 metadata 문자열 |
| `createdAt` | string | 생성 시각 |
| `updatedAt` | string | 갱신 시각 |

클라이언트는 projection 목록에서 다음과 같은 실행 조건을 표시할 수 있다.

```text
TASK_SKILL / kure / KURE-v1 / PCA / HDBSCAN
```

`skillType`이 `null`인 기존 projection은 "전체" 또는 "미지정"으로 표시한다.

## Projection Points

Endpoint:

```http
GET /api/mgmt/skillgraph/visualization/projections/{projectionId}/points?clusterId={clusterId}
```

권한:

```text
features:skillgraph/read
```

응답 항목:

| Field | Type | Description |
|---|---:|---|
| `projectionId` | string | projection 식별자 |
| `skillId` | string | skill 식별자 |
| `x` | number | x 좌표 |
| `y` | number | y 좌표 |
| `clusterId` | string or null | cluster 식별자 |
| `displayOrder` | number | 표시 순서 |
| `createdAt` | string | 생성 시각 |

주의:

`clusterId`는 projection별로 고유한 서버 생성 ID다. 클라이언트에서 직접 조합하거나 추측하지 말고, cluster 목록 응답에서 받은 값을 그대로 사용한다.

## Cluster 목록

Endpoint:

```http
GET /api/mgmt/skillgraph/visualization/projections/{projectionId}/clusters
```

권한:

```text
features:skillgraph/read
```

응답 항목:

| Field | Type | Description |
|---|---:|---|
| `clusterId` | string | cluster 식별자 |
| `label` | string or null | cluster label |
| `algorithm` | string | clustering algorithm |
| `itemCount` | number | cluster member 수 |
| `skillType` | string or null | cluster의 skill type |
| `jobId` | string or null | 생성 job id |
| `clusterLabel` | number or null | algorithm cluster label |
| `representativeSkillIds` | string[] | 대표 skill id 목록 |
| `centroidProjectionId` | string or null | centroid에 가까운 대표 skill id |
| `confidence` | number or null | cluster confidence |
| `metadata` | string or null | cluster 실행 metadata 문자열 |
| `createdAt` | string | 생성 시각 |

클라이언트 표시 권장:

- cluster label/name
- skill type
- member count
- representative skill ids
- confidence
- clustering algorithm

## Cluster Members

Endpoint:

```http
GET /api/mgmt/skillgraph/visualization/projections/{projectionId}/clusters/{clusterId}/members
```

권한:

```text
features:skillgraph/read
```

새로 추가된 cluster 상세용 API다.

응답 항목:

| Field | Type | Description |
|---|---:|---|
| `clusterId` | string | cluster 식별자 |
| `skillId` | string | skill 식별자 |
| `embeddingId` | string or null | embedding 식별자 |
| `projectionId` | string | projection 식별자 |
| `membershipScore` | number | membership score |
| `distanceToCentroid` | number | cluster 중심과의 거리 |
| `representative` | boolean | 대표 skill 여부 |

용도:

- cluster 상세 화면에서 포함 skill 목록 표시
- 대표 skill 강조 표시
- 중심점에 가까운 순서로 정렬/표시
- representative skill badge 처리

## 기존 Representatives API

Endpoint:

```http
GET /api/mgmt/skillgraph/visualization/projections/{projectionId}/clusters/{clusterId}/representatives
```

기존 API는 유지된다.

다만 cluster 상세에서 전체 member 목록이 필요하면 새 `/members` API를 우선 사용한다. `/representatives`는 category draft 생성 흐름과 연결된 대표 skill 조회 용도로 유지한다.

## 클라이언트 작업 체크리스트

- Projection 생성 폼에 `skillType`, `projectionType`, `projectionDimension`, `parameters` 입력을 추가한다.
- Projection 목록 화면에 실행 조건 필드를 표시한다.
- Cluster 목록 화면에 `representativeSkillIds`, `confidence`, `metadata`를 표시한다.
- Cluster 상세 화면 또는 drawer에서 `/members` API를 호출해 member skill 목록을 보여준다.
- `clusterId`는 서버 응답값을 그대로 사용한다.
- `skillType`이 `null`인 projection/cluster는 "전체" 또는 "미지정"으로 표시한다.
- `parameters`는 문자열로 전송하되, JSON 형태로 입력받는 UI라면 invalid JSON 여부를 클라이언트에서도 사전 검증하는 것을 권장한다.

