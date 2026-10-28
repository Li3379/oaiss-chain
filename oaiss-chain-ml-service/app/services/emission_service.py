"""
Emission prediction service using Prophet for time-series forecasting.

Provides Prophet-based regression for carbon emission prediction
with confidence intervals and trend classification.
"""

import logging
import time
from typing import Literal

import numpy as np
import pandas as pd
from fastapi import HTTPException
from prophet import Prophet

from app.schemas.emission import EmissionForecastRequest, EmissionForecastResponse

logger = logging.getLogger(__name__)

TrendDirection = Literal["up", "down", "stable"]


class EmissionService:
    """Carbon emission time-series prediction service.

    Uses Prophet with multiplicative seasonality mode for emission
    data forecasting with confidence intervals and trend classification.
    """

    def __init__(self) -> None:
        self.model_version = "1.0.0"

    def predict(self, request: EmissionForecastRequest) -> EmissionForecastResponse:
        """Generate emission forecast using Prophet regression.

        Args:
            request: Emission forecast request with historical dates,
                     emission values, sector, and forecast horizon.

        Returns:
            EmissionForecastResponse with forecast dates, emissions,
            confidence intervals, trend direction, and model version.

        Raises:
            HTTPException: 422 if insufficient data or prediction fails.
        """
        self._validate_input(request)

        dates = pd.to_datetime(request.dates)
        emissions = np.array(request.emissions, dtype=float)
        horizon = request.horizon_days

        start_time = time.time()

        # Prophet emission forecast
        forecast_emissions, lower_bound, upper_bound, forecast_dates = (
            self._prophet_forecast(dates, emissions, horizon)
        )

        # Compute confidence from relative interval width
        confidence = self._compute_confidence(
            forecast_emissions, lower_bound, upper_bound
        )

        # Determine trend direction
        trend = self._classify_trend(forecast_emissions)

        duration = time.time() - start_time
        logger.info(
            "Emission prediction completed: enterprise_id=%d, trend=%s, "
            "horizon=%d, confidence=%.2f, duration=%.2fs",
            request.enterprise_id,
            trend,
            horizon,
            confidence,
            duration,
        )

        return EmissionForecastResponse(
            enterprise_id=request.enterprise_id,
            forecast_dates=forecast_dates,
            forecast_emissions=forecast_emissions,
            lower_bound=lower_bound,
            upper_bound=upper_bound,
            trend=trend,
            confidence=confidence,
            model_version=self.model_version,
        )

    def _validate_input(self, request: EmissionForecastRequest) -> None:
        """Validate input list lengths match and have minimum data points."""
        n_dates = len(request.dates)
        n_emissions = len(request.emissions)

        if n_dates != n_emissions:
            raise HTTPException(
                status_code=422,
                detail=(
                    f"Input length mismatch: dates={n_dates}, "
                    f"emissions={n_emissions}"
                ),
            )

        if n_dates < 2:
            raise HTTPException(
                status_code=422,
                detail="Insufficient data for emission forecasting: "
                       "need at least 2 data points",
            )

    def _prophet_forecast(
        self,
        dates: pd.DatetimeIndex,
        emissions: np.ndarray,
        horizon: int,
    ) -> tuple[list[float], list[float], list[float], list[str]]:
        """Run Prophet time-series forecast for carbon emissions.

        Uses multiplicative seasonality mode (appropriate for emission
        data which has proportional seasonal variation) and adds yearly
        seasonality if more than 2 years of data are available.

        Returns:
            Tuple of (forecast_emissions, lower_bound, upper_bound, forecast_dates).
        """
        try:
            df = pd.DataFrame({"ds": dates, "y": emissions})

            # Emission-specific Prophet configuration:
            # multiplicative seasonality for proportional seasonal variation
            # lower changepoint_prior_scale for more stable trends
            model = Prophet(
                interval_width=0.8,
                changepoint_prior_scale=0.05,
                seasonality_mode="multiplicative",
            )

            # Add explicit yearly seasonality when sufficient data exists
            if len(dates) > 24:
                model.add_seasonality(
                    name="yearly", period=365.25, fourier_order=10
                )

            model.fit(df)

            future = model.make_future_dataframe(periods=horizon, freq="D")
            forecast = model.predict(future)

            # Extract only the forecast period (last horizon rows)
            result = forecast.iloc[-horizon:]

            forecast_dates: list[str] = result["ds"].dt.strftime("%Y-%m-%d").tolist()
            # Carbon emissions are physically non-negative; Prophet can
            # extrapolate negative yhat with flat/short input series.
            forecast_emissions: list[float] = (
                np.clip(result["yhat"].to_numpy(), 0, None).round(2).tolist()
            )
            lower_bound: list[float] = (
                np.clip(result["yhat_lower"].to_numpy(), 0, None).round(2).tolist()
            )
            upper_bound: list[float] = result["yhat_upper"].round(2).tolist()

            return forecast_emissions, lower_bound, upper_bound, forecast_dates

        except Exception as exc:
            logger.warning(
                "Prophet emission forecast failed or backend unavailable (%s), falling back to polynomial trend estimation",
                exc,
            )
            try:
                x = np.arange(len(emissions))
                poly = np.polyfit(x, emissions, deg=min(1, len(emissions) - 1))
                poly_fn = np.poly1d(poly)

                future_x = np.arange(len(emissions), len(emissions) + horizon)
                pred_y = poly_fn(future_x)

                residuals = emissions - poly_fn(x)
                residual_std = float(np.std(residuals)) if len(residuals) > 1 else 1.0
                if residual_std < 0.01:
                    residual_std = 1.0

                last_date = dates[-1]
                future_dates = pd.date_range(
                    start=last_date + pd.Timedelta(days=1), periods=horizon, freq="D"
                )
                forecast_dates = future_dates.strftime("%Y-%m-%d").tolist()

                forecast_emissions = np.clip(pred_y, 0, None).round(2).tolist()
                lower_bound = np.clip(pred_y - 1.28 * residual_std, 0, None).round(2).tolist()
                upper_bound = (pred_y + 1.28 * residual_std).round(2).tolist()

                return forecast_emissions, lower_bound, upper_bound, forecast_dates
            except Exception as fallback_exc:
                logger.error("Fallback trend estimation failed: %s", fallback_exc)
                raise HTTPException(
                    status_code=422,
                    detail=f"Emission forecasting failed: {exc}",
                ) from exc

    @staticmethod
    def _compute_confidence(
        forecast_emissions: list[float],
        lower_bound: list[float],
        upper_bound: list[float],
    ) -> float:
        """Compute prediction confidence from relative interval width.

        Confidence = 1 - (avg_interval_width / avg_emission_value).
        Clamped to [0.0, 1.0]. Returns 0.5 if average emission is zero.
        """
        avg_width = float(np.mean(np.array(upper_bound) - np.array(lower_bound)))
        avg_emission = float(np.mean(np.array(forecast_emissions)))

        if avg_emission > 0:
            confidence = max(0.0, min(1.0, 1.0 - (avg_width / avg_emission)))
        else:
            confidence = 0.5

        return round(confidence, 4)

    @staticmethod
    def _classify_trend(forecast_emissions: list[float]) -> TrendDirection:
        """Classify trend direction based on forecast emission delta.

        Delta > 2% of first emission -> "up"
        Delta < -2% of first emission -> "down"
        Otherwise -> "stable"
        """
        if not forecast_emissions or forecast_emissions[0] == 0:
            return "stable"

        delta = forecast_emissions[-1] - forecast_emissions[0]
        threshold = abs(forecast_emissions[0]) * 0.02

        if delta > threshold:
            return "up"
        if delta < -threshold:
            return "down"
        return "stable"