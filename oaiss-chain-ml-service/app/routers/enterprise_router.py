"""
Enterprise inference FastAPI router.

Provides POST endpoint for enterprise compliance risk assessment
using IsolationForest anomaly detection + XGBoost classification.
"""

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException

from app.security import require_ml_service_secret
from app.schemas.enterprise import (
    EnterpriseInferenceRequest,
    EnterpriseInferenceResponse,
)
from app.services.enterprise_service import EnterpriseService

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/v1/predict/enterprise",
    tags=["Enterprise Inference"],
)

# Module-level singleton
enterprise_service = EnterpriseService()


@router.post(
    "/",
    response_model=EnterpriseInferenceResponse,
    summary="Enterprise compliance risk assessment",
    description="Generate enterprise compliance risk assessment "
    "using IsolationForest anomaly detection and XGBoost classification.",
)
async def infer_enterprise(
    request: EnterpriseInferenceRequest,
    _authorized: Annotated[None, Depends(require_ml_service_secret)],
) -> EnterpriseInferenceResponse:
    """Generate enterprise compliance risk assessment.

    Args:
        request: Enterprise feature data for inference.

    Returns:
        EnterpriseInferenceResponse with compliance status, confidence,
        anomaly detection results, and risk factors.
    """
    try:
        return enterprise_service.infer(request)
    except ValueError as exc:
        logger.error("Enterprise inference failed: %s", exc)
        raise HTTPException(status_code=422, detail=str(exc)) from exc
