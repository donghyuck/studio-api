FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        libgl1 \
        libglib2.0-0 \
        libgomp1 \
        poppler-utils \
        tesseract-ocr \
        tesseract-ocr-kor \
        tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -U pip \
    && pip install --no-cache-dir -r requirements.txt

COPY app ./app

ENV PIX2TEXT_WORKER_HOST=0.0.0.0
ENV PIX2TEXT_WORKER_PORT=8503
ENV PIX2TEXT_MAX_UPLOAD_BYTES=52428800
ENV PIX2TEXT_LANGUAGE=ko,en
ENV PIX2TEXT_DEVICE=cpu
ENV PIX2TEXT_DOWNLOAD_SOURCE=HF
ENV UVICORN_WORKERS=1
ENV LOG_LEVEL=info

EXPOSE 8503

CMD ["sh", "-c", "uvicorn app.main:app --host ${PIX2TEXT_WORKER_HOST} --port ${PIX2TEXT_WORKER_PORT} --workers ${UVICORN_WORKERS} --log-level ${LOG_LEVEL}"]
