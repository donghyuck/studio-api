from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="STUDIO_PANDOC_", extra="ignore")

    internal_token: str = ""
    callback_token: str = ""
    max_input_bytes: int = 100 * 1024 * 1024
    max_output_bytes: int = 200 * 1024 * 1024
    timeout_seconds: int = 300
    max_concurrent_jobs: int = 2
    work_dir: Path = Path("/tmp/pandoc-work")
    cleanup_enabled: bool = True
    allow_remote_resources: bool = False
    local_path_enabled: bool = False
    shared_volume_root: Path = Path("/shared")
    pandoc_binary: str = "pandoc"


settings = Settings()
