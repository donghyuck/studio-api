# PyMuPDF4LLM Worker

`studio-platform-textract`의 선택형 PDF 추출 worker 예시다.
Java 서버는 기본적으로 PDFBox를 사용하고, `studio.textract.pdf.engines.pymupdf4llm.enabled=true`일 때만 이 worker를 호출한다.

## 요구 사항

- Python 3.10 이상
- PyMuPDF4LLM
- FastAPI / Uvicorn
- OCR을 사용할 경우 Tesseract와 `tessdata`

## 로컬 실행

```bash
cd tools/pymupdf4llm-worker
python3 -m venv .venv
. .venv/bin/activate
pip install -U pip
pip install -U pymupdf4llm[ocr,layout]
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

상태 확인:

```bash
curl http://localhost:8000/health
```

PDF 추출 예시:

```bash
curl -X POST http://localhost:8000/extract/pdf \
  -F 'file=@sample.pdf;type=application/pdf' \
  -F 'options={"preserveLayout":true,"tableExtractionRequired":true};type=application/json'
```

## Docker

```bash
cd tools/pymupdf4llm-worker
docker build -t studio-pymupdf4llm-worker .
docker run --rm -p 8000:8000 studio-pymupdf4llm-worker
```

OCR을 켜는 예:

```bash
docker run --rm -p 8000:8000 \
  -e PYMUPDF4LLM_OCR_ENABLED=true \
  -e PYMUPDF4LLM_OCR_LANGUAGE=kor+eng \
  studio-pymupdf4llm-worker
```

## 환경 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `PYMUPDF4LLM_WORKER_HOST` | `0.0.0.0` | Uvicorn bind host |
| `PYMUPDF4LLM_WORKER_PORT` | `8000` | Uvicorn bind port |
| `PYMUPDF4LLM_MAX_UPLOAD_BYTES` | `52428800` | worker upload 제한 |
| `PYMUPDF4LLM_OCR_ENABLED` | `false` | OCR 요청 허용 여부 |
| `PYMUPDF4LLM_OCR_LANGUAGE` | `kor+eng` | OCR 언어 표현 |
| `TESSDATA_PREFIX` | unset | Tesseract `tessdata` 위치 |
| `UVICORN_WORKERS` | `1` | Uvicorn worker 수 |
| `LOG_LEVEL` | `INFO` | Uvicorn log level |

## Java 설정

```yaml
studio:
  features:
    textract:
      enabled: true
  textract:
    pdf:
      engine: auto
      fallback-enabled: true
      engines:
        pdfbox:
          enabled: true
        pymupdf4llm:
          enabled: true
          endpoint: http://localhost:8000/extract/pdf
          timeout: 60s
          max-file-size: 50MB
      auto:
        prefer-pymupdf4llm-when:
          min-pages: 3
          table-detection-required: true
          ocr-required: true
          preserve-layout: true
```

## 응답 계약

`POST /extract/pdf`는 다음 필드를 반환한다.

- `markdown`: PyMuPDF4LLM Markdown 출력
- `pages`: page text와 page metadata
- `blocks`: Markdown에서 파생한 heading, paragraph, table, list block
- `tables`: Markdown table 후보
- `images`: PyMuPDF page image reference
- `metadata`: PDF metadata와 page count
- `warnings`: OCR disabled 같은 부분 경고
- `elapsedMs`: worker 처리 시간
- `ocrApplied`: OCR 적용 여부

## 운영 주의 사항

- PDF 원문, 추출 본문, token, secret은 로그로 남기지 않는다.
- worker timeout이나 장애가 발생해도 Java 설정의 `fallback-enabled=true`이면 PDFBox fallback이 동작한다.
- `PYMUPDF4LLM_MAX_UPLOAD_BYTES`와 Java의 `studio.textract.pdf.engines.pymupdf4llm.max-file-size`를 같은 값으로 맞춘다.
- OCR은 Tesseract 설치와 `tessdata` 품질에 영향을 받으므로 운영 이미지에서 언어 데이터를 명시적으로 관리한다.
