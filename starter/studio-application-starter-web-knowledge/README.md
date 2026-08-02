# studio-application-starter-web-knowledge

공개 HTTPS 페이지를 workspace 공유 RAG 자료로 수집하는 Spring Boot starter다. 기본
`SINGLE_PAGE` 동작과 선택적인 bounded `SITE` 수집을 함께 제공한다. 범용 검색엔진, 인증 페이지
수집기, JavaScript browser crawler는 아니다.

## 수집 모드

| 모드 | 동작 | 기본 여부 |
|---|---|---|
| `SINGLE_PAGE` | 등록 URL 한 페이지를 수집·정규화·청킹·색인 | 기본 |
| `SITE` | 동일 origin과 허용 경로 안에서 sitemap과 HTML link를 bounded BFS로 수집 | 기능 플래그 |

`SITE`는 페이지별 revision과 vector partition을 만들고, 성공한 페이지 목록을 하나의
`corpusRevisionId`로 고정한다. refresh가 실패하거나 취소되면 현재 corpus를 교체하지 않는다.
변경되지 않은 페이지는 기존 page revision과 embedding을 재사용한다.

## 필수 구성

```kotlin
implementation(project(":starter:studio-application-starter-web-knowledge"))
implementation(project(":starter:studio-platform-starter-ai"))
implementation(project(":starter:studio-platform-starter-chunking"))
```

`RagPipelineService`, object partition을 지원하는 vector store, canonical embedding deployment,
JPA와 Flyway가 필요하다. partition을 지원하지 않는 backend에서는 `SITE` 생성이
`WEB_SITE_CRAWL_PARTITION_UNSUPPORTED`로 거부된다.

## 권장 개발 설정

```yaml
studio:
  ai:
    indexed-web:
      enabled: true
      max-selected-sources: 10
      crawl:
        site-crawl-enabled: true
        default-max-depth: 2
        maximum-depth: 5
        default-max-pages: 50
        maximum-pages: 500
        default-max-concurrency: 2
        maximum-concurrency: 8
        min-delay-per-origin: 500ms
        max-total-response-bytes: 52428800
        max-total-normalized-chars: 10000000
        max-run-duration: 10m
        max-active-runs-global: 2
        max-active-runs-per-workspace: 1
        max-active-runs-per-principal: 1
      quota:
        max-sources-per-workspace: 100
        max-active-pages-per-workspace: 10000
        max-snapshot-units-per-workspace: 1000000000
    providers:
      google-ai:
        embedding:
          request-timeout: 30s
```

Google GenAI 임베딩 요청은 provider별 `request-timeout`을 SDK 호출 상한으로 적용한다. SITE 수집 상태는
fetch 직후와 색인 직전에 독립 트랜잭션으로 저장되므로 외부 호출이 지연되어도 run의 heartbeat와
`discovered/fetched/indexed` 수치를 조회할 수 있다. 제한 시간과 기존 retry를 모두 소진하면 run은
`WEB_CRAWL_EMBEDDING_TIMEOUT`으로 실패 수렴한다.

운영 rollout에서는 `site-crawl-enabled=false`로 migration과 기존 `SINGLE_PAGE` 회귀를 먼저
확인한 뒤 관리 계정과 작은 `maxPages`부터 활성화한다.

## API

기본 경로는 `/api/workspaces/{workspaceId}/ai/rag/web-sources`다.

| API | 설명 |
|---|---|
| `POST /preview` | 저장 없이 seed, 첫 링크와 sitemap 후보를 bounded preview |
| `POST /` | `SINGLE_PAGE` 또는 `SITE` source 생성 |
| `GET /` | workspace source 목록 |
| `GET /{sourceId}` | current revision/corpus와 최근 실행 집계 |
| `POST /{sourceId}/refresh` | 새 수집 실행 |
| `POST /{sourceId}/cancel` | 활성 실행 취소 요청 |
| `GET /{sourceId}/crawl-runs` | 실행 이력 |
| `GET /{sourceId}/crawl-runs/{runId}` | 실행 상태·중단 사유 |
| `GET /{sourceId}/pages` | 현재 page 상태와 안전한 locator |
| `PATCH /{sourceId}/crawl-policy` | 다음 refresh 정책 갱신 |
| `DELETE /{sourceId}` | source archive 및 RAG 사용 중지 |

preview는 저장·색인을 하지 않으며 첫 hop과 bounded sitemap 후보만 보여준다. 따라서
`PREVIEW_FIRST_HOP_ONLY` 경고는 정상이다. query 값은 응답에 노출하지 않고 제거 건수만 제공한다.

SITE 생성 예:

```json
{
  "url": "https://example.org/docs/",
  "displayName": "제품 문서",
  "embeddingDeploymentId": "embedding-default",
  "collectionMode": "SITE",
  "crawlPolicy": {
    "scope": "PATH_PREFIX",
    "discoveryMode": "SITEMAP_AND_LINKS",
    "maxDepth": 2,
    "maxPages": 50,
    "includePathGlobs": ["/docs/**"],
    "excludePathGlobs": ["/docs/archive/**"]
  }
}
```

RAG 요청에서 SITE source는 mutable source가 아니라 완료된 corpus를 고정한다.

```json
{
  "indexedWebSources": [
    {
      "sourceId": "wsrc-...",
      "corpusRevisionId": "wcorpus-..."
    }
  ]
}
```

## 보안과 운영 계약

- 최초 URL, link, sitemap, redirect마다 HTTPS, DNS와 public IP, origin/path scope를 재검증한다.
- loopback, private, link-local, metadata endpoint, credential URL과 cross-origin redirect를 거부한다.
- robots 규칙, MIME, compressed/decompressed 크기, 응답·정규화·시간 budget을 강제한다.
- 여러 source의 동일 origin 요청도 process-wide delay를 공유한다.
- HTML 원본은 저장하지 않고 정제된 normalized snapshot만 저장한다.
- URL query, 본문, title, principal과 provider topology는 로그·metric label에 남기지 않는다.
- 목록·상세 조회는 workspace `READ`, 등록·preview·refresh·cancel·정책 변경은 workspace `UPDATE`,
  archive는 workspace `ARCHIVE`를 요구한다. 각 API는 별도로 `services:ai_rag/read|write`를 확인한다.
- 저장된 대화를 다시 열 때 base object뿐 아니라 선택했던 모든 `web_source`의 현재 read 권한을
  재검사하고, 하나라도 회수됐으면 답변과 근거를 함께 redaction한다.

DB migration은 PostgreSQL, MySQL, MariaDB용 `V1700`, `V1710`~`V1713`을 포함한다.
기존 `SINGLE_PAGE` 자료는 짧은 결정적 legacy ID로 corpus를 backfill하며 embedding을 재생성하지 않는다.
