# PaddleOCR Korean Text Worker

Page-scoped Korean body OCR worker for `studio.textract.pdf.engines.korean-ocr`.
It returns the structured PDF extraction contract with `page`, `sourceRef`,
`bbox`, and confidence metadata.

```bash
python3.12 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
./run-native.sh
```

The default endpoint is `http://127.0.0.1:8603/extract/pdf` and the health
endpoint is `http://127.0.0.1:8603/health`.

On the Mac mini, install `com.studioone.paddleocr-worker.plist` under
`~/Library/LaunchAgents` to run and recover the worker through `launchd`.
