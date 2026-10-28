"""
Emission prediction router.

Exposes POST /predict/emission/forecast for Prophet-based
carbon emission time-series forecasting.
"""

import logging
from typing import Annotated

from fastapi import APIRouter, Depends

from app.security import require_ml_service_secret
from app.schemas.emission import EmissionForecastRequest, EmissionForecastResponse
from app.services.emission_service import EmissionService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/predict/emission", tags=["emission"])

emission_service = EmissionService()


@router.post(
    "/forecast",
    response_model=EmissionForecastResponse,
    summary="Predict future carbon emissions",
    description=(
        "Uses Prophet time-series regression to forecast carbon emissions "
        "based on historical data with confidence intervals and trend classification."
    ),
)
def predict_emission(
    request: EmissionForecastRequest,
    _authorized: Annotated[None, Depends(require_ml_service_secret)],
) -> EmissionForecastResponse:
    """Predict future carbon emissions using Prophet regression."""
    logger.info(
        "Emission prediction request: enterprise_id=%d, horizon=%d, "
        "data_points=%d, sector=%s",
        request.enterprise_id,
        request.horizon_days,
        len(request.dates),
        request.sector,
    )
    return emission_service.predict(request)
