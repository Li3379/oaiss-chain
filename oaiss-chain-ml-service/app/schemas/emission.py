"""
Emission forecast request/response schemas.
"""

from pydantic import BaseModel, Field


class EmissionForecastRequest(BaseModel):
    """Request schema for carbon emission forecasting."""

    enterprise_id: int = Field(..., description="Enterprise ID")
    dates: list[str] = Field(..., min_length=1, max_length=1000, description="ISO 8601 date strings")
    emissions: list[float] = Field(
        ..., min_length=1, max_length=1000, description="Historical emission values"
    )
    sector: str = Field(
        default="power_generation", max_length=100, description="Industry sector"
    )
    horizon_days: int = Field(
        default=180, ge=1, le=365, description="Forecast horizon in days"
    )


class EmissionForecastResponse(BaseModel):
    """Response schema for carbon emission forecasting."""

    enterprise_id: int
    forecast_dates: list[str]
    forecast_emissions: list[float]
    lower_bound: list[float]
    upper_bound: list[float]
    trend: str  # "up" | "stable" | "down"
    confidence: float = Field(..., ge=0.0, le=1.0)
    model_version: str = "1.0.0"
