# Pix2Text Math OCR Worker

`studio-platform-textract`의 optional math document OCR worker다. PDF 수학 문서가 analyzer에서
`MATH_LIKE` 또는 `MIXED`로 판정되고 `studio.textract.pdf.engines.math.enabled=true`이면 Java 서버가
이 worker를 먼저 호출한다. 실패하면 기존 PyMuPDF4LLM/OCR heuristic 경로로 fallback한다.

## 로컬 Docker 실행

```bash
cd tools/pix2text-worker
docker build -t studio-pix2text-worker .
docker run -d --name studio-pix2text-worker -p 8503:8503 \
  -e PIX2TEXT_LANGUAGE=ko,en \
  -e PIX2TEXT_DEVICE=cpu \
  -e PIX2TEXT_ORT_PROVIDERS=CPUExecutionProvider \
  -e PIX2TEXT_MAX_UPLOAD_BYTES=52428800 \
  studio-pix2text-worker
```

상태 확인:

```bash
curl http://localhost:8503/health
```

PDF 추출 예시:

```bash
curl -X POST http://localhost:8503/extract/pdf \
  -F 'file=@sample.pdf;type=application/pdf' \
  -F 'options={"language":"ko,en","pageFrom":1,"pageTo":2};type=application/json'
```

## Java 설정

```yaml
studio:
  textract:
    pdf:
      engines:
        math:
          enabled: true
          provider: pix2text
          pix2text:
            endpoint: http://localhost:8503/extract/pdf
            timeout: 5m
            max-file-size: 50MB
            language: ko,en
```

## 환경 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `PIX2TEXT_WORKER_HOST` | `0.0.0.0` | Uvicorn bind host |
| `PIX2TEXT_WORKER_PORT` | `8503` | Uvicorn bind port |
| `PIX2TEXT_MAX_UPLOAD_BYTES` | `52428800` | upload 제한 |
| `PIX2TEXT_LANGUAGE` | `ko,en` | Pix2Text language hint |
| `PIX2TEXT_DEVICE` | `cpu` | Pix2Text 실행 device hint |
| `PIX2TEXT_ORT_PROVIDERS` | `CPUExecutionProvider` | ONNX Runtime 공급자 우선순위. 동적 레이아웃 모델의 CoreML 오류를 피하려면 CPU로 고정한다. |
| `PIX2TEXT_DOWNLOAD_SOURCE` | `HF` | 모델 다운로드 source |
| `UVICORN_WORKERS` | `1` | Uvicorn worker 수 |
| `LOG_LEVEL` | `info` | Uvicorn log level |

## 응답 계약

`POST /extract/pdf`는 Java `Pix2TextMathDocumentOcrClient`가 읽는 다음 필드를 반환한다.

- `markdown`: Pix2Text Markdown 출력
- `plainText`: Markdown과 동일한 fallback text
- `blocks`: Markdown line 기반 block 후보
- `metadata.extractionEngine`: `pix2text`
- `metadata.mathOcrProvider`: `pix2text`
- `metadata.mathMarkdownEngine`: `pix2text`
- `metadata.mathMarkdownQuality`: `VALID`
- `elapsedMs`: worker 처리 시간
- `ocrApplied`: `true`

## 운영 주의 사항

- 첫 추출 시 Pix2Text 모델 다운로드와 초기화 때문에 시간이 오래 걸릴 수 있다.
- 모델 cache를 유지하려면 운영 환경에서 volume mount를 추가한다.
- PDF 원문과 추출 본문은 로그로 남기지 않는다.
