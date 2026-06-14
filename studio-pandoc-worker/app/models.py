from enum import StrEnum
from typing import Any
from urllib.parse import urlsplit

from pydantic import BaseModel, ConfigDict, Field, model_validator


class JobStatus(StrEnum):
    ACCEPTED = "ACCEPTED"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class PandocJobRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    jobId: str = Field(min_length=1, max_length=100)
    sourceFormat: str
    targetFormat: str
    targetContentType: str = Field(min_length=1, max_length=200)
    sourceUrl: str | None = None
    uploadUrl: str | None = None
    uploadToken: str | None = Field(default=None, min_length=1, max_length=500)
    callbackUrl: str = Field(min_length=1)
    resultFileId: str = Field(min_length=1, max_length=500)
    sourcePath: str | None = None
    outputPath: str | None = None
    options: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_transport(self) -> "PandocJobRequest":
        url_mode = bool(self.sourceUrl or self.uploadUrl)
        path_mode = bool(self.sourcePath or self.outputPath)
        if url_mode and path_mode:
            raise ValueError("URL and local path modes cannot be mixed")
        if url_mode and not (self.sourceUrl and self.uploadUrl):
            raise ValueError("sourceUrl and uploadUrl are both required")
        if url_mode and not self.uploadToken:
            raise ValueError("uploadToken is required for URL mode")
        if path_mode and self.uploadToken:
            raise ValueError("uploadToken is not allowed for local path mode")
        if path_mode and not (self.sourcePath and self.outputPath):
            raise ValueError("sourcePath and outputPath are both required")
        if not url_mode and not path_mode:
            raise ValueError("a transfer mode is required")
        for name, value in (
            ("sourceUrl", self.sourceUrl),
            ("uploadUrl", self.uploadUrl),
            ("callbackUrl", self.callbackUrl),
        ):
            if not value:
                continue
            parsed = urlsplit(value)
            if parsed.scheme not in {"http", "https"} or not parsed.hostname:
                raise ValueError(f"{name} must use HTTP or HTTPS")
            if parsed.username or parsed.password:
                raise ValueError(f"{name} must not contain user information")
        return self


class JobView(BaseModel):
    jobId: str
    status: JobStatus
    errorCode: str | None = None
    errorMessage: str | None = None


class CallbackPayload(BaseModel):
    status: str
    resultFileId: str | None = None
    errorCode: str | None = None
    errorMessage: str | None = None
