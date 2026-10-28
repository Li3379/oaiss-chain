"""
Enterprise inference request/response schemas.
"""

from enum import Enum

from pydantic import BaseModel, Field


class ComplianceStatus(str, Enum):
    """Enterprise compliance status levels."""

    COMPLIANT = "compliant"
    AT_RISK = "at_risk"
    NON_COMPLIANT = "non_compliant"


class EnterpriseInferenceRequest(BaseModel):
    """Request schema for enterprise compliance inference and anomaly detection."""

    enterprise_id: int = Field(..., gt=0)
    report_count: int = Field(..., ge=0)
    total_emissions: float = Field(..., ge=0.0)
    credit_score: float = Field(..., ge=0.0, le=100.0)
    emission_rating: float = Field(..., ge=0.0)
    transaction_volume: float = Field(..., ge=0.0)
    compliance_flags: int = Field(..., ge=0)
    avg_emission_per_report: float = Field(..., ge=0.0)
    days_since_last_report: int = Field(..., ge=0)


class EnterpriseInferenceResponse(BaseModel):
    """Response schema for enterprise compliance inference and anomaly detection."""

    enterprise_id: int
    compliance_status: ComplianceStatus
    confidence: float = Field(..., ge=0.0, le=1.0)
    anomaly_score: float
    is_anomaly: bool
    risk_factors: list[str] = Field(default_factory=list)
    model_version: str = "1.0.0"
