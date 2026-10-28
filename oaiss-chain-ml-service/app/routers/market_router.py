"""
FastAPI router for carbon market prediction endpoints.
"""

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.security import require_ml_service_secret
from app.schemas.market import MarketForecastRequest, MarketForecastResponse
from app.services.market_service import MarketService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/predict/market", tags=["market-prediction"])

market_service = MarketService()


@router.post(
    "/trend",
    response_model=MarketForecastResponse,
    summary="Predict market trend",
    description="Generate carbon market trend forecast using Prophet time-series model.",
    responses={
        200: {"description": "Market trend prediction generated successfully"},
        422: {"description": "Invalid input data or insufficient data points"},
    },
)
def predict_trend(
    request: MarketForecastRequest,
    _authorized: Annotated[None, Depends(require_ml_service_secret)],
) -> MarketForecastResponse:
    """Predict carbon market trend direction and forecast prices."""
    logger.info(
        "Market trend prediction requested: %d data points, %d day horizon",
        len(request.dates),
        request.horizon_days,
    )
    try:
        return market_service.predict(request)
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Market trend prediction failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Prediction failed: {exc}",
        ) from exc


@router.post(
    "/price",
    response_model=MarketForecastResponse,
    summary="Predict carbon price",
    description="Generate carbon price forecast with confidence intervals using Prophet.",
    responses={
        200: {"description": "Carbon price prediction generated successfully"},
        422: {"description": "Invalid input data or insufficient data points"},
    },
)
def predict_price(
    request: MarketForecastRequest,
    _authorized: Annotated[None, Depends(require_ml_service_secret)],
) -> MarketForecastResponse:
    """Predict carbon price with confidence intervals."""
    logger.info(
        "Carbon price prediction requested: %d data points, %d day horizon",
        len(request.dates),
        request.horizon_days,
    )
    try:
        return market_service.predict(request)
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Carbon price prediction failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Prediction failed: {exc}",
        ) from exc


@router.post(
    "/supply-demand",
    response_model=MarketForecastResponse,
    summary="Predict supply and demand",
    description="Generate supply/demand volume forecast using XGBoost.",
    responses={
        200: {"description": "Supply/demand prediction generated successfully"},
        422: {"description": "Invalid input data or insufficient data points"},
    },
)
def predict_supply_demand(
    request: MarketForecastRequest,
    _authorized: Annotated[None, Depends(require_ml_service_secret)],
) -> MarketForecastResponse:
    """Predict supply and demand volume trends."""
    logger.info(
        "Supply/demand prediction requested: %d data points, %d day horizon",
        len(request.dates),
        request.horizon_days,
    )
    try:
        return market_service.predict(request)
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Supply/demand prediction failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Prediction failed: {exc}",
        ) from exc
