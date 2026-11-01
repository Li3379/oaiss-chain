"""
OAISS CHAIN ML Service - FastAPI Application Entry Point

Provides prediction endpoints for carbon emission forecasting,
enterprise inference, and market forecasting.
"""

import logging
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI

from app.routers.copilot_router import router as copilot_router
from app.routers.emission_router import router as emission_router
from app.routers.enterprise_router import router as enterprise_router
from app.routers.market_router import router as market_router
from app.security import require_ml_service_secret

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(application: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifespan: startup and shutdown hooks."""
    logger.info("Starting ML service")
    yield
    logger.info("Shutting down ML service")


app = FastAPI(
    title="OAISS Chain ML Service",
    version="1.0.0",
    lifespan=lifespan,
)

# Register prediction routers with global shared-secret authentication
app.include_router(market_router, dependencies=[Depends(require_ml_service_secret)])
app.include_router(enterprise_router, dependencies=[Depends(require_ml_service_secret)])
app.include_router(emission_router, dependencies=[Depends(require_ml_service_secret)])
app.include_router(copilot_router, dependencies=[Depends(require_ml_service_secret)])


@app.get("/health")
async def health_check() -> dict[str, str]:
    """Health check endpoint for Docker and load balancer probes."""
    return {"status": "healthy", "version": "1.0.0"}
