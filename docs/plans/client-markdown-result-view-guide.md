# 클라이언트 Markdown 결과 보기/다운로드 지시문

## 목적

Markdown 생성 완료 후 사용자가 실제 결과물을 확인할 수 있도록 `Markdown 보기`, `다운로드`, `복사` 기능을 추가한다.

Revision 목록 API의 `markdownText`에 의존하지 말고, 전용 Markdown 결과 API를 사용한다. 목록 조회는 상태와 이력 표시용으로 유지하고, 큰 본문 조회는 사용자가 결과를 열 때만 수행한다.

## 서버 API

### 현재 Revision Markdown 보기

```http
GET /api/markdown-documents/{documentId}/markdown
Accept: text/markdown
```

성공 응답:

```http
200 OK
Content-Type: text/markdown;charset=UTF-8
Content-Disposition: inline; filename*=UTF-8''sample.md
X-Markdown-Document-Id: {documentId}
X-Markdown-Revision-Id: {revisionId}
X-Markdown-Content-Hash: {sha256}

# Markdown body
```

서버는 Markdown 본문을 로컬 파일 캐시에 저장한 뒤 파일을 스트리밍한다. 같은 `X-Markdown-Content-Hash`의
파일이 이미 있으면 다시 쓰지 않고 기존 파일을 재사용한다.

### 특정 Revision Markdown 보기

```http
GET /api/markdown-documents/{documentId}/revisions/{revisionId}/markdown
Accept: text/markdown
```

Revision history 화면에서 과거 결과를 확인할 때 사용한다.

### 다운로드

보기 API에 `download=true`를 붙인다.

```http
GET /api/markdown-documents/{documentId}/markdown?download=true
```

응답은 같은 Markdown 본문이며 `Content-Disposition: attachment`로 내려온다.

### PDF 페이지/영역 미리보기

```http
GET /api/markdown-documents/{documentId}/pages/{page}/preview
Accept: image/png
```

- PDF 원본만 지원하며 `page`는 1부터 시작한다.
- Markdown의 기존 `page[n]/image[m]` 논리 참조는 서버가 위 미리보기 URL로 변환한다. 클라이언트는
  변환된 Markdown을 그대로 렌더링한다.
- 서버는 페이지 이미지와 crop 결과를 Markdown 결과 캐시 경로 아래에 해시 기준으로 저장해 재사용한다.

block provenance의 bbox로 영역을 보여줄 때는 PDF 좌표계의 `x0`, `y0`, `x1`, `y1`를 모두 전달한다.

```http
GET /api/markdown-documents/{documentId}/pages/12/preview?x0=72&y0=144&x1=420&y1=288
Accept: image/png
```

## 오류 처리

| 상태 | 처리 |
|---|---|
| `404 markdown.document.not-found` | 문서 또는 revision을 찾을 수 없음 |
| `409 markdown.content-unavailable` | 아직 완료된 Markdown 본문이 없음 |
| `409 markdown.page-preview-unavailable` | PDF 원본이 아니거나 페이지 렌더링을 제공할 수 없음 |
| `401/403` | 기존 인증/권한 처리 |

`409 markdown.content-unavailable`은 생성 실패가 아니라 아직 본문을 볼 수 없는 상태일 수 있다. Pipeline 상태를 다시 polling하거나 재시도 버튼을 표시한다.

## UI 지시

### 첨부파일 상세

Markdown document가 있고 current revision이 `COMPLETED`이면 다음 액션을 표시한다.

| 버튼 | 동작 |
|---|---|
| `Markdown 보기` | `GET /{documentId}/markdown` 호출 후 viewer modal 또는 tab에 표시 |
| `다운로드` | `GET /{documentId}/markdown?download=true`로 브라우저 다운로드 |
| `복사` | 보기 API로 받은 text를 clipboard에 복사 |

Markdown 생성 중이면 버튼을 비활성화하고 기존 progress를 표시한다.

### Viewer

- `Content-Type`이 `text/markdown`이므로 응답은 JSON으로 파싱하지 않는다.
- 큰 문서가 가능하므로 modal open 시점에 lazy load한다.
- `response.body`가 있으면 `ReadableStream`으로 받아 progressive append한다.
- loading, error, retry 상태를 분리한다.
- preview renderer가 있으면 rendered view와 raw view tab을 제공한다.
- rendered view의 Markdown image URL은 인증이 필요한 서버 URL이다. `<img src>`가 인증 cookie를 보내지 않는
  환경이라면 이미지도 `fetch`로 받아 Blob URL로 렌더링한다.
- locator/provenance의 `page`와 `bbox`가 있으면 `원본 위치 보기` 액션에서 페이지 또는 crop preview를 연다.
- raw view에서는 monospace, line wrap toggle, search를 제공한다.

### Revision History

각 completed revision row에 `보기` 버튼을 추가한다.

```ts
GET /api/markdown-documents/${documentId}/revisions/${revisionId}/markdown
```

현재 revision과 과거 revision을 구분해 표시한다.

## TypeScript 예시

```ts
async function fetchMarkdown(documentId: string, revisionId?: string): Promise<{
  text: string;
  revisionId?: string;
  filename?: string;
}> {
  const path = revisionId
    ? `/api/markdown-documents/${documentId}/revisions/${revisionId}/markdown`
    : `/api/markdown-documents/${documentId}/markdown`;
  const response = await fetch(path, {
    headers: { Accept: "text/markdown" },
  });
  if (!response.ok) {
    throw await response.json().catch(() => new Error(response.statusText));
  }
  return {
    text: await response.text(),
    revisionId: response.headers.get("X-Markdown-Revision-Id") ?? undefined,
    filename: filenameFromContentDisposition(response.headers.get("Content-Disposition")),
  };
}

function downloadMarkdown(documentId: string, revisionId?: string) {
  const path = revisionId
    ? `/api/markdown-documents/${documentId}/revisions/${revisionId}/markdown?download=true`
    : `/api/markdown-documents/${documentId}/markdown?download=true`;
  window.location.assign(path);
}
```

인증 header를 직접 붙이는 클라이언트라면 `window.location.assign` 대신 `fetch -> Blob -> object URL` 방식으로 다운로드한다.

대형 문서 viewer는 다음 방식도 허용한다.

```ts
async function streamMarkdown(
  documentId: string,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
) {
  const response = await fetch(`/api/markdown-documents/${documentId}/markdown`, {
    headers: { Accept: "text/markdown" },
    signal,
  });
  if (!response.ok) {
    throw await response.json().catch(() => new Error(response.statusText));
  }
  const reader = response.body?.getReader();
  if (!reader) {
    onChunk(await response.text());
    return;
  }
  const decoder = new TextDecoder("utf-8");
  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    onChunk(decoder.decode(value, { stream: true }));
  }
  onChunk(decoder.decode());
}
```

## 완료 기준

- Markdown 생성 완료 후 결과 본문을 화면에서 볼 수 있다.
- 결과를 `.md` 파일로 다운로드할 수 있다.
- 결과 본문을 clipboard에 복사할 수 있다.
- 대형 Markdown은 streaming fetch로 점진 표시할 수 있다.
- Revision 목록 조회만으로 큰 `markdownText`를 렌더링하지 않는다.
- `409 markdown.content-unavailable`에서 화면이 깨지지 않고 재시도 또는 progress 확인을 안내한다.
- 특정 revision의 Markdown 결과도 확인할 수 있다.

## 테스트 시나리오

- completed current revision에서 `Markdown 보기`가 본문을 표시한다.
- `download=true`가 파일 다운로드로 동작한다.
- 특정 revision 보기 버튼이 해당 revision 본문을 표시한다.
- running revision에서 `409 markdown.content-unavailable`을 처리한다.
- 인증 만료 시 기존 로그인 만료 흐름으로 이동한다.
- 1MB 이상 Markdown에서도 목록 화면이 느려지지 않고 viewer lazy load가 동작한다.
