# 로컬 KURE embedding 서버

KURE embedding은 Hugging Face Text Embeddings Inference(TEI) 컨테이너로 로컬에서 실행한다.
Studio 서버는 `TEI` provider를 통해 `/embed` 엔드포인트를 호출한다.

## 서버 실행

```bash
docker compose -f tools/kure-embedding-server/compose.yaml up -d
```

기본 포트는 `8080`이다. 변경하려면 실행 전에 `KURE_TEI_PORT`를 지정한다.
기본 이미지는 Apple Silicon/ARM64 Docker용 `cpu-arm64-latest`다.
x86_64 Linux에서는 `KURE_TEI_IMAGE_TAG=cpu-1.9`를 지정한다.

```bash
KURE_TEI_PORT=18080 docker compose -f tools/kure-embedding-server/compose.yaml up -d
```

```bash
KURE_TEI_IMAGE_TAG=cpu-1.9 docker compose -f tools/kure-embedding-server/compose.yaml up -d
```

첫 실행은 `nlpai-lab/KURE-v1` model 파일을 내려받기 때문에 시간이 걸릴 수 있다.
KURE-v1은 1024 dimension embedding을 생성한다.
로컬 Docker 메모리를 아끼기 위해 기본 `max-batch-tokens`는 `512`, 기본 client batch size는 `4`로 둔다.
처리량이 필요하고 Docker 메모리가 충분하면 `KURE_TEI_MAX_BATCH_TOKENS`, `KURE_TEI_MAX_CLIENT_BATCH_SIZE`를 높인다.

## Studio 설정

채팅 provider는 기존 Gemini를 유지하고, embedding provider만 KURE로 라우팅한다.

```yaml
studio:
  ai:
    routing:
      default-chat-provider: gemini
      default-embedding-provider: kure
    providers:
      gemini:
        type: GOOGLE_AI_GEMINI
        enabled: true
        chat:
          enabled: true
      kure:
        type: TEI
        enabled: true
        base-url: http://localhost:8080
        embedding:
          enabled: true
          model: nlpai-lab/KURE-v1
    rag:
      default-embedding-profile: retrieval-ko-kure
      embedding-profiles:
        retrieval-ko-kure:
          provider: kure
          model: nlpai-lab/KURE-v1
          dimension: 1024
          supported-input-types: [TEXT, TABLE_TEXT, IMAGE_CAPTION, OCR_TEXT]

spring:
  ai:
    google:
      genai:
        chat:
          api-key: ${GOOGLE_API_KEY}
          options:
            model: gemini-2.5-flash
```

기존 Gemini embedding으로 색인한 vector와 KURE embedding vector는 같은 embedding space가 아니다.
KURE로 검색하려면 같은 profile로 문서를 다시 색인한다.

## 동작 확인

```bash
curl -s http://localhost:8080/embed \
  -H 'Content-Type: application/json' \
  -d '{"inputs":["한국어 문서 검색 테스트"]}'
```

응답은 1024개 숫자를 가진 embedding 배열이어야 한다.
