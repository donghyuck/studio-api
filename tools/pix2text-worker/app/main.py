import json
import logging
import os
import tempfile
import time
from pathlib import Path
from typing import Any

import fitz
from fastapi import FastAPI, File, Form, HTTPException, UploadFile

logger = logging.getLogger("pix2text-worker")

MAX_UPLOAD_BYTES = int(os.getenv("PIX2TEXT_MAX_UPLOAD_BYTES", "52428800"))
DEFAULT_LANGUAGE = os.getenv("PIX2TEXT_LANGUAGE", "ko,en")
DEVICE = os.getenv("PIX2TEXT_DEVICE", "cpu")
ORT_PROVIDERS = tuple(
    provider.strip()
    for provider in os.getenv("PIX2TEXT_ORT_PROVIDERS", "CPUExecutionProvider").split(",")
    if provider.strip()
)

app = FastAPI(title="Studio Pix2Text Math OCR Worker", version="0.1.0")
_pix2text: Any | None = None
_pix2text_error: str | None = None


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "engine": "pix2text",
        "language": DEFAULT_LANGUAGE,
        "device": DEVICE,
        "onnxRuntimeProviders": ORT_PROVIDERS,
        "maxUploadBytes": MAX_UPLOAD_BYTES,
        "modelLoaded": _pix2text is not None,
        "modelError": _pix2text_error,
    }


@app.post("/extract/pdf")
async def extract_pdf(
    file: UploadFile = File(...),
    options: str = Form("{}"),
) -> dict[str, Any]:
    started = time.monotonic()
    parsed_options = parse_options(options)
    language = str(parsed_options.get("language") or DEFAULT_LANGUAGE).strip() or DEFAULT_LANGUAGE
    content = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="PDF exceeds worker upload limit")
    if not content:
        raise HTTPException(status_code=400, detail="PDF file is empty")

    with tempfile.TemporaryDirectory(prefix="studio-pix2text-") as work_dir:
        work_path = Path(work_dir)
        pdf_path = work_path / "input.pdf"
        output_dir = work_path / "output-md"
        pdf_path.write_bytes(content)
        page_count = read_page_count(pdf_path)
        page_numbers = page_range(parsed_options, page_count)
        page_by_page = bool(parsed_options.get("pageByPage", True))
        try:
            if page_by_page and len(page_numbers) > 1:
                markdown, pages, blocks = recognize_pdf_by_page(pdf_path, output_dir, page_numbers, language)
            else:
                markdown = recognize_pdf(pdf_path, output_dir, page_numbers, language)
                first_page = page_numbers[0] + 1 if page_numbers else 1
                pages = page_items(markdown, first_page)
                blocks = markdown_blocks(markdown, first_page)
        except HTTPException:
            raise
        except Exception as exc:
            logger.exception("Failed to extract PDF with Pix2Text")
            raise HTTPException(status_code=422, detail=f"Pix2Text extraction failed: {str(exc)[:300]}") from exc

    warnings: list[dict[str, Any]] = []
    if not markdown.strip():
        warnings.append({
            "code": "PIX2TEXT_EMPTY_MARKDOWN",
            "message": "Pix2Text returned empty markdown text.",
            "sourceRef": "document",
            "metadata": {"pageCount": page_count},
        })

    elapsed_ms = int((time.monotonic() - started) * 1000)
    first_page = page_numbers[0] + 1 if page_numbers else 1
    return {
        "filename": file.filename,
        "contentType": file.content_type,
        "markdown": markdown,
        "plainText": markdown,
        "pages": pages,
        "blocks": blocks,
        "tables": [],
        "images": [],
        "metadata": {
            "pageCount": page_count,
            "pageFrom": first_page,
            "pageTo": page_numbers[-1] + 1 if page_numbers else page_count,
            "rangePageCount": len(page_numbers),
            "textLength": len(markdown.strip()),
            "pageByPage": page_by_page,
            "extractionEngine": "pix2text",
            "mathOcrProvider": "pix2text",
            "mathOcrApplied": True,
            "mathMarkdownApplied": True,
            "mathMarkdownEngine": "pix2text",
            "mathMarkdownQuality": "VALID",
            "language": language,
            "device": DEVICE,
            "onnxRuntimeProviders": list(ORT_PROVIDERS),
        },
        "warnings": warnings,
        "elapsedMs": elapsed_ms,
        "ocrApplied": True,
    }


def pix2text() -> Any:
    global _pix2text, _pix2text_error
    if _pix2text is not None:
        return _pix2text
    try:
        configure_onnxruntime_providers()
        from pix2text import Pix2Text

        _pix2text = Pix2Text.from_config()
        _pix2text_error = None
        return _pix2text
    except Exception as exc:
        _pix2text_error = str(exc)[:500]
        logger.exception("Failed to initialize Pix2Text")
        raise HTTPException(status_code=503, detail=f"Pix2Text model initialization failed: {_pix2text_error}") from exc


def configure_onnxruntime_providers() -> tuple[str, ...]:
    """Keep dynamic-layout ONNX models off unstable CoreML execution paths."""
    import onnxruntime as ort

    available = set(ort.get_available_providers())
    unavailable = [provider for provider in ORT_PROVIDERS if provider not in available]
    if unavailable:
        raise RuntimeError(
            f"Configured ONNX Runtime providers are unavailable: {', '.join(unavailable)}; "
            f"available: {', '.join(sorted(available))}"
        )
    if not ORT_PROVIDERS:
        raise RuntimeError("At least one ONNX Runtime provider must be configured")

    # cnstd constructs sessions from get_available_providers(). Override its global
    # discovery before Pix2Text imports the layout models so the configured order wins.
    ort.get_available_providers = lambda: list(ORT_PROVIDERS)
    return ORT_PROVIDERS


def recognize_pdf(pdf_path: Path, output_dir: Path, page_numbers: list[int], language: str) -> str:
    model = pix2text()
    kwargs: dict[str, Any] = {}
    if page_numbers:
        kwargs["page_numbers"] = page_numbers
    try:
        document = model.recognize_pdf(str(pdf_path), **kwargs)
    except TypeError:
        document = model.recognize_pdf(str(pdf_path))
    output_dir.mkdir(parents=True, exist_ok=True)
    if hasattr(document, "to_markdown"):
        document.to_markdown(str(output_dir))
    markdown_path = output_dir / "output.md"
    if markdown_path.exists():
        return markdown_path.read_text(encoding="utf-8")
    if isinstance(document, str):
        return document
    if hasattr(document, "markdown"):
        return str(document.markdown)
    return str(document or "")


def recognize_pdf_by_page(
    pdf_path: Path,
    output_dir: Path,
    page_numbers: list[int],
    language: str,
) -> tuple[str, list[dict[str, Any]], list[dict[str, Any]]]:
    markdown_parts: list[str] = []
    pages: list[dict[str, Any]] = []
    blocks: list[dict[str, Any]] = []
    order = 0
    for page_index in page_numbers:
        page_number = page_index + 1
        page_dir = output_dir / f"page-{page_number}"
        page_markdown = recognize_pdf(pdf_path, page_dir, [page_index], language).strip()
        if not page_markdown:
            continue
        page_markdown = f"<!-- page: {page_number} -->\n\n{page_markdown}"
        markdown_parts.append(page_markdown)
        page_blocks = markdown_blocks(page_markdown, page_number, order)
        blocks.extend(page_blocks)
        pages.append({
            "pageNumber": page_number,
            "text": page_markdown,
            "blocks": [block["id"] for block in page_blocks],
            "metadata": {"sourceRef": f"page[{page_number}]", "engine": "pix2text"},
        })
        order += len(page_blocks)
    markdown = "\n\n".join(markdown_parts).strip()
    return markdown, pages, blocks


def parse_options(raw: str) -> dict[str, Any]:
    try:
        value = json.loads(raw or "{}")
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail="options must be valid JSON") from exc
    if not isinstance(value, dict):
        raise HTTPException(status_code=400, detail="options must be a JSON object")
    return value


def read_page_count(pdf_path: Path) -> int:
    try:
        with fitz.open(pdf_path) as document:
            return document.page_count
    except Exception:
        return 0


def page_range(options: dict[str, Any], page_count: int) -> list[int]:
    if page_count <= 0:
        return []
    page_from = positive_int(options.get("pageFrom"), 1)
    page_to = positive_int(options.get("pageTo"), page_count)
    max_pages = positive_int(options.get("maxPages"), None)
    page_from = max(1, min(page_from, page_count))
    page_to = max(1, min(page_to, page_count))
    if page_to < page_from:
        raise HTTPException(status_code=400, detail="pageTo must be greater than or equal to pageFrom")
    if max_pages is not None:
        page_to = min(page_to, page_from + max_pages - 1)
    return list(range(page_from - 1, page_to))


def positive_int(value: Any, default: int | None) -> int | None:
    if value is None or value == "":
        return default
    try:
        parsed = int(value)
    except (TypeError, ValueError) as exc:
        raise HTTPException(status_code=400, detail="page range options must be positive integers") from exc
    if parsed <= 0:
        raise HTTPException(status_code=400, detail="page range options must be positive integers")
    return parsed


def page_items(markdown: str, first_page: int) -> list[dict[str, Any]]:
    return [{
        "pageNumber": first_page,
        "text": markdown,
        "blocks": [],
        "metadata": {"sourceRef": f"page[{first_page}]", "engine": "pix2text"},
    }] if markdown.strip() else []


def markdown_blocks(markdown: str, first_page: int, start_order: int = 0) -> list[dict[str, Any]]:
    blocks: list[dict[str, Any]] = []
    order = start_order
    for raw in markdown.splitlines():
        text = raw.strip()
        if not text or text.startswith("<!-- page:"):
            continue
        block_type = "HEADING" if text.startswith("#") else "PARAGRAPH"
        clean_text = text.lstrip("#").strip() if block_type == "HEADING" else text
        source_ref = f"page[{first_page}]/pix2text-block[{order}]"
        blocks.append({
            "id": f"pix2text-block-{order}",
            "type": block_type,
            "text": clean_text,
            "page": first_page,
            "metadata": {
                "sourceRef": source_ref,
                "order": order,
                "engine": "pix2text",
            },
        })
        order += 1
    if not blocks and markdown.strip():
        blocks.append({
            "id": f"pix2text-block-{start_order}",
            "type": "DOCUMENT",
            "text": markdown,
            "page": first_page,
            "metadata": {"sourceRef": f"page[{first_page}]/pix2text-block[{start_order}]", "order": start_order},
        })
    return blocks
