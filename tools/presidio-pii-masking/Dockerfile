FROM mcr.microsoft.com/presidio-analyzer:latest

# Install Spacy Korean model (installing spacy again to ensure compatibility with model)
RUN pip install --no-cache-dir spacy==3.7.5 && \
    python -m spacy download ko_core_news_sm

# Copy custom configs
COPY analyzer_config.yaml /app/presidio-analyzer/analyzer_config.yaml
COPY nlp_config.yaml /app/presidio-analyzer/nlp_config.yaml
COPY recognizer_registry_config.yaml /app/presidio-analyzer/recognizer_registry_config.yaml
