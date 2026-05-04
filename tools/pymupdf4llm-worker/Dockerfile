FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-kor tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

ENV PYMUPDF4LLM_WORKER_HOST=0.0.0.0
ENV PYMUPDF4LLM_WORKER_PORT=8000
ENV PYMUPDF4LLM_MAX_UPLOAD_BYTES=52428800
ENV PYMUPDF4LLM_OCR_ENABLED=false
ENV PYMUPDF4LLM_OCR_LANGUAGE=kor+eng
ENV UVICORN_WORKERS=1
ENV LOG_LEVEL=INFO

EXPOSE 8000

CMD ["sh", "-c", "uvicorn app.main:app --host ${PYMUPDF4LLM_WORKER_HOST} --port ${PYMUPDF4LLM_WORKER_PORT} --workers ${UVICORN_WORKERS} --log-level ${LOG_LEVEL}"]
