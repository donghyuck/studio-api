import asyncio
import logging
import re
import shutil
import tempfile
from pathlib import Path

import httpx

from app.config import Settings
from app.models import CallbackPayload, JobStatus, JobView, PandocJobRequest
from app.pandoc import PandocFailure, build_command, run_pandoc
from app.security import resolve_shared_path

log = logging.getLogger(__name__)


class PandocJobService:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        settings.work_dir.mkdir(parents=True, exist_ok=True)
        self.jobs: dict[str, JobView] = {}
        self.semaphore = asyncio.Semaphore(settings.max_concurrent_jobs)

    def submit(self, request: PandocJobRequest) -> JobView:
        existing = self.jobs.get(request.jobId)
        if existing and existing.status in {JobStatus.ACCEPTED, JobStatus.RUNNING}:
            return existing
        view = JobView(jobId=request.jobId, status=JobStatus.ACCEPTED)
        self.jobs[request.jobId] = view
        asyncio.create_task(self._execute(request))
        return view

    async def _execute(self, request: PandocJobRequest) -> None:
        async with self.semaphore:
            self.jobs[request.jobId] = JobView(jobId=request.jobId, status=JobStatus.RUNNING)
            try:
                await self._convert(request)
                view = JobView(jobId=request.jobId, status=JobStatus.COMPLETED)
                self.jobs[request.jobId] = view
                await self._callback(
                    request,
                    CallbackPayload(status="COMPLETED", resultFileId=request.resultFileId),
                )
            except PandocFailure as exc:
                await self._fail(request, exc.code, str(exc))
            except Exception:
                log.exception("Pandoc job failed: jobId=%s", request.jobId)
                await self._fail(request, "INTERNAL_ERROR", "Document conversion failed")

    async def _convert(self, request: PandocJobRequest) -> None:
        work_dir = Path(tempfile.mkdtemp(prefix=f"{request.jobId}-", dir=self.settings.work_dir))
        source = work_dir / f"source.{extension(request.sourceFormat)}"
        output = work_dir / f"result.{extension(request.targetFormat)}"
        try:
            if request.sourceUrl:
                await download(request.sourceUrl, source, self.settings.max_input_bytes)
            else:
                if not self.settings.local_path_enabled:
                    raise PandocFailure("INVALID_CONVERT_OPTION", "Local path mode is disabled")
                source_path = resolve_shared_path(
                    request.sourcePath or "", self.settings.shared_volume_root
                )
                if not source_path.is_file():
                    raise PandocFailure("SOURCE_FILE_NOT_FOUND", "Source file not found")
                if source_path.stat().st_size > self.settings.max_input_bytes:
                    raise PandocFailure("SOURCE_FILE_TOO_LARGE", "Source file is too large")
                shutil.copyfile(source_path, source)

            reject_remote_resources(
                source, request.sourceFormat, self.settings.allow_remote_resources
            )
            command = build_command(
                self.settings.pandoc_binary,
                source,
                output,
                request.sourceFormat,
                request.targetFormat,
                request.options,
            )
            await asyncio.to_thread(run_pandoc, command, self.settings.timeout_seconds)
            if not output.is_file():
                raise PandocFailure("PANDOC_EXIT_NON_ZERO", "Pandoc produced no output")
            if output.stat().st_size > self.settings.max_output_bytes:
                raise PandocFailure("RESULT_FILE_TOO_LARGE", "Result file is too large")

            if request.uploadUrl:
                result_file_id = await upload(
                    request.uploadUrl,
                    request.uploadToken or "",
                    output,
                    request.targetContentType,
                )
                if result_file_id:
                    request.resultFileId = result_file_id
            else:
                output_path = resolve_shared_path(
                    request.outputPath or "", self.settings.shared_volume_root
                )
                output_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(output, output_path)
        finally:
            if self.settings.cleanup_enabled:
                shutil.rmtree(work_dir, ignore_errors=True)

    async def _fail(self, request: PandocJobRequest, code: str, message: str) -> None:
        view = JobView(
            jobId=request.jobId,
            status=JobStatus.FAILED,
            errorCode=code,
            errorMessage=message[:500],
        )
        self.jobs[request.jobId] = view
        await self._callback(
            request,
            CallbackPayload(status="FAILED", errorCode=code, errorMessage=message[:500]),
        )

    async def _callback(self, request: PandocJobRequest, payload: CallbackPayload) -> None:
        headers = {"X-Internal-Token": self.settings.callback_token}
        try:
            async with httpx.AsyncClient(timeout=30) as client:
                response = await client.post(
                    request.callbackUrl, json=payload.model_dump(), headers=headers
                )
                response.raise_for_status()
        except Exception:
            log.warning("Pandoc callback failed: jobId=%s", request.jobId)


async def download(url: str, destination: Path, limit: int) -> None:
    total = 0
    async with httpx.AsyncClient(follow_redirects=False, timeout=60) as client:
        async with client.stream("GET", url) as response:
            response.raise_for_status()
            with destination.open("wb") as output:
                async for chunk in response.aiter_bytes():
                    total += len(chunk)
                    if total > limit:
                        raise PandocFailure("SOURCE_FILE_TOO_LARGE", "Source file is too large")
                    output.write(chunk)


async def upload(url: str, token: str, source: Path, content_type: str) -> str | None:
    async def chunks():
        with source.open("rb") as body:
            while data := body.read(1024 * 1024):
                yield data

    async with httpx.AsyncClient(follow_redirects=False, timeout=120) as client:
        response = await client.put(
            url,
            content=chunks(),
            headers={"Content-Type": content_type, "X-Upload-Token": token},
        )
        if response.status_code < 200 or response.status_code >= 300:
            raise PandocFailure("RESULT_UPLOAD_FAILED", "Result upload failed")
        return response.headers.get("X-Result-File-Id")


def extension(value: str) -> str:
    return {"markdown": "md", "html": "html", "docx": "docx", "pdf": "pdf", "text": "txt"}.get(
        value.lower(), "bin"
    )


def reject_remote_resources(source: Path, source_format: str, allowed: bool) -> None:
    if allowed or source_format.lower() not in {"markdown", "html"}:
        return
    text = source.read_text(encoding="utf-8", errors="ignore")
    if re.search(r"(?i)(?:src|href)\s*=\s*[\"']https?://|!?\[[^\]]*\]\(https?://", text):
        raise PandocFailure(
            "INVALID_CONVERT_OPTION", "Remote document resources are disabled"
        )
