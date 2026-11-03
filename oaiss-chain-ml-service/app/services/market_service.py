"""
Market prediction service using Prophet for price forecasting
and XGBoost for supply/demand volume prediction.
"""

import logging
import time
from typing import Literal

import numpy as np
import pandas as pd
import xgboost as xgb
from fastapi import HTTPException
from prophet import Prophet

from app.schemas.market import MarketForecastRequest, MarketForecastResponse

logger = logging.getLogger(__name__)

TrendDirection = Literal["up", "down", "stable"]


class MarketService:
    """Carbon market price and volume prediction service.

    Uses Prophet for time-series price forecasting with confidence intervals,
    and XGBoost for supply/demand volume trend prediction.
    """

    def __init__(self) -> None:
        self.model_version = "1.0.0"

    def predict(
        self, request: MarketForecastRequest, include_volumes: bool = False
    ) -> MarketForecastResponse:
        """Generate carbon market price forecast using Prophet + XGBoost.

        Args:
            request: Market forecast request with historical dates, prices,
                     volumes, and forecast horizon.
            include_volumes: True 时一并返回 XGBoost 的成交量预测。
                只有 `/predict/market/supply-demand` 需要；价格类端点
                传 False 以免把成交量混进价格语义。

        Returns:
            MarketForecastResponse with forecast dates, prices, confidence
            intervals, trend direction, and model version.

        Raises:
            HTTPException: 422 if insufficient data or prediction fails.
        """
        self._validate_input(request)

        dates = pd.to_datetime(request.dates)
        prices = np.array(request.prices, dtype=float)
        volumes = np.array(request.volumes, dtype=float)
        horizon = request.horizon_days

        start_time = time.time()

        # Prophet price forecast
        forecast_prices, lower_bound, upper_bound, forecast_dates = (
            self._prophet_forecast(dates, prices, horizon)
        )

        # XGBoost volume forecast.
        #
        # 历史实现算完即弃（`_forecast_volumes`），导致「供需预测」端点
        # 返回的其实是价格预测——名字与结果不符。现在成交量真正进入响应，
        # 由 include_volumes 控制：只有 supply-demand 端点需要它。
        forecast_volumes = (
            self._xgboost_forecast(volumes, horizon) if include_volumes else []
        )

        # Determine trend direction
        trend = self._classify_trend(forecast_prices)

        duration = time.time() - start_time
        logger.info(
            "Market prediction completed: trend=%s, horizon=%d, volumes=%s, duration=%.2fs",
            trend,
            horizon,
            "included" if forecast_volumes else "omitted",
            duration,
        )

        return MarketForecastResponse(
            forecast_dates=forecast_dates,
            forecast_prices=forecast_prices,
            lower_bound=lower_bound,
            upper_bound=upper_bound,
            trend=trend,
            model_version=self.model_version,
            forecast_volumes=forecast_volumes,
            forecast_volume_unit="tCO2e" if forecast_volumes else None,
        )

    def _validate_input(self, request: MarketForecastRequest) -> None:
        """Validate that input list lengths match and have minimum data points."""
        n_dates = len(request.dates)
        n_prices = len(request.prices)
        n_volumes = len(request.volumes)

        if n_dates != n_prices or n_dates != n_volumes:
            raise HTTPException(
                status_code=422,
                detail=(
                    f"Input length mismatch: dates={n_dates}, "
                    f"prices={n_prices}, volumes={n_volumes}"
                ),
            )

        if n_dates < 2:
            raise HTTPException(
                status_code=422,
                detail="Insufficient data for forecasting: need at least 2 data points",
            )

    def _prophet_forecast(
        self,
        dates: pd.DatetimeIndex,
        prices: np.ndarray,
        horizon: int,
    ) -> tuple[list[str], list[float], list[float], list[str]]:
        """Run Prophet time-series forecast for carbon prices.

        Returns:
            Tuple of (forecast_prices, lower_bound, upper_bound, forecast_dates).
        """
        try:
            df = pd.DataFrame({"ds": dates, "y": prices})
            model = Prophet(
                interval_width=0.8,
                changepoint_prior_scale=0.05,
            )
            model.fit(df)

            future = model.make_future_dataframe(periods=horizon)
            forecast = model.predict(future)

            # Extract only the forecast period (last horizon rows)
            forecast_tail = forecast.iloc[-horizon:]

            forecast_dates: list[str] = (
                forecast_tail["ds"].dt.strftime("%Y-%m-%d").tolist()
            )
            # Prices/volumes are physically non-negative; Prophet can
            # extrapolate negative yhat with flat/short input series.
            forecast_prices: list[float] = (
                np.clip(forecast_tail["yhat"].to_numpy(), 0, None).round(2).tolist()
            )
            lower_bound: list[float] = (
                np.clip(forecast_tail["yhat_lower"].to_numpy(), 0, None)
                .round(2)
                .tolist()
            )
            upper_bound: list[float] = forecast_tail["yhat_upper"].round(2).tolist()

            return forecast_prices, lower_bound, upper_bound, forecast_dates

        except Exception as exc:
            logger.warning(
                "Prophet price forecast failed or backend unavailable (%s), falling back to polynomial trend estimation",
                exc,
            )
            try:
                x = np.arange(len(prices))
                poly = np.polyfit(x, prices, deg=min(1, len(prices) - 1))
                poly_fn = np.poly1d(poly)

                future_x = np.arange(len(prices), len(prices) + horizon)
                pred_y = poly_fn(future_x)

                residuals = prices - poly_fn(x)
                residual_std = float(np.std(residuals)) if len(residuals) > 1 else 1.0
                if residual_std < 0.01:
                    residual_std = 1.0

                last_date = dates[-1]
                future_dates = pd.date_range(
                    start=last_date + pd.Timedelta(days=1), periods=horizon, freq="D"
                )
                forecast_dates = future_dates.strftime("%Y-%m-%d").tolist()

                forecast_prices = np.clip(pred_y, 0, None).round(2).tolist()
                lower_bound = (
                    np.clip(pred_y - 1.28 * residual_std, 0, None).round(2).tolist()
                )
                upper_bound = (pred_y + 1.28 * residual_std).round(2).tolist()

                return forecast_prices, lower_bound, upper_bound, forecast_dates
            except Exception as fallback_exc:
                logger.error("Fallback trend estimation failed: %s", fallback_exc)
                raise HTTPException(
                    status_code=422,
                    detail=f"Price forecasting failed: {exc}",
                ) from exc

    def _xgboost_forecast(self, volumes: np.ndarray, horizon: int) -> list[float]:
        """Run XGBoost regression for supply/demand volume trend.

        Features: day-of-week, day-of-month, month, lag-7 volume, lag-30 volume.
        Uses iterative prediction to forecast future volumes.

        Returns:
            List of predicted volumes for the forecast horizon.
        """
        try:
            n = len(volumes)

            # Build feature matrix from historical data
            features, targets = self._build_volume_features(volumes)

            if len(targets) < 2:
                # Not enough data for meaningful XGBoost; return simple average
                avg_volume = float(np.mean(volumes))
                return [round(avg_volume, 2)] * horizon

            model = xgb.XGBRegressor(
                n_estimators=100,
                max_depth=3,
                learning_rate=0.1,
                objective="reg:squarederror",
            )
            model.fit(features, targets)

            # Iterative prediction
            recent_volumes = list(volumes)
            predictions: list[float] = []

            for i in range(horizon):
                day_idx = n + i
                lag_7 = (
                    recent_volumes[-7]
                    if len(recent_volumes) >= 7
                    else float(np.mean(recent_volumes))
                )
                lag_30 = (
                    recent_volumes[-30]
                    if len(recent_volumes) >= 30
                    else float(np.mean(recent_volumes))
                )

                feat = np.array(
                    [
                        [
                            day_idx % 7,  # day-of-week
                            day_idx % 30,  # day-of-month
                            day_idx % 12,  # month
                            lag_7,
                            lag_30,
                        ]
                    ]
                )

                pred = float(model.predict(feat)[0])
                pred = max(0.0, round(pred, 2))
                predictions.append(pred)
                recent_volumes.append(pred)

            return predictions

        except Exception as exc:
            logger.error("XGBoost forecast failed: %s", exc)
            # Fall back to simple average instead of failing entirely
            avg_volume = float(np.mean(volumes))
            return [round(avg_volume, 2)] * horizon

    @staticmethod
    def _build_volume_features(
        volumes: np.ndarray,
    ) -> tuple[np.ndarray, np.ndarray]:
        """Build feature matrix for XGBoost volume prediction.

        Creates features: day-of-week, day-of-month, month, lag-7, lag-30.
        Target: next-day volume.

        Returns:
            Tuple of (features, targets) as numpy arrays.
        """
        n = len(volumes)
        min_lag = 30  # Need at least 30 points for lag-30

        features: list[list[float]] = []
        targets: list[float] = []

        for i in range(min_lag, n - 1):
            day_idx = i + 1
            feat = [
                day_idx % 7,  # day-of-week
                day_idx % 30,  # day-of-month
                day_idx % 12,  # month
                float(volumes[i - 7]),  # lag-7
                float(volumes[i - 30]),  # lag-30
            ]
            features.append(feat)
            targets.append(float(volumes[i + 1]))

        return np.array(features), np.array(targets)

    @staticmethod
    def _classify_trend(forecast_prices: list[float]) -> TrendDirection:
        """Classify trend direction based on forecast price delta.

        Delta > 2% of first price -> "up"
        Delta < -2% of first price -> "down"
        Otherwise -> "stable"
        """
        if not forecast_prices or forecast_prices[0] == 0:
            return "stable"

        delta = forecast_prices[-1] - forecast_prices[0]
        threshold = abs(forecast_prices[0]) * 0.02

        if delta > threshold:
            return "up"
        if delta < -threshold:
            return "down"
        return "stable"
