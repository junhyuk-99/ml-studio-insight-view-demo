import logging
import os
import sys
from datetime import datetime, timezone

from fastapi import FastAPI
from fastapi import HTTPException
from fastapi.responses import JSONResponse
from fastapi.routing import APIRoute

from app.routers.model_execute import router as model_execute_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)

app = FastAPI(title="ML Studio AI Server", version="0.1.0")
logger = logging.getLogger(__name__)
REQUIRED_RANDOM_FOREST_PATH = "/api/model/execute/random-forest"
STARTED_AT_UTC = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
APP_ENTRY_FILE = os.path.abspath(__file__)


@app.get("/health")
def health_check() -> dict[str, object]:
    return {
        "ok": True,
        "message": "ML_STUDIO_AI_SERVER is running",
        "version": app.version,
        "started_at_utc": STARTED_AT_UTC,
        "process_id": os.getpid(),
        "cwd": os.getcwd(),
        "entry_file": APP_ENTRY_FILE,
        "python_executable": sys.executable,
    }


@app.exception_handler(HTTPException)
async def http_exception_handler(_request, exc: HTTPException) -> JSONResponse:
    return JSONResponse(status_code=exc.status_code, content={"ok": False, "message": str(exc.detail)})


@app.exception_handler(Exception)
async def unhandled_exception_handler(_request, exc: Exception) -> JSONResponse:
    logger.exception("Unhandled exception on ML_STUDIO_AI_SERVER", exc_info=exc)
    return JSONResponse(status_code=500, content={"ok": False, "message": "Internal server error"})


app.include_router(model_execute_router)


@app.on_event("startup")
def log_registered_routes() -> None:
    logger.info(
        "ML_STUDIO_AI_SERVER startup context: pid=%s cwd=%s entry_file=%s python=%s",
        os.getpid(),
        os.getcwd(),
        APP_ENTRY_FILE,
        sys.executable,
    )

    route_descriptions: list[str] = []
    registered_paths: set[str] = set()

    for route in app.routes:
        if not isinstance(route, APIRoute):
            continue

        methods = sorted(method for method in route.methods if method not in {"HEAD", "OPTIONS"})
        route_descriptions.append(f"{','.join(methods)} {route.path}")
        registered_paths.add(route.path)

    route_descriptions.sort()
    logger.info("Registered FastAPI routes (%s)", len(route_descriptions))
    for description in route_descriptions:
        logger.info("Registered route: %s", description)
    if REQUIRED_RANDOM_FOREST_PATH in registered_paths:
        logger.info("Verified required Random Forest endpoint: POST %s", REQUIRED_RANDOM_FOREST_PATH)
    else:
        raise RuntimeError(f"Missing required Random Forest endpoint: POST {REQUIRED_RANDOM_FOREST_PATH}")
