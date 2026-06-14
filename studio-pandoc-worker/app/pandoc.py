import subprocess
from pathlib import Path
from typing import Any


SUPPORTED_CONVERSIONS = {
    ("markdown", "html"),
    ("markdown", "docx"),
    ("markdown", "pdf"),
    ("html", "docx"),
    ("html", "pdf"),
    ("docx", "markdown"),
    ("docx", "html"),
}
ALLOWED_OPTIONS = {"pdfEngine", "mainFont", "toc", "numberSections", "standalone", "metadata"}


class PandocFailure(RuntimeError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


def build_command(
    binary: str,
    source: Path,
    output: Path,
    source_format: str,
    target_format: str,
    options: dict[str, Any],
) -> list[str]:
    pair = (source_format.lower(), target_format.lower())
    if pair not in SUPPORTED_CONVERSIONS:
        raise PandocFailure("UNSUPPORTED_TARGET_FORMAT", "Unsupported conversion")
    if not ALLOWED_OPTIONS.issuperset(options):
        raise PandocFailure("INVALID_CONVERT_OPTION", "Unsupported conversion option")

    command = [binary, str(source), "--from", pair[0], "--to", pair[1], "-o", str(output)]
    pdf_engine = options.get("pdfEngine")
    if pdf_engine:
        if pdf_engine not in {"xelatex"}:
            raise PandocFailure("INVALID_CONVERT_OPTION", "Unsupported PDF engine")
        command.append(f"--pdf-engine={pdf_engine}")
    main_font = options.get("mainFont")
    if main_font:
        command.extend(["-V", f"mainfont={str(main_font)[:100]}"])
    for option, flag in (
        ("toc", "--toc"),
        ("numberSections", "--number-sections"),
        ("standalone", "--standalone"),
    ):
        if options.get(option) is True:
            command.append(flag)
    metadata = options.get("metadata", {})
    if not isinstance(metadata, dict) or not {"title", "author"}.issuperset(metadata):
        raise PandocFailure("INVALID_CONVERT_OPTION", "Unsupported metadata option")
    for key in ("title", "author"):
        if metadata.get(key):
            command.extend(["--metadata", f"{key}={str(metadata[key])[:200]}"])
    return command


def run_pandoc(command: list[str], timeout_seconds: int) -> None:
    try:
        completed = subprocess.run(
            command,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            timeout=timeout_seconds,
            check=False,
            text=True,
        )
    except subprocess.TimeoutExpired as exc:
        raise PandocFailure("PANDOC_TIMEOUT", "Pandoc execution timed out") from exc
    if completed.returncode != 0:
        raise PandocFailure("PANDOC_EXIT_NON_ZERO", "Pandoc conversion failed")
