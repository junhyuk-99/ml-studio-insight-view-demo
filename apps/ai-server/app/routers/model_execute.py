import logging

from fastapi import APIRouter, HTTPException

from app.schemas import (
    AutoEncoderExecuteRequest,
    AutoEncoderExecuteResponse,
    AutoEncoderResultRow,
    IsolationForestExecuteRequest,
    IsolationForestExecuteResponse,
    IsolationForestResultRow,
    RandomForestExecuteRequest,
    RandomForestExecuteResponse,
)
from app.services.autoencoder_service import run_autoencoder
from app.services.isolation_forest_service import run_isolation_forest
from app.services.random_forest_service import run_random_forest

ROUTER_PREFIX = "/api/model/execute"
router = APIRouter(prefix=ROUTER_PREFIX, tags=["model-execute"])
logger = logging.getLogger(__name__)

SUPPORTED_ISOLATION_FOREST = "ISOLATION_FOREST"
SUPPORTED_AUTOENCODER = "AUTOENCODER"
SUPPORTED_RANDOM_FOREST = "RANDOM_FOREST"


@router.post("/isolation-forest", response_model=IsolationForestExecuteResponse)
def execute_isolation_forest(request: IsolationForestExecuteRequest) -> IsolationForestExecuteResponse:
    if request.algorithm.strip().upper() != SUPPORTED_ISOLATION_FOREST:
        raise HTTPException(status_code=400, detail="algorithm must be ISOLATION_FOREST.")

    if not request.rows:
        raise HTTPException(status_code=400, detail="rows must not be empty.")

    try:
        outputs = run_isolation_forest(request.rows, request.model_params)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Isolation forest execution failed")
        raise HTTPException(status_code=500, detail=f"Model execution failed: {exc}") from exc

    results = [
        IsolationForestResultRow(
            window_start=output.window_start,
            window_end=output.window_end,
            anomaly_score=output.anomaly_score,
            is_anomaly=output.is_anomaly,
            health_index=output.health_index,
            status=output.status,
        )
        for output in outputs
    ]
    return IsolationForestExecuteResponse(ok=True, results=results)


@router.post("/autoencoder", response_model=AutoEncoderExecuteResponse)
def execute_autoencoder(request: AutoEncoderExecuteRequest) -> AutoEncoderExecuteResponse:
    if request.algorithm.strip().upper() != SUPPORTED_AUTOENCODER:
        raise HTTPException(status_code=400, detail="algorithm must be AUTOENCODER.")

    if not request.rows:
        raise HTTPException(status_code=400, detail="rows must not be empty.")

    try:
        outputs, meta = run_autoencoder(request.rows, request.model_params)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Autoencoder execution failed")
        raise HTTPException(status_code=500, detail=f"Model execution failed: {exc}") from exc

    results = [
        AutoEncoderResultRow(
            window_start=output.window_start,
            window_end=output.window_end,
            anomaly_score=output.anomaly_score,
            is_anomaly=output.is_anomaly,
            health_index=output.health_index,
            status=output.status,
        )
        for output in outputs
    ]
    return AutoEncoderExecuteResponse(ok=True, results=results, meta=meta)


@router.post("/random-forest", response_model=RandomForestExecuteResponse)
def execute_random_forest(request: RandomForestExecuteRequest) -> RandomForestExecuteResponse:
    algo_code = request.algo_code.strip().upper() if request.algo_code else SUPPORTED_RANDOM_FOREST
    if algo_code != SUPPORTED_RANDOM_FOREST:
        raise HTTPException(status_code=400, detail="algo_code must be RANDOM_FOREST.")

    if not request.rows and not request.records:
        raise HTTPException(status_code=400, detail="rows or records must not be empty.")

    try:
        return run_random_forest(request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Random Forest execution failed")
        raise HTTPException(status_code=500, detail=f"Model execution failed: {exc}") from exc
