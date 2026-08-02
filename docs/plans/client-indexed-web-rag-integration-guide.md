# 클라이언트 Indexed Web RAG 연동 가이드

## 1. 목적

RAG Chat과 첨부 상세 Q&A에서 공개 HTTPS URL을 workspace 자료로 수집하고, 완료된 revision을 질문의
추가 근거로 선택한다. URL을 채팅 본문에 적는 방식이 아니라 별도의 자료 선택 상태로 관리한다.

서버 계약:

- URL 자료 API: `/api/workspaces/{workspaceId}/ai/rag/web-sources`
- RAG 요청 필드: `indexedWebSources`
- 최대 선택 수: `GET /api/ai/chat/rag/capabilities`의 `indexedWeb.maxSelectedSources`
- 선택 가능 상태: 완료된 `currentRevisionId` 또는 `currentCorpusRevisionId`가 있는 source
- embedding 불일치: `409 WEB_SOURCE_EMBEDDING_SPACE_MISMATCH`

## 2. 필수 원칙

### 2.1 임베딩 deployment를 추정하지 않는다

URL source의 `embeddingDeploymentId`는 현재 질문에서 사용할 canonical deployment ID와 정확히 같아야
한다.

```ts
const effectiveDeploymentId = selectedEmbeddingOption?.deploymentId;
```

다음 fallback은 사용하지 않는다.

```ts
// 금지
const effectiveDeploymentId = embeddingDeploymentId || "embedding-default";
```

- 선택된 옵션에 `deploymentId`가 없으면 URL 등록·목록·선택을 비활성화한다.
- `embeddingProfileId`, provider, model 이름으로 deployment ID를 추정하지 않는다.
- 실제 선택된 deployment ID가 `"embedding-default"`일 때만 그 값을 사용한다.
- deployment가 변경되면 기존 웹 자료 선택과 현재 conversation을 초기화한다.

표시 문구:

> 웹 자료를 사용하려면 RAG 임베딩 deployment를 먼저 선택해 주세요.

### 2.2 URL은 채팅 메시지가 아니라 자료 선택으로 전송한다

```json
{
  "chat": {
    "messages": [
      {"role": "user", "content": "문서와 외부 자료의 차이를 비교해줘"}
    ]
  },
  "objectType": "attachment",
  "objectId": "12",
  "embeddingDeploymentId": "embedding-default",
  "indexedWebSources": [
    {
      "sourceId": "wsrc-...",
      "corpusRevisionId": "wcorpus-..."
    }
  ]
}
```

URL 문자열을 user message나 system prompt에 자동으로 추가하지 않는다.

### 2.3 revision 또는 corpus revision을 고정한다

- `SINGLE_PAGE`는 목록 응답의 `currentRevisionId`, `SITE`는 `currentCorpusRevisionId`를 선택 시점에 저장한다.
- 완료 상태이며 해당 모드의 revision ID가 존재하는 source만 선택 가능하다.
- refresh로 current revision이 변경되면 기존 선택을 새 revision으로 조용히 바꾸지 않는다.
- 사용자에게 변경 사실을 보여주고 선택을 갱신한 뒤 새 conversation을 시작한다.

## 3. 공통 화면 구조

`RagChatPage`와 `FileDetailDialog`는 동일한 `RagEvidenceSourcePicker`와 상태 모델을 사용한다.

질문 입력창 주변에는 다음 summary를 항상 표시한다.

```text
참고자료  문서 1개 · 웹 2개   [자료 관리]
```

- 첨부 상세에서는 현재 attachment를 기본 문서 근거로 표시한다.
- 독립 RAG Chat은 선택한 URL source만으로도 질문할 수 있다.
- 선택 자료가 없어도 자료 관리 진입점을 유지한다.
- `+` 아이콘만 단독으로 두지 않는다. 텍스트 label과 선택 수를 함께 표시한다.

자료 관리 화면:

| 서버 상태 | 표시 | 선택 |
|---|---|---|
| `PENDING` | 대기 중 | 불가 |
| `FETCHING` | 수집 중 | 불가 |
| `NORMALIZING` | 본문 정리 중 | 불가 |
| `INDEXING` | 색인 중 | 불가 |
| `COMPLETED` | 사용 가능 | 가능 |
| `UNCHANGED` | 변경 없음·사용 가능 | 가능 |
| `FAILED` | 실패·오류 코드 표시 | 불가 |
| `CANCELLED` | 취소됨 | 불가 |

polling은 활성 작업이 있을 때만 수행하고 화면이 닫히거나 작업이 종결되면 중지한다.

수집 범위는 `이 페이지만(SINGLE_PAGE)`과 `하위 페이지 포함(SITE)`으로 분리한다. capabilities의
`indexedWeb.siteCrawlEnabled=false`이면 기존 단일 페이지 입력만 보여준다. SITE 선택 시에는 등록 전에
`POST /web-sources/preview`를 호출해 첫 hop 후보, 적용 scope, query 제거 수와 limit 경고를 보여주며,
preview를 사이트 전체 페이지 수로 표현하지 않는다.

## 4. workspace 결정

workspace는 다음 순서로 결정한다.

1. 화면에 명시적으로 전달된 `workspaceId`
2. attachment가 workspace 소유 객체이면 attachment의 `objectId`
3. 사용자가 선택한 workspace

workspace 목록 조회가 실패한 경우 오류를 숨기지 않는다.

> workspace 목록을 불러오지 못했습니다. 권한과 연결 상태를 확인해 주세요.

workspace가 확정되지 않으면 URL 입력·등록 버튼과 source 목록을 비활성화한다.

## 5. API 처리

### 5.1 등록과 목록

```http
POST /api/workspaces/{workspaceId}/ai/rag/web-sources
Content-Type: application/json

{
  "url": "https://example.org/docs/",
  "displayName": "참고 자료",
  "embeddingDeploymentId": "embedding-default",
  "collectionMode": "SITE",
  "crawlPolicy": {
    "scope": "PATH_PREFIX",
    "discoveryMode": "SITEMAP_AND_LINKS",
    "maxDepth": 2,
    "maxPages": 50
  }
}
```

성공은 `202 Accepted`다. 응답 source를 목록에 즉시 반영하되 완료 전에는 선택하지 않는다.

```http
GET /api/workspaces/{workspaceId}/ai/rag/web-sources?embeddingDeploymentId={deploymentId}
```

현재 deployment와 호환되는 source만 요청한다. 다른 deployment의 source를 클라이언트에서 호환 처리하지
않는다.

SITE preview:

```http
POST /api/workspaces/{workspaceId}/ai/rag/web-sources/preview
Content-Type: application/json

{
  "url": "https://example.org/docs/",
  "crawlPolicy": {
    "scope": "PATH_PREFIX",
    "discoveryMode": "SITEMAP_AND_LINKS",
    "maxDepth": 2,
    "maxPages": 50
  }
}
```

preview 응답의 URL에는 query 값이 포함되지 않는다. `PREVIEW_FIRST_HOP_ONLY`는 오류가 아니라 bounded
예상 범위임을 나타낸다. preview와 source 생성·갱신·취소·정책 변경은 workspace `UPDATE`, archive는
workspace `ARCHIVE` 권한이 필요하므로 `403`을 단순 네트워크 오류로 표시하지 않는다.

### 5.2 갱신·취소·archive

```text
POST   /web-sources/{sourceId}/refresh
POST   /web-sources/{sourceId}/cancel
GET    /web-sources/{sourceId}/crawl-runs
GET    /web-sources/{sourceId}/crawl-runs/{runId}
GET    /web-sources/{sourceId}/pages
PATCH  /web-sources/{sourceId}/crawl-policy
DELETE /web-sources/{sourceId}
```

- refresh 시작 시 현재 선택 revision은 그대로 유지한다.
- 새 revision이 완료되면 “새 버전 사용” 동작으로 명시적으로 교체한다.
- archive한 source는 선택 목록에서 제거하고 새 conversation을 시작한다.

### 5.3 오류 표시

| HTTP/code | 클라이언트 동작 |
|---|---|
| `400 WEB_SOURCE_REQUEST_INVALID` | URL 또는 deployment 입력 확인 |
| `400 URL_NOT_ALLOWED` | 공개 HTTPS URL만 허용됨을 표시 |
| `400 HOST_NOT_PUBLIC` | 내부·로컬·metadata 주소는 수집할 수 없음을 표시 |
| `404 WEB_SOURCE_NOT_FOUND` | 목록에서 제거하고 권한 또는 archive 가능성 안내 |
| `409 WEB_SOURCE_ALREADY_EXISTS` | 기존 source를 다시 조회 |
| `409 WEB_SOURCE_EMBEDDING_SPACE_MISMATCH` | 선택 해제 후 embedding deployment 재선택 안내 |
| `409 WEB_SOURCE_JOB_ALREADY_ACTIVE` | 현재 진행 상태 polling 유지 |
| `429 WEB_CRAWL_QUOTA_EXCEEDED` | workspace source/page/snapshot 상한 안내 |
| `429 WEB_CRAWL_*_LIMIT_EXCEEDED` | 활성 실행이 끝난 뒤 재시도 |

provider 예외 원문이나 서버 topology를 표시하지 않는다.

## 6. 권한·대화·SSE

- 조회·선택: `features:workspace/read`와 `services:ai_rag/read`
- 등록·refresh·cancel·archive: 추가로 `services:ai_rag/write`
- 쓰기 권한이 없으면 등록과 변경 버튼을 처음부터 비활성화한다.
- canonical URL은 새 창으로 열고 `rel="noopener noreferrer"`를 사용한다.
- source ID 또는 revision ID가 변경되면 새 conversation을 시작한다.
- sync, SSE, RAG regenerate 요청 모두에 현재 선택된 `sourceId`와 고정된
  `currentRevisionId | currentCorpusRevisionId`를 `indexedWebSources`로 전송한다.
- `rag_status` 동안 source 상태만 표시한다.
- `complete.canonicalContent` 이전에는 답변과 근거 링크를 표시하지 않는다.
- `ragReferences[].origin`을 `DOCUMENT | INDEXED_WEB | OFFICIAL_EXTERNAL` badge로 표시한다.
- `sourcePolicy.effectiveScope`는 공식 외부 자료의 실시간 조회 정책이며, 수집한 웹 자료의 선택 여부를
  뜻하지 않는다. 따라서 이 값만으로 `첨부 문서만` badge를 만들지 않는다.
- 최종 자료 범위 표시는 `evidenceSourceSelection`을 기준으로 한다.
  - `documentScopeSelected`: 첨부 문서 scope 선택 여부
  - `indexedWebSourceCount`: 요청에서 고정한 수집 웹 자료 수
  - `packedOrigins`: 최종 prompt에 포함된 근거 origin
  - `usedOrigins`: canonical 답변 또는 근거 후보에 실제 사용된 origin
  - `officialExternalEnabled`: 공식 외부 자료 조회 허용 여부
- 예: `documentScopeSelected=true`, `indexedWebSourceCount=1`,
  `usedOrigins=[INDEXED_WEB]`이면 `첨부 문서 + 수집한 웹 1개 · 답변은 수집한 웹 사용`으로 표시한다.
- 웹 근거는 title, publisher, canonical URL, 기준 시각, 검색 점수, `exactText`를 표시한다.
- `usageStatus=CITED`만 canonical 답변의 inline citation 링크로 활성화한다.
  `RETRIEVED_ONLY`는 `검색된 근거 후보`로 분리해 표시한다.
- `EVIDENCE_ONLY / INSUFFICIENT_SOURCE_COVERAGE`는 일반 검색 실패와 구분한다.

## 7. 현재 클라이언트에서 수정할 파일

| 파일 | 필수 수정 |
|---|---|
| `src/react/pages/ai/components/RagEvidenceSourcePicker.tsx` | `"embedding-default"` fallback 제거, deployment 미확정 시 비활성화 |
| `src/react/pages/ai/components/RagEvidenceSourceDrawer.tsx` | workspace/deployment/capability 오류를 명시적으로 표시 |
| `src/react/pages/ai/components/RagEvidenceSourceSummary.tsx` | 두 Q&A 화면에 실제 적용하고 선택 수 표시 |
| `src/react/pages/ai/RagChatPage.tsx` | deployment·revision 변경 시 conversation 초기화, 요청값 일치 |
| `src/react/pages/files/FileDetailDialog.tsx` | 공통 summary 적용, workspace 결정 실패 표시 |
| RAG API adapter/types | typed source 상태, 오류 code, `indexedWebSources` 계약 유지 |

## 8. 자동 테스트와 완료 기준

필수 테스트:

1. deployment ID가 없으면 URL 등록·목록·선택이 비활성화된다.
2. `"embedding-default"`가 임의로 요청에 삽입되지 않는다.
3. 등록 요청과 RAG 요청의 `embeddingDeploymentId`가 동일하다.
4. `COMPLETED | UNCHANGED` source만 선택할 수 있다.
5. 선택 payload에 source ID와 모드에 맞는 고정 revision/corpus revision ID가 포함된다.
6. deployment/source/revision 변경 시 새 conversation이 시작된다.
7. RAG Chat과 File Detail이 동일한 picker/view model을 사용한다.
8. `409 WEB_SOURCE_EMBEDDING_SPACE_MISMATCH`가 사용자 조치 문구로 변환된다.
9. workspace 목록 실패가 숨겨지지 않는다.
10. 쓰기 권한이 없을 때 변경 버튼이 비활성화된다.
11. 외부 링크에 `noopener noreferrer`가 적용된다.
12. SSE `complete` 전에는 canonical 답변·근거 링크가 활성화되지 않는다.
13. SITE preview가 query 값을 노출하지 않고 first-hop 경고와 제한을 표시한다.
14. run/page 진행률과 truncation/error code가 source 상태와 일치한다.
15. `sourcePolicy=DOCUMENT_ONLY`이더라도 `indexedWebSourceCount > 0`이면 `첨부 문서만`으로 표시하지 않는다.
16. `packedOrigins`와 `usedOrigins`가 다른 경우 선택 범위와 실제 사용 범위를 구분해 표시한다.
17. sync, SSE, regenerate가 같은 `indexedWebSources` snapshot을 전송한다.
18. `CITED` 근거만 inline link로 활성화되고 `RETRIEVED_ONLY`는 후보 영역에 표시된다.

검증:

```text
npm run typecheck
npm test -- --run
npm run build
```

브라우저 smoke test:

```text
embedding deployment 선택
→ workspace 선택
→ URL 등록
→ COMPLETED 확인
→ 웹 자료 선택
→ 질문 요청
→ INDEXED_WEB exact excerpt와 canonical URL 확인
→ deployment 변경
→ 이전 선택 해제와 새 conversation 확인
```
