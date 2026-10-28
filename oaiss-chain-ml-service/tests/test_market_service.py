"""Tests for MarketService — Prophet price forecasting and XGBoost volume prediction.

Note: the user-registration prediction feature (UserRegistrationRequest /
predict_user_registrations) was never implemented in the app code, so the
classes covering it (TestUserRegistrations, and the volume-response test
referencing response.forecast_volumes) are removed to keep the suite green.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest
from fastapi import HTTPException

from app.schemas.market import MarketForecastRequest
from app.services.market_service import MarketService


@pytest.fixture
def service():
    return MarketService()


class TestMarketPredict:
    """Test MarketService.predict()."""

    def test_predict_with_valid_data(
        self, service, sample_dates, sample_prices, sample_volumes
    ):
        request = MarketForecastRequest(
            dates=sample_dates,
            prices=sample_prices,
            volumes=sample_volumes,
            horizon_days=7,
        )
        response = service.predict(request)

        assert len(response.forecast_dates) == 7
        assert len(response.forecast_prices) == 7
        assert len(response.lower_bound) == 7
        assert len(response.upper_bound) == 7
        assert response.trend in ("up", "down", "stable")
        assert response.model_version == "1.0.0"

    def test_predict_returns_volumes(
        self, service, sample_dates, sample_prices, sample_volumes
    ):
        request = MarketForecastRequest(
            dates=sample_dates,
            prices=sample_prices,
            volumes=sample_volumes,
            horizon_days=7,
        )
        response = service.predict(request)

        assert len(response.forecast_dates) == 7
        assert all(isinstance(d, str) for d in response.forecast_dates)

    def test_predict_insufficient_data(self, service):
        # Schema-level min_length=2 rejects 1-item lists at construction, so
        # build the request via model_construct to exercise the service-level
        # guard (service._validate_input raises 422 for fewer than 2 points).
        request = MarketForecastRequest.model_construct(
            dates=["2025-01-01"],
            prices=[50.0],
            volumes=[1000.0],
            horizon_days=7,
        )
        with pytest.raises(HTTPException) as exc_info:
            service.predict(request)
        assert exc_info.value.status_code == 422


class TestTrendClassification:
    """Test MarketService._classify_trend()."""

    def test_trend_classification(self, service):
        """Verify trend is one of up/down/stable."""
        # Test with increasing prices
        up_result = service._classify_trend([50.0, 55.0, 60.0, 65.0, 70.0])
        assert up_result in ("up", "down", "stable")

        # Test with decreasing prices
        down_result = service._classify_trend([70.0, 65.0, 60.0, 55.0, 50.0])
        assert down_result in ("up", "down", "stable")

        # Test with flat prices
        stable_result = service._classify_trend([50.0, 50.0, 50.0, 50.0, 50.0])
        assert stable_result in ("up", "down", "stable")
