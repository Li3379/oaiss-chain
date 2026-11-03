"""
Pydantic v2 schemas for market prediction endpoints.
"""

from pydantic import BaseModel, Field


class MarketForecastRequest(BaseModel):
    """Request schema for carbon market price/volume forecasting.

    Attributes:
        dates: ISO-8601 date strings for historical observations.
        prices: Carbon prices corresponding to each date.
        volumes: Trade volumes corresponding to each date.
        horizon_days: Number of days to forecast into the future.
    """

    dates: list[str] = Field(
        ...,
        min_length=2,
        max_length=1000,
        description="ISO-8601 date strings for historical observations",
        examples=[["2025-01-01", "2025-01-02", "2025-01-03"]],
    )
    prices: list[float] = Field(
        ...,
        min_length=2,
        max_length=1000,
        description="Carbon prices corresponding to each date",
        examples=[[50.0, 52.0, 48.5]],
    )
    volumes: list[float] = Field(
        ...,
        min_length=2,
        max_length=1000,
        description="Trade volumes corresponding to each date",
        examples=[[1000.0, 1200.0, 800.0]],
    )
    horizon_days: int = Field(
        default=30,
        ge=1,
        le=365,
        description="Number of days to forecast into the future",
    )


class MarketForecastResponse(BaseModel):
    """Response schema for carbon market price/volume forecasting.

    Attributes:
        forecast_dates: ISO-8601 date strings for the forecast period.
        forecast_prices: Predicted carbon prices for each forecast date.
        lower_bound: Lower bound of 80% confidence interval.
        upper_bound: Upper bound of 80% confidence interval.
        trend: Market trend direction: "up", "down", or "stable".
        model_version: Version identifier for the prediction model.
    """

    forecast_dates: list[str] = Field(
        description="ISO-8601 date strings for the forecast period",
    )
    forecast_prices: list[float] = Field(
        description="Predicted carbon prices for each forecast date",
    )
    lower_bound: list[float] = Field(
        description="Lower bound of 80% confidence interval",
    )
    upper_bound: list[float] = Field(
        description="Upper bound of 80% confidence interval",
    )
    trend: str = Field(
        description='Market trend direction: "up", "down", or "stable"',
    )
    model_version: str = Field(
        description="Version identifier for the prediction model",
    )
    # 成交量预测：仅 /predict/market/supply-demand 填充。
    #
    # 历史实现里 XGBoost 算出的成交量被 `_forecast_volumes` 丢弃 ——
    # 三个端点全都返回价格，"供需预测"名不副实。加这两个字段后，
    # 供需端点才真正携带成交量序列；价格端点保持为空列表，便于前端区分。
    forecast_volumes: list[float] = Field(
        default_factory=list,
        description="Predicted traded volumes per forecast date (supply-demand endpoint only)",
    )
    forecast_volume_unit: str | None = Field(
        default=None,
        description="Volume unit, e.g. tCO2e (supply-demand endpoint only)",
    )
