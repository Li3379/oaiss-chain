"""Endpoint-level tests for ML service routers using FastAPI TestClient."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest
from fastapi.testclient import TestClient

from app.config import settings
from app.main import app

TEST_SECRET = "test-secret-for-endpoint-tests"


@pytest.fixture(autouse=True)
def configure_secret():
    """Set and restore ML_SERVICE_SECRET for each test."""
    original = settings.ml_service_secret
    settings.ml_service_secret = TEST_SECRET
    yield
    settings.ml_service_secret = original


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def auth_headers():
    return {"X-ML-Service-Secret": TEST_SECRET}


def _valid_emission_payload():
    return {
        "enterprise_id": 1,
        "dates": [f"2025-01-{i:02d}" for i in range(1, 31)],
        "emissions": [float(100 + i * 5) for i in range(30)],
        "sector": "power_generation",
        "horizon_days": 7,
    }


def _valid_market_payload():
    return {
        "dates": [f"2025-01-{i:02d}" for i in range(1, 31)],
        "prices": [50.0 + (i % 3) * 2.0 for i in range(30)],
        "volumes": [1000.0 + (i % 5) * 50.0 for i in range(30)],
        "horizon_days": 7,
    }


def _valid_enterprise_payload():
    return {
        "enterprise_id": 1,
        "report_count": 15,
        "total_emissions": 500.0,
        "credit_score": 85.0,
        "emission_rating": 3.0,
        "transaction_volume": 200.0,
        "compliance_flags": 0,
        "avg_emission_per_report": 33.3,
        "days_since_last_report": 10,
    }


class TestEmissionEndpoint:
    """Tests for POST /predict/emission/forecast."""

    def test_valid_request_returns_200(self, client, auth_headers):
        response = client.post(
            "/predict/emission/forecast",
            json=_valid_emission_payload(),
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert "forecast_dates" in data
        assert "forecast_emissions" in data
        assert len(data["forecast_dates"]) == 7

    def test_invalid_request_returns_422(self, client, auth_headers):
        # Missing required fields
        response = client.post(
            "/predict/emission/forecast",
            json={"enterprise_id": 1},
            headers=auth_headers,
        )
        assert response.status_code == 422


class TestMarketEndpoints:
    """Tests for POST /predict/market/* endpoints."""

    def test_trend_valid_request_returns_200(self, client, auth_headers):
        response = client.post(
            "/predict/market/trend",
            json=_valid_market_payload(),
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert "forecast_prices" in data
        assert "trend" in data

    def test_price_valid_request_returns_200(self, client, auth_headers):
        response = client.post(
            "/predict/market/price",
            json=_valid_market_payload(),
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert "forecast_prices" in data
        assert "lower_bound" in data
        assert "upper_bound" in data

    def test_supply_demand_returns_200_with_volumes(self, client, auth_headers):
        response = client.post(
            "/predict/market/supply-demand",
            json=_valid_market_payload(),
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        # The endpoint returns the market forecast response shape; trend must
        # be present (XGBoost volume regression feeds the price forecast).
        assert "forecast_prices" in data
        assert "trend" in data


class TestEnterpriseEndpoints:
    """Tests for POST /api/v1/predict/enterprise/* endpoints.

    Note: only POST /api/v1/predict/enterprise/ (inference) is implemented.
    There is no /train route in the app -- EnterpriseService trains its
    models in-process at startup on synthetic data, so no train endpoint
    test exists.
    """

    def test_inference_valid_request_returns_200(self, client, auth_headers):
        response = client.post(
            "/api/v1/predict/enterprise/",
            json=_valid_enterprise_payload(),
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert "compliance_status" in data
        assert "confidence" in data
        assert "is_anomaly" in data


class TestAuthentication:
    """Test that endpoints reject requests without the secret header."""

    def test_emission_no_secret_returns_401(self, client):
        response = client.post(
            "/predict/emission/forecast",
            json=_valid_emission_payload(),
        )
        assert response.status_code == 401

    def test_market_trend_no_secret_returns_401(self, client):
        response = client.post(
            "/predict/market/trend",
            json=_valid_market_payload(),
        )
        assert response.status_code == 401

    def test_market_price_no_secret_returns_401(self, client):
        response = client.post(
            "/predict/market/price",
            json=_valid_market_payload(),
        )
        assert response.status_code == 401

    def test_market_supply_demand_no_secret_returns_401(self, client):
        response = client.post(
            "/predict/market/supply-demand",
            json=_valid_market_payload(),
        )
        assert response.status_code == 401

    def test_enterprise_infer_no_secret_returns_401(self, client):
        response = client.post(
            "/api/v1/predict/enterprise/",
            json=_valid_enterprise_payload(),
        )
        assert response.status_code == 401

    def test_copilot_policy_qa_no_secret_returns_401(self, client):
        response = client.post(
            "/copilot/policy-qa",
            json={"question": "测试政策问答"},
        )
        assert response.status_code == 401

    def test_copilot_report_analysis_no_secret_returns_401(self, client):
        response = client.post(
            "/copilot/report-analysis",
            json={
                "report_id": "test-sec",
                "enterprise_name": "测试企业",
                "accounting_period": "2026-Q1",
                "total_emission": 100.0,
            },
        )
        assert response.status_code == 401


class TestDoSProtection:
    """Test that requests exceeding array size limits are rejected (422) to prevent DoS."""

    def test_market_trend_excessive_data_points_rejected_422(
        self, client, auth_headers
    ):
        oversized_payload = {
            "dates": ["2025-01-01" for _ in range(1001)],
            "prices": [50.0] * 1001,
            "volumes": [1000.0] * 1001,
            "horizon_days": 7,
        }
        response = client.post(
            "/predict/market/trend",
            json=oversized_payload,
            headers=auth_headers,
        )
        assert response.status_code == 422

    def test_emission_forecast_excessive_data_points_rejected_422(
        self, client, auth_headers
    ):
        oversized_payload = {
            "enterprise_id": 1,
            "dates": ["2025-01-01" for _ in range(1001)],
            "emissions": [100.0] * 1001,
            "sector": "power_generation",
            "horizon_days": 7,
        }
        response = client.post(
            "/predict/emission/forecast",
            json=oversized_payload,
            headers=auth_headers,
        )
        assert response.status_code == 422
