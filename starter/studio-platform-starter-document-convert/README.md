# Studio Platform Document Convert Starter

ObjectStorage에 저장된 attachment를 별도 Pandoc worker로 변환하는 비동기 Job 기능을 자동 구성한다.
API 서버는 Pandoc을 직접 실행하거나 파일 본문을 worker에 전달하지 않는다.

```yaml
studio:
  document-convert:
    enabled: true
    callback-base-url: http://studio-api:8080
    callback-token: ${DOCUMENT_CONVERT_CALLBACK_TOKEN}
    worker:
      base-url: http://studio-pandoc-worker:8080
      internal-token: ${PANDOC_WORKER_INTERNAL_TOKEN}
      connect-timeout: 5s
      request-timeout: 30s
    storage:
      signed-url-ttl: 10m
    job:
      max-retry-count: 2
```

원본 파일은 기존 Attachment 저장 방식(database, local, objectstorage)을 그대로 사용한다.
Worker의 `sourceUrl`은 Attachment signed-download URL이며, 변환 결과는 내부 upload endpoint를 통해
원본과 같은 objectType/objectId의 새 Attachment로 저장된다.
결과 업로드 token은 URL query에 포함하지 않고 Worker가 `X-Upload-Token` 헤더로 전달한다.
signed URL과 object key는 Job DB나 로그에 저장하지 않는다.

Worker의 local-path 모드는 Docker Volume을 공유하는 개발 환경 전용이다. 운영에서는 URL 모드만
사용하고 `STUDIO_PANDOC_LOCAL_PATH_ENABLED=false`를 유지한다.
