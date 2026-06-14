import secrets

from fastapi import Depends, FastAPI, Header, HTTPException, status

from app.config import settings
from app.models import JobView, PandocJobRequest
from app.service import PandocJobService

app = FastAPI(title="Studio Pandoc Worker", version="0.1.0")
service = PandocJobService(settings)


def verify_token(x_internal_token: str = Header(default="")) -> None:
    if not settings.internal_token or not secrets.compare_digest(
        x_internal_token, settings.internal_token
    ):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post(
    "/internal/pandoc/jobs",
    response_model=JobView,
    status_code=status.HTTP_202_ACCEPTED,
    dependencies=[Depends(verify_token)],
)
async def create_job(request: PandocJobRequest) -> JobView:
    return service.submit(request)


@app.get(
    "/internal/pandoc/jobs/{job_id}",
    response_model=JobView,
    dependencies=[Depends(verify_token)],
)
async def get_job(job_id: str) -> JobView:
    job = service.jobs.get(job_id)
    if job is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    return job
