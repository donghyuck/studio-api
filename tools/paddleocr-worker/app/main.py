from __future__ import annotations

import json
import os
import tempfile
import threading
import time
from pathlib import Path
from typing import Any

import fitz
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from paddleocr import PaddleOCR
from PIL import Image

app = FastAPI(title="Studio Korean PaddleOCR Worker", version="1.0.0")

MAX_UPLOAD_BYTES = int(os.getenv("PADDLEOCR_MAX_UPLOAD_BYTES", str(50 * 1024 * 1024)))
MAX_PAGES_PER_REQUEST = max(1, int(os.getenv("PADDLEOCR_MAX_PAGES_PER_REQUEST", "8")))
MAX_RENDER_PIXELS = max(1_000_000, int(os.getenv("PADDLEOCR_MAX_RENDER_PIXELS", "25000000")))
DEFAULT_DPI = max(96, min(300, int(os.getenv("PADDLEOCR_DPI", "180"))))
LANGUAGE = os.getenv("PADDLEOCR_LANGUAGE", "korean")

_engine: PaddleOCR | None = None
_engine_lock = threading.Lock()


def engine() -> PaddleOCR:
    global _engine
    if _engine is None:
        with _engine_lock:
            if _engine is None:
                _engine = PaddleOCR(
                    lang=LANGUAGE,
                    text_detection_model_name="PP-OCRv5_mobile_det",
                    text_recognition_model_name="korean_PP-OCRv5_mobile_rec",
                    text_det_limit_side_len=1600,
                    use_doc_orientation_classify=False,
                    use_doc_unwarping=False,
                    use_textline_orientation=False,
                )
    return _engine


def bounded_page_range(page_count: int, options: dict[str, Any]) -> tuple[int, int]:
    page_from = max(1, int(options.get("pageFrom") or 1))
    page_to = min(page_count, int(options.get("pageTo") or page_count))
    if page_from > page_count or page_to < page_from:
        raise HTTPException(status_code=400, detail="Invalid PDF page range")
    if page_to - page_from + 1 > MAX_PAGES_PER_REQUEST:
        raise HTTPException(status_code=400, detail="PDF page range exceeds configured limit")
    return page_from, page_to


def render_scale(page: fitz.Page, dpi: int) -> float:
    requested_scale = dpi / 72.0
    requested_pixels = page.rect.width * requested_scale * page.rect.height * requested_scale
    if requested_pixels <= MAX_RENDER_PIXELS:
        return requested_scale
    return requested_scale * (MAX_RENDER_PIXELS / requested_pixels) ** 0.5


def result_payload(result: Any) -> dict[str, Any]:
    payload = getattr(result, "json", result)
    if callable(payload):
        payload = payload()
    if isinstance(payload, str):
        payload = json.loads(payload)
    if isinstance(payload, dict) and isinstance(payload.get("res"), dict):
        return payload["res"]
    return payload if isinstance(payload, dict) else {}


def bbox_from_polygon(polygon: Any) -> list[float] | None:
    if polygon is None:
        return None
    points = np.asarray(polygon, dtype=float)
    if points.ndim != 2 or points.shape[0] < 2 or points.shape[1] < 2:
        return None
    return [
        float(points[:, 0].min()),
        float(points[:, 1].min()),
        float(points[:, 0].max()),
        float(points[:, 1].max()),
    ]


def recognize(image: Image.Image) -> list[tuple[str, float | None, list[float] | None]]:
    results = engine().predict(np.asarray(image.convert("RGB")))
    lines: list[tuple[str, float | None, list[float] | None]] = []
    for result in results or []:
        payload = result_payload(result)
        texts = payload.get("rec_texts") or []
        scores = payload.get("rec_scores") or []
        polygons = payload.get("rec_polys") or payload.get("dt_polys") or []
        for index, text in enumerate(texts):
            value = str(text).strip()
            if not value:
                continue
            score = float(scores[index]) if index < len(scores) else None
            polygon = polygons[index] if index < len(polygons) else None
            lines.append((value, score, bbox_from_polygon(polygon)))
    return lines


@app.get("/health")
def health() -> dict[str, Any]:
    return {"status": "UP", "provider": "paddleocr", "language": LANGUAGE}


@app.post("/extract/pdf")
async def extract_pdf(
    file: UploadFile = File(...),
    options: str = Form("{}"),
) -> dict[str, Any]:
    started = time.monotonic()
    try:
        request_options = json.loads(options or "{}")
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail="Invalid options JSON") from exc
    if not isinstance(request_options, dict):
        raise HTTPException(status_code=400, detail="Options must be a JSON object")

    total = 0
    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as temporary:
        temp_path = Path(temporary.name)
        try:
            while chunk := await file.read(1024 * 1024):
                total += len(chunk)
                if total > MAX_UPLOAD_BYTES:
                    raise HTTPException(status_code=413, detail="PDF exceeds configured upload limit")
                temporary.write(chunk)
        except Exception:
            temp_path.unlink(missing_ok=True)
            raise

    try:
        with fitz.open(temp_path) as document:
            page_count = document.page_count
            page_from, page_to = bounded_page_range(page_count, request_options)
            dpi = max(96, min(300, int(request_options.get("dpi") or DEFAULT_DPI)))
            blocks: list[dict[str, Any]] = []
            pages: list[dict[str, Any]] = []
            markdown_pages: list[str] = []

            for page_number in range(page_from, page_to + 1):
                page = document.load_page(page_number - 1)
                scale = render_scale(page, dpi)
                pixmap = page.get_pixmap(matrix=fitz.Matrix(scale, scale), alpha=False)
                image = Image.frombytes("RGB", (pixmap.width, pixmap.height), pixmap.samples)
                page_lines: list[str] = []
                page_blocks: list[dict[str, Any]] = []
                for line_index, (text, confidence, bbox) in enumerate(recognize(image)):
                    source_ref = f"page[{page_number}]/ocr/line[{line_index}]"
                    metadata: dict[str, Any] = {
                        "sourceRef": source_ref,
                        "engine": "paddleocr",
                    }
                    if bbox is not None:
                        metadata["bbox"] = bbox
                    if confidence is not None:
                        metadata["confidence"] = confidence
                    block = {
                        "id": f"paddleocr-page-{page_number}-line-{line_index}",
                        "type": "OCR_TEXT",
                        "text": text,
                        "pageNumber": page_number,
                        "order": len(blocks),
                        "sourceRef": source_ref,
                        "bbox": bbox,
                        "metadata": metadata,
                    }
                    blocks.append(block)
                    page_blocks.append(block)
                    page_lines.append(text)
                page_text = "\n".join(page_lines)
                pages.append({
                    "pageNumber": page_number,
                    "text": page_text,
                    "blocks": page_blocks,
                    "metadata": {"sourceRef": f"page[{page_number}]", "engine": "paddleocr"},
                })
                markdown_pages.append(page_text)

        markdown = "\n\n".join(value for value in markdown_pages if value)
        return {
            "filename": file.filename,
            "contentType": file.content_type or "application/pdf",
            "markdown": markdown,
            "plainText": markdown,
            "pages": pages,
            "blocks": blocks,
            "tables": [],
            "images": [],
            "metadata": {
                "pageCount": page_count,
                "pageFrom": page_from,
                "pageTo": page_to,
                "rangePageCount": page_to - page_from + 1,
                "textLength": len(markdown),
                "extractionEngine": "paddleocr",
                "koreanTextOcrProvider": "paddleocr",
                "language": LANGUAGE,
                "dpi": dpi,
            },
            "warnings": [],
            "elapsedMs": round((time.monotonic() - started) * 1000),
            "ocrApplied": True,
        }
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"PaddleOCR extraction failed: {type(exc).__name__}") from exc
    finally:
        temp_path.unlink(missing_ok=True)
