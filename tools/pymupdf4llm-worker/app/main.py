import json
import logging
import os
import tempfile
import time
from collections.abc import Sequence
from typing import Any

import fitz
import pymupdf4llm
from fastapi import FastAPI, File, Form, HTTPException, UploadFile

logger = logging.getLogger("pymupdf4llm-worker")

MAX_UPLOAD_BYTES = int(os.getenv("PYMUPDF4LLM_MAX_UPLOAD_BYTES", "52428800"))
OCR_ENABLED = os.getenv("PYMUPDF4LLM_OCR_ENABLED", "false").lower() == "true"
OCR_LANGUAGE = os.getenv("PYMUPDF4LLM_OCR_LANGUAGE", "kor+eng")
OCR_DPI = int(os.getenv("PYMUPDF4LLM_OCR_DPI", "220"))

app = FastAPI(title="Studio PyMuPDF4LLM Worker", version="0.1.0")


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "engine": "pymupdf4llm",
        "ocrEnabled": OCR_ENABLED,
        "ocrLanguage": OCR_LANGUAGE,
        "ocrDpi": OCR_DPI,
        "maxUploadBytes": MAX_UPLOAD_BYTES,
    }


@app.post("/extract/pdf")
async def extract_pdf(
    file: UploadFile = File(...),
    options: str = Form("{}"),
) -> dict[str, Any]:
    started = time.monotonic()
    parsed_options = parse_options(options)
    ocr_mode = str(parsed_options.get("ocrMode") or "AUTO").strip().upper().replace("-", "_")
    ocr_disabled = ocr_mode == "DISABLED"
    ocr_requested = not ocr_disabled and (bool(parsed_options.get("ocrRequired")) or ocr_mode == "FORCE")
    ocr_allowed = ocr_requested and OCR_ENABLED
    ocr_language = str(parsed_options.get("ocrLanguage") or OCR_LANGUAGE).strip() or OCR_LANGUAGE
    content = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="PDF exceeds worker upload limit")
    if not content:
        raise HTTPException(status_code=400, detail="PDF file is empty")

    warnings: list[dict[str, Any]] = []
    if ocr_requested and not OCR_ENABLED:
        warnings.append({
            "code": "OCR_DISABLED",
            "message": "OCR was requested but PYMUPDF4LLM_OCR_ENABLED is false.",
            "sourceRef": "document",
            "metadata": {"ocrLanguage": ocr_language},
        })

    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=True) as temp:
        temp.write(content)
        temp.flush()
        try:
            with fitz.open(temp.name) as document:
                page_numbers = page_range(parsed_options, document.page_count)
                pages, page_blocks, ocr_applied = extract_pages(document, page_numbers, ocr_allowed, ocr_language, warnings)
                markdown_page_chunks: list[tuple[int, str]] = []
                if ocr_allowed and ocr_applied:
                    markdown = blocks_to_markdown(page_blocks)
                else:
                    markdown, markdown_page_chunks = to_markdown(
                        temp.name, page_numbers, range_requested(parsed_options)
                    )
                    if markdown_page_chunks:
                        fallback_blocks = markdown_chunk_blocks(markdown_page_chunks)
                        page_blocks = merge_missing_page_blocks(page_blocks, fallback_blocks)
                        pages = merge_markdown_chunk_pages(pages, markdown_page_chunks, page_blocks, document)
                images = extract_images(document, page_numbers, bool(parsed_options.get("includeImages")))
                metadata = dict(document.metadata or {})
                metadata["pageCount"] = document.page_count
                metadata["pageFrom"] = page_numbers[0] + 1 if page_numbers else None
                metadata["pageTo"] = page_numbers[-1] + 1 if page_numbers else None
                metadata["rangePageCount"] = len(page_numbers)
                metadata["textLength"] = len(markdown.strip())
                metadata["ocrRequested"] = ocr_requested
                metadata["ocrMode"] = ocr_mode
                metadata["ocrApplied"] = ocr_applied
                metadata["ocrEngine"] = "pymupdf"
                metadata["ocrLanguage"] = ocr_language if ocr_allowed else None
                metadata["ocrDpi"] = OCR_DPI if ocr_allowed else None
                metadata["blockSource"] = "pymupdf-ocr-dict" if ocr_applied else "pymupdf-dict"
        except HTTPException:
            raise
        except Exception as exc:
            logger.exception("Failed to extract PDF with PyMuPDF4LLM")
            raise HTTPException(status_code=422, detail="PDF extraction failed") from exc

    if not markdown.strip():
        warnings.append({
            "code": "PYMUPDF4LLM_EMPTY_TEXT",
            "message": "PyMuPDF4LLM returned empty markdown text.",
            "sourceRef": "document",
            "metadata": {
                "pageFrom": metadata.get("pageFrom"),
                "pageTo": metadata.get("pageTo"),
            },
        })

    first_page = metadata.get("pageFrom") or 1
    blocks = page_blocks or markdown_blocks(markdown, first_page)
    tables = markdown_chunk_tables(markdown_page_chunks) if markdown_page_chunks else markdown_tables(markdown, first_page)
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
        "ocrApplied": bool(metadata.get("ocrApplied")),
    }


def parse_options(raw: str) -> dict[str, Any]:
    try:
        value = json.loads(raw or "{}")
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail="options must be valid JSON") from exc
    if not isinstance(value, dict):
        raise HTTPException(status_code=400, detail="options must be a JSON object")
    return value


def page_range(options: dict[str, Any], page_count: int) -> list[int]:
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


def range_requested(options: dict[str, Any]) -> bool:
    return any(key in options for key in ("pageFrom", "pageTo", "maxPages"))


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


def to_markdown(
    path: str,
    page_numbers: Sequence[int],
    requested_range: bool,
) -> tuple[str, list[tuple[int, str]]]:
    try:
        value = pymupdf4llm.to_markdown(path, page_chunks=True, pages=list(page_numbers))
    except TypeError as exc:
        if requested_range:
            raise HTTPException(
                status_code=422,
                detail={
                    "errorCode": "PYMUPDF4LLM_PAGE_RANGE_UNSUPPORTED",
                    "message": "Installed pymupdf4llm does not support page range extraction.",
                },
            ) from exc
        value = pymupdf4llm.to_markdown(path, page_chunks=True)
    if isinstance(value, list):
        chunks: list[tuple[int, str]] = []
        for index, chunk in enumerate(value):
            fallback_page = page_numbers[index] + 1 if index < len(page_numbers) else index + 1
            page_number = markdown_chunk_page(chunk, fallback_page)
            text = markdown_chunk_text(chunk)
            if text.strip():
                chunks.append((page_number, text))
        return "\n\n".join(text for _, text in chunks), chunks
    text = str(value or "")
    first_page = page_numbers[0] + 1 if page_numbers else 1
    return text, [(first_page, text)] if text.strip() else []


def markdown_chunk_page(chunk: Any, fallback: int) -> int:
    if not isinstance(chunk, dict):
        return fallback
    metadata = chunk.get("metadata") if isinstance(chunk.get("metadata"), dict) else {}
    for value in (
        chunk.get("pageNumber"),
        chunk.get("page_number"),
        chunk.get("page"),
        metadata.get("pageNumber"),
        metadata.get("page_number"),
        metadata.get("page"),
        metadata.get("page_index"),
    ):
        if isinstance(value, int):
            # PyMuPDF4LLM metadata uses a zero-based page index.
            return value + 1 if value == fallback - 1 else max(1, value)
    return fallback


def markdown_chunk_text(chunk: Any) -> str:
    if not isinstance(chunk, dict):
        return str(chunk or "")
    for key in ("text", "markdown", "content"):
        value = chunk.get(key)
        if isinstance(value, str):
            return value
    return ""


def markdown_chunk_blocks(chunks: Sequence[tuple[int, str]]) -> list[dict[str, Any]]:
    blocks: list[dict[str, Any]] = []
    for page_number, text in chunks:
        page_blocks = markdown_blocks(text, page_number)
        for block in page_blocks:
            block["order"] = len(blocks)
            blocks.append(block)
    return blocks


def merge_missing_page_blocks(
    native_blocks: Sequence[dict[str, Any]],
    fallback_blocks: Sequence[dict[str, Any]],
) -> list[dict[str, Any]]:
    native_pages = {
        int(block.get("pageNumber") or 1)
        for block in native_blocks
        if str(block.get("text") or "").strip()
    }
    merged = list(native_blocks)
    merged.extend(
        block for block in fallback_blocks
        if int(block.get("pageNumber") or 1) not in native_pages
    )
    for order, block in enumerate(merged):
        block["order"] = order
    return merged


def merge_markdown_chunk_pages(
    native_pages: Sequence[dict[str, Any]],
    chunks: Sequence[tuple[int, str]],
    blocks: Sequence[dict[str, Any]],
    document: fitz.Document,
) -> list[dict[str, Any]]:
    blocks_by_page: dict[int, list[dict[str, Any]]] = {}
    for block in blocks:
        blocks_by_page.setdefault(int(block.get("pageNumber") or 1), []).append(block)
    native_by_page = {
        int(page.get("pageNumber") or 1): page
        for page in native_pages
    }
    pages: list[dict[str, Any]] = []
    for page_number, text in chunks:
        page = document[page_number - 1]
        native = native_by_page.get(page_number, {})
        native_text = str(native.get("text") or "").strip()
        pages.append({
            "pageNumber": page_number,
            "text": native_text or text,
            "blocks": blocks_by_page.get(page_number, []),
            "metadata": {
                "sourceRef": f"page[{page_number}]",
                "width": page.rect.width,
                "height": page.rect.height,
                "ocrApplied": False,
                "markdownPageFallback": not bool(native_text),
            },
        })
    return pages


def markdown_chunk_tables(chunks: Sequence[tuple[int, str]]) -> list[dict[str, Any]]:
    tables: list[dict[str, Any]] = []
    for page_number, text in chunks:
        page_tables = markdown_tables(text, page_number)
        for table in page_tables:
            table["order"] = len(tables)
            tables.append(table)
    return tables


def extract_pages(
    document: fitz.Document,
    page_numbers: Sequence[int],
    use_ocr: bool,
    ocr_language: str,
    warnings: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], bool]:
    pages: list[dict[str, Any]] = []
    all_blocks: list[dict[str, Any]] = []
    any_ocr_applied = False
    for zero_based in page_numbers:
        page = document[zero_based]
        index = zero_based + 1
        textpage = None
        page_ocr_applied = False
        if use_ocr:
            textpage = get_ocr_textpage(page, index, ocr_language, warnings)
            page_ocr_applied = textpage is not None
            any_ocr_applied = any_ocr_applied or page_ocr_applied
        text_dict = page_text_dict(page, textpage)
        blocks = text_blocks(text_dict, index, page_ocr_applied, len(all_blocks), ocr_language)
        all_blocks.extend(blocks)
        pages.append({
            "pageNumber": index,
            "text": "\n\n".join(block["text"] for block in blocks if block.get("text")),
            "blocks": blocks,
            "metadata": {
                "sourceRef": f"page[{index}]",
                "width": page.rect.width,
                "height": page.rect.height,
                "ocrApplied": page_ocr_applied,
                "ocrLanguage": ocr_language if page_ocr_applied else None,
            },
        })
    return pages, all_blocks, any_ocr_applied


def get_ocr_textpage(
    page: fitz.Page,
    page_number: int,
    ocr_language: str,
    warnings: list[dict[str, Any]],
) -> Any | None:
    if not hasattr(page, "get_textpage_ocr"):
        warnings.append({
            "code": "PYMUPDF_OCR_UNSUPPORTED",
            "message": "Installed PyMuPDF does not support get_textpage_ocr.",
            "sourceRef": f"page[{page_number}]",
            "metadata": {"ocrLanguage": ocr_language},
        })
        return None
    attempts = [
        {"language": ocr_language, "dpi": OCR_DPI, "full": True},
        {"language": ocr_language, "dpi": OCR_DPI},
        {"language": ocr_language},
        {},
    ]
    for kwargs in attempts:
        try:
            return page.get_textpage_ocr(**kwargs)
        except TypeError:
            continue
        except Exception as exc:
            warnings.append({
                "code": "PYMUPDF_OCR_FAILED",
                "message": f"PyMuPDF OCR failed for page {page_number}.",
                "sourceRef": f"page[{page_number}]",
                "metadata": {
                    "ocrLanguage": ocr_language,
                    "ocrDpi": OCR_DPI,
                    "error": str(exc)[:300],
                },
            })
            return None
    warnings.append({
        "code": "PYMUPDF_OCR_SIGNATURE_UNSUPPORTED",
        "message": "Installed PyMuPDF OCR signature is not supported by the worker.",
        "sourceRef": f"page[{page_number}]",
        "metadata": {"ocrLanguage": ocr_language},
    })
    return None


def page_text_dict(page: fitz.Page, textpage: Any | None) -> dict[str, Any]:
    if textpage is None:
        return page.get_text("dict")
    try:
        return page.get_text("dict", textpage=textpage)
    except TypeError:
        return page.get_text("dict")


def text_blocks(
    text_dict: dict[str, Any],
    page_number: int,
    ocr_applied: bool,
    global_offset: int,
    ocr_language: str,
) -> list[dict[str, Any]]:
    blocks: list[dict[str, Any]] = []
    for index, raw_block in enumerate(text_dict.get("blocks") or []):
        if raw_block.get("type", 0) != 0:
            continue
        text = block_text(raw_block)
        if not text:
            continue
        source_ref = f"page[{page_number}]/block[{index}]"
        bbox = bbox_list(raw_block.get("bbox"))
        block: dict[str, Any] = {
            "type": "ocr_text" if ocr_applied else block_type_for(text),
            "text": text,
            "pageNumber": page_number,
            "order": global_offset + len(blocks),
            "level": heading_level(text) if block_type_for(text) == "heading" else None,
            "sourceRef": source_ref,
            "bbox": bbox,
            "metadata": {
                "sourceRef": source_ref,
                "bbox": bbox,
                "ocrApplied": ocr_applied,
                "ocrLanguage": ocr_language if ocr_applied else None,
            },
        }
        blocks.append(block)
    return blocks


def block_text(raw_block: dict[str, Any]) -> str:
    lines: list[str] = []
    for line in raw_block.get("lines") or []:
        spans = line.get("spans") or []
        line_text = "".join(str(span.get("text") or "") for span in spans).strip()
        if line_text:
            lines.append(line_text)
    return "\n".join(lines).strip()


def bbox_list(value: Any) -> list[float]:
    if not isinstance(value, (list, tuple)) or len(value) != 4:
        return []
    return [round(float(item), 2) for item in value]


def blocks_to_markdown(blocks: Sequence[dict[str, Any]]) -> str:
    pages: dict[int, list[str]] = {}
    for block in blocks:
        text = str(block.get("text") or "").strip()
        if not text:
            continue
        page = int(block.get("pageNumber") or 1)
        pages.setdefault(page, []).append(text)
    return "\n\n".join(
        "\n\n".join(page_blocks)
        for _, page_blocks in sorted(pages.items())
        if page_blocks
    )


def extract_images(document: fitz.Document, page_numbers: Sequence[int], include_images: bool) -> list[dict[str, Any]]:
    if not include_images:
        return []
    images: list[dict[str, Any]] = []
    for zero_based in page_numbers:
        page = document[zero_based]
        page_index = zero_based + 1
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


def markdown_blocks(markdown: str, page_number: int) -> list[dict[str, Any]]:
    blocks: list[dict[str, Any]] = []
    order = 0
    for paragraph in split_paragraphs(markdown):
        block_type = block_type_for(paragraph)
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


def markdown_tables(markdown: str, page_number: int) -> list[dict[str, Any]]:
    tables: list[dict[str, Any]] = []
    current: list[str] = []
    for line in markdown.splitlines() + [""]:
        if is_table_line(line):
            current.append(line)
            continue
        if current:
            table_index = len(tables)
            source_ref = f"page[{page_number}]/table[{table_index}]"
            headers, rows = parse_markdown_table(current)
            tables.append({
                "pageNumber": page_number,
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
