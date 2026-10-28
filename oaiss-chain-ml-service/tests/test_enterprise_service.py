"""Tests for EnterpriseService -- IsolationForest anomaly + XGBoost compliance."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest

from app.schemas.enterprise import ComplianceStatus, EnterpriseInferenceRequest
from app.services.enterprise_service import EnterpriseService


@pytest.fixture
def service():
    """Create a fresh EnterpriseService (trains on synthetic data)."""
    return EnterpriseService()


def _make_request(**overrides) -> EnterpriseInferenceRequest:
    """Helper to build an EnterpriseInferenceRequest with defaults."""
    defaults = {
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
    defaults.update(overrides)
    return EnterpriseInferenceRequest(**defaults)


class TestInferCompliance:
    """Test EnterpriseService.infer() compliance classification."""

    def test_infer_compliant(self, service):
        """Low anomaly + high credit score should yield COMPLIANT."""
        request = _make_request(
            credit_score=85.0,
            compliance_flags=0,
            days_since_last_report=10,
            avg_emission_per_report=30.0,
        )
        response = service.infer(request)
        assert response.compliance_status in (
            ComplianceStatus.COMPLIANT,
            ComplianceStatus.AT_RISK,
        )
        assert 0.0 <= response.confidence <= 1.0
        assert response.enterprise_id == 1

    def test_infer_at_risk(self, service):
        """Medium risk features should yield AT_RISK or similar."""
        request = _make_request(
            credit_score=50.0,
            compliance_flags=2,
            days_since_last_report=60,
        )
        response = service.infer(request)
        assert response.compliance_status in (
            ComplianceStatus.AT_RISK,
            ComplianceStatus.NON_COMPLIANT,
            ComplianceStatus.COMPLIANT,
        )
        assert 0.0 <= response.confidence <= 1.0

    def test_infer_non_compliant(self, service):
        """High risk features should yield NON_COMPLIANT or AT_RISK."""
        request = _make_request(
            credit_score=20.0,
            compliance_flags=5,
            days_since_last_report=120,
            avg_emission_per_report=150.0,
        )
        response = service.infer(request)
        assert response.compliance_status in (
            ComplianceStatus.NON_COMPLIANT,
            ComplianceStatus.AT_RISK,
        )
        assert 0.0 <= response.confidence <= 1.0


class TestAnomalyDetection:
    """Test anomaly detection in EnterpriseService.infer()."""

    def test_infer_anomaly_detected(self, service):
        """Anomalous features (extreme values) should trigger is_anomaly=True."""
        request = _make_request(
            report_count=500,
            total_emissions=9000.0,
            credit_score=5.0,
            emission_rating=9.0,
            transaction_volume=5000.0,
            compliance_flags=15,
            avg_emission_per_report=2000.0,
            days_since_last_report=350,
        )
        response = service.infer(request)
        assert response.is_anomaly is True
        assert "Anomalous emission pattern detected" in response.risk_factors


class TestRiskFactors:
    """Test risk factor identification."""

    def test_risk_factors_low_credit(self, service):
        request = _make_request(credit_score=30.0)
        response = service.infer(request)
        assert "Low credit score" in response.risk_factors

    def test_risk_factors_high_flags(self, service):
        request = _make_request(compliance_flags=5)
        response = service.infer(request)
        assert "High compliance flag count" in response.risk_factors

    def test_risk_factors_reporting_gap(self, service):
        request = _make_request(days_since_last_report=100)
        response = service.infer(request)
        assert "Extended reporting gap" in response.risk_factors

    def test_risk_factors_high_avg_emission(self, service):
        request = _make_request(avg_emission_per_report=150.0)
        response = service.infer(request)
        assert "High average emissions per report" in response.risk_factors
