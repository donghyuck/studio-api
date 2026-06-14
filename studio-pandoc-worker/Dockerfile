FROM python:3.11-slim-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
    pandoc \
    texlive-xetex \
    texlive-latex-recommended \
    texlive-latex-extra \
    texlive-lang-cjk \
    fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --create-home --uid 10001 worker
WORKDIR /app
COPY pyproject.toml ./
COPY app ./app
RUN pip install --no-cache-dir .
RUN mkdir -p /tmp/pandoc-work && chown -R worker:worker /app /tmp/pandoc-work

USER worker
EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
