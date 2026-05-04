import json
import logging
import os
import tempfile
import time
from typing import Any

import fitz
import pymupdf4llm
from fastapi import FastAPI, File, Form, HTTPException, UploadFile

logger = logging.getLogger("pymupdf4llm-worker")

MAX_UPLOAD_BYTES = int(os.getenv("PYMUPDF4LLM_MAX_UPLOAD_BYTES", "52428800"))
OCR_ENABLED = os.getenv("PYMUPDF4LLM_OCR_ENABLED", "false").lower() == "true"
OCR_LANGUAGE = os.getenv("PYMUPDF4LLM_OCR_LANGUAGE", "kor+eng")

app = FastAPI(title="Studio PyMuPDF4LLM Worker", version="0.1.0")


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "engine": "pymupdf4llm",
        "ocrEnabled": OCR_ENABLED,
        "ocrLanguage": OCR_LANGUAGE,
        "maxUploadBytes": MAX_UPLOAD_BYTES,
    }


@app.post("/extract/pdf")
async def extract_pdf(
    file: UploadFile = File(...),
    options: str = Form("{}"),
) -> dict[str, Any]:
    started = time.monotonic()
    parsed_options = parse_options(options)
    content = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="PDF exceeds worker upload limit")
    if not content:
        raise HTTPException(status_code=400, detail="PDF file is empty")

    warnings: list[dict[str, Any]] = []
    if parsed_options.get("ocrRequired") and not OCR_ENABLED:
        warnings.append({
            "code": "OCR_DISABLED",
            "message": "OCR was requested but PYMUPDF4LLM_OCR_ENABLED is false.",
            "sourceRef": "document",
            "metadata": {"ocrLanguage": OCR_LANGUAGE},
        })

    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=True) as temp:
        temp.write(content)
        temp.flush()
        try:
            markdown = to_markdown(temp.name)
            with fitz.open(temp.name) as document:
                pages = extract_pages(document)
                images = extract_images(document)
                metadata = dict(document.metadata or {})
                metadata["pageCount"] = document.page_count
        except Exception as exc:
            logger.exception("Failed to extract PDF with PyMuPDF4LLM")
            raise HTTPException(status_code=422, detail="PDF extraction failed") from exc

    blocks = markdown_blocks(markdown)
    tables = markdown_tables(markdown)
    elapsed_ms = int((time.monotonic() - started) * 1000)
    return {
        "filename": file.filename,
        "contentType": file.content_type,
        "markdown": markdown,
        "pages": pages,
        "blocks": blocks,
        "tables": tables,
        "images": images,
        "metadata": metadata,
        "warnings": warnings,
        "elapsedMs": elapsed_ms,
        "ocrApplied": bool(parsed_options.get("ocrRequired") and OCR_ENABLED),
    }


def parse_options(raw: str) -> dict[str, Any]:
    try:
        value = json.loads(raw or "{}")
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail="options must be valid JSON") from exc
    if not isinstance(value, dict):
        raise HTTPException(status_code=400, detail="options must be a JSON object")
    return value


def to_markdown(path: str) -> str:
    try:
        value = pymupdf4llm.to_markdown(path, page_chunks=False)
    except TypeError:
        value = pymupdf4llm.to_markdown(path)
    if isinstance(value, list):
        return "\n\n".join(str(chunk) for chunk in value)
    return str(value or "")


def extract_pages(document: fitz.Document) -> list[dict[str, Any]]:
    pages: list[dict[str, Any]] = []
    for index, page in enumerate(document, start=1):
        pages.append({
            "pageNumber": index,
            "text": page.get_text("text"),
            "blocks": [],
            "metadata": {
                "sourceRef": f"page[{index}]",
                "width": page.rect.width,
                "height": page.rect.height,
            },
        })
    return pages


def extract_images(document: fitz.Document) -> list[dict[str, Any]]:
    images: list[dict[str, Any]] = []
    for page_index, page in enumerate(document, start=1):
        for image_index, image in enumerate(page.get_images(full=True)):
            source_ref = f"page[{page_index}]/image[{image_index}]"
            images.append({
                "pageNumber": page_index,
                "name": f"image-{page_index}-{image_index}",
                "mimeType": None,
                "width": image[2],
                "height": image[3],
                "sourceRef": source_ref,
                "caption": "",
                "altText": "",
                "ocrText": "",
                "ocrApplied": False,
                "bbox": [],
                "metadata": {"sourceRef": source_ref},
            })
    return images


def markdown_blocks(markdown: str) -> list[dict[str, Any]]:
    blocks: list[dict[str, Any]] = []
    order = 0
    for paragraph in split_paragraphs(markdown):
        block_type = block_type_for(paragraph)
        page_number = 1
        source_ref = f"page[{page_number}]/block[{order}]"
        block: dict[str, Any] = {
            "type": block_type,
            "text": paragraph,
            "pageNumber": page_number,
            "order": order,
            "level": heading_level(paragraph) if block_type == "heading" else None,
            "sourceRef": source_ref,
            "bbox": [],
            "metadata": {"sourceRef": source_ref},
        }
        blocks.append(block)
        order += 1
    return blocks


def markdown_tables(markdown: str) -> list[dict[str, Any]]:
    tables: list[dict[str, Any]] = []
    current: list[str] = []
    for line in markdown.splitlines() + [""]:
        if is_table_line(line):
            current.append(line)
            continue
        if current:
            table_index = len(tables)
            source_ref = f"page[1]/table[{table_index}]"
            headers, rows = parse_markdown_table(current)
            tables.append({
                "pageNumber": 1,
                "caption": "",
                "headers": headers,
                "rows": rows,
                "markdown": "\n".join(current),
                "sourceRef": source_ref,
                "bbox": [],
                "metadata": {"sourceRef": source_ref},
            })
            current = []
    return tables


def split_paragraphs(markdown: str) -> list[str]:
    return [part.strip() for part in markdown.split("\n\n") if part.strip()]


def block_type_for(text: str) -> str:
    stripped = text.lstrip()
    if stripped.startswith("#"):
        return "heading"
    if is_table_line(stripped.splitlines()[0]):
        return "table"
    if stripped.startswith(("- ", "* ", "1. ")):
        return "list_item"
    return "paragraph"


def heading_level(text: str) -> int | None:
    stripped = text.lstrip()
    count = len(stripped) - len(stripped.lstrip("#"))
    return count or None


def is_table_line(line: str) -> bool:
    stripped = line.strip()
    return stripped.startswith("|") and stripped.endswith("|") and stripped.count("|") >= 2


def parse_markdown_table(lines: list[str]) -> tuple[list[str], list[list[str]]]:
    rows = [table_cells(line) for line in lines if is_table_line(line)]
    if not rows:
        return [], []
    headers = rows[0]
    data_rows = [
        row for row in rows[1:]
        if not all(cell.replace("-", "").replace(":", "").strip() == "" for cell in row)
    ]
    return headers, data_rows


def table_cells(line: str) -> list[str]:
    return [cell.strip() for cell in line.strip().strip("|").split("|")]
