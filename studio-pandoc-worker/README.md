# Studio Pandoc Worker

독립 Docker 컨테이너에서 Pandoc 변환을 실행하는 FastAPI 서비스다. 운영 요청은
`sourceUrl`, `uploadUrl`, `callbackUrl`을 사용하며 API 서버의 로컬 파일 경로를 참조하지 않는다.
결과 업로드 인증값은 URL query에 포함하지 않고 `uploadToken` 요청 필드로 전달받아
`X-Upload-Token` 헤더로 전송한다. token과 URL 원문은 로그에 남기지 않는다.

필수 환경 변수:

```text
STUDIO_PANDOC_INTERNAL_TOKEN
STUDIO_PANDOC_CALLBACK_TOKEN
```

개발 환경에서 공유 Docker volume을 사용할 때만 다음 설정을 활성화할 수 있다.

```text
STUDIO_PANDOC_LOCAL_PATH_ENABLED=true
STUDIO_PANDOC_SHARED_VOLUME_ROOT=/shared
```

local path 요청과 URL 요청은 혼합할 수 없으며 모든 경로는 shared volume root 아래로 제한된다.
Markdown/HTML 원격 리소스는 기본적으로 거부하며 필요한 격리 환경에서만
`STUDIO_PANDOC_ALLOW_REMOTE_RESOURCES=true`로 활성화한다.
