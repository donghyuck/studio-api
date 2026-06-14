from pathlib import Path

import pytest
from pydantic import ValidationError

from app.models import PandocJobRequest
from app.pandoc import PandocFailure, build_command
from app.security import resolve_shared_path
from app.service import reject_remote_resources


def request(**overrides: object) -> PandocJobRequest:
    values = {
        "jobId": "job-1",
        "sourceFormat": "markdown",
        "targetFormat": "pdf",
        "targetContentType": "application/pdf",
        "sourceUrl": "https://storage.example/source",
        "uploadUrl": "https://storage.example/result",
        "uploadToken": "upload-token",
        "callbackUrl": "https://api.example/callback",
        "resultFileId": "document-conversions/job-1/result.pdf",
    }
    values.update(overrides)
    return PandocJobRequest(**values)


def test_rejects_mixed_transfer_modes() -> None:
    with pytest.raises(ValidationError):
        request(sourcePath="source.md", outputPath="result.pdf")


def test_rejects_non_http_transfer_url() -> None:
    with pytest.raises(ValidationError):
        request(sourceUrl="file:///etc/passwd")


def test_requires_upload_token_for_url_mode() -> None:
    with pytest.raises(ValidationError):
        request(uploadToken=None)


def test_builds_argument_list_from_whitelisted_options(tmp_path: Path) -> None:
    command = build_command(
        "pandoc",
        tmp_path / "source.md",
        tmp_path / "result.pdf",
        "markdown",
        "pdf",
        {"pdfEngine": "xelatex", "toc": True, "metadata": {"title": "제목"}},
    )
    assert command[0] == "pandoc"
    assert "--pdf-engine=xelatex" in command
    assert "--toc" in command


def test_rejects_unknown_option(tmp_path: Path) -> None:
    with pytest.raises(PandocFailure):
        build_command(
            "pandoc",
            tmp_path / "source.md",
            tmp_path / "result.pdf",
            "markdown",
            "pdf",
            {"rawArgs": ["--filter", "evil"]},
        )


def test_local_path_must_stay_under_shared_root(tmp_path: Path) -> None:
    root = tmp_path / "shared"
    root.mkdir()
    assert resolve_shared_path("input.md", root) == root / "input.md"
    with pytest.raises(ValueError):
        resolve_shared_path("../outside.md", root)


def test_rejects_remote_document_resources_by_default(tmp_path: Path) -> None:
    source = tmp_path / "source.md"
    source.write_text("![image](https://example.com/image.png)", encoding="utf-8")
    with pytest.raises(PandocFailure):
        reject_remote_resources(source, "markdown", False)
