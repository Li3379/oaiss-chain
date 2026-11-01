"""Tests for EmissionService — Prophet-based carbon emission forecasting."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest
from fastapi import HTTPException

from app.schemas.emission import EmissionForecastRequest
from app.services.emission_service import EmissionService


@pytest.fixture
def service():
    return EmissionService()


class TestEmissionPredict:
    """Test EmissionService.predict()."""

    def test_predict_with_valid_data(self, service, sample_dates, increasing_emissions):
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=sample_dates,
            emissions=increasing_emissions,
            horizon_days=7,
        )
        response = service.predict(request)

        assert response.enterprise_id == 1
        assert len(response.forecast_dates) == 7
        assert len(response.forecast_emissions) == 7
        assert len(response.lower_bound) == 7
        assert len(response.upper_bound) == 7
        assert response.trend in ("up", "down", "stable")
        assert 0.0 <= response.confidence <= 1.0
        assert response.model_version == "1.0.0"

    def test_predict_with_minimum_data_points(self, service):
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=["2025-01-01", "2025-01-02"],
            emissions=[100.0, 110.0],
            horizon_days=3,
        )
        response = service.predict(request)

        assert len(response.forecast_dates) == 3
        assert len(response.forecast_emissions) == 3

    def test_predict_with_insufficient_data(self, service):
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=["2025-01-01"],
            emissions=[100.0],
            horizon_days=7,
        )
        with pytest.raises(HTTPException) as exc_info:
            service.predict(request)
        assert exc_info.value.status_code == 422

    def test_predict_with_mismatched_lengths(self, service):
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=["2025-01-01", "2025-01-02", "2025-01-03"],
            emissions=[100.0, 110.0],
            horizon_days=7,
        )
        with pytest.raises(HTTPException) as exc_info:
            service.predict(request)
        assert exc_info.value.status_code == 422


class TestConfidence:
    """Test EmissionService._compute_confidence()."""

    def test_confidence_computation(self, service, sample_dates, increasing_emissions):
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=sample_dates,
            emissions=increasing_emissions,
            horizon_days=7,
        )
        response = service.predict(request)

        assert 0.0 <= response.confidence <= 1.0

    def test_confidence_narrow_interval(self, service):
        """Constant data should yield high confidence (narrow intervals)."""
        dates = [f"2025-01-{i:02d}" for i in range(1, 31)]
        emissions = [200.0] * 30
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=dates,
            emissions=emissions,
            horizon_days=5,
        )
        response = service.predict(request)
        assert response.confidence >= 0.5


class TestTrendClassification:
    """Test EmissionService._classify_trend()."""

    def test_trend_classification_up(self, service, sample_dates, increasing_emissions):
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=sample_dates,
            emissions=increasing_emissions,
            horizon_days=7,
        )
        response = service.predict(request)
        assert response.trend == "up"

    def test_trend_classification_down(
        self, service, sample_dates, decreasing_emissions
    ):
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=sample_dates,
            emissions=decreasing_emissions,
            horizon_days=7,
        )
        response = service.predict(request)
        assert response.trend == "down"

    def test_trend_classification_stable(self, service, sample_dates, stable_emissions):
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=sample_dates,
            emissions=stable_emissions,
            horizon_days=7,
        )
        response = service.predict(request)
        assert response.trend == "stable"


class TestResponseStructure:
    """Test response structure completeness."""

    def test_response_structure(self, service, sample_dates, increasing_emissions):
        request = EmissionForecastRequest(
            enterprise_id=1,
            dates=sample_dates,
            emissions=increasing_emissions,
            horizon_days=10,
        )
        response = service.predict(request)

        assert hasattr(response, "enterprise_id")
        assert hasattr(response, "forecast_dates")
        assert hasattr(response, "forecast_emissions")
        assert hasattr(response, "lower_bound")
        assert hasattr(response, "upper_bound")
        assert hasattr(response, "trend")
        assert hasattr(response, "confidence")
        assert hasattr(response, "model_version")
