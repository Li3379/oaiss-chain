"""
Schemas for AI Dual-Carbon Copilot services.
"""

from typing import Any, List, Optional
from pydantic import BaseModel, Field


class ReportAnalysisRequest(BaseModel):
    """Request for AI-assisted carbon accounting report analysis."""

    report_id: Optional[Any] = Field(default=None, description="Report ID or No")
    enterprise_name: str = Field(
        default="申报企业", max_length=200, description="Enterprise name"
    )
    accounting_period: str = Field(
        default="2026-Q4", max_length=50, description="Accounting period"
    )
    total_emission: float = Field(..., ge=0.0, description="Total emissions in tCO2e")
    scope1_emission: float = Field(default=0.0, ge=0.0, description="Scope 1 emissions")
    scope2_emission: float = Field(default=0.0, ge=0.0, description="Scope 2 emissions")
    desulf_emission: float = Field(
        default=0.0, ge=0.0, description="Desulfurization emissions"
    )
    emission_details: Optional[Any] = Field(
        default=None, description="Structured emission details or JSON"
    )


class ReportAnalysisResponse(BaseModel):
    """Response containing AI-generated carbon accounting analysis."""

    report_id: Optional[Any] = None
    summary: str = Field(..., description="Executive summary paragraph")
    key_findings: List[str] = Field(
        default_factory=list, description="Key diagnostic findings"
    )
    reduction_suggestions: List[str] = Field(
        default_factory=list, description="Concrete abatement suggestions"
    )
    full_text: str = Field(..., description="Full analytical narrative text")
    analysis_hash: str = Field(
        ..., description="SHA-256 hash of the full text for blockchain anchoring"
    )
    model_used: str = Field(
        ..., description="Name/version of the generative model used"
    )
    insufficient_data: bool = Field(
        default=False,
        description="True when the report carries no usable emission data; analysis fields are then intentionally empty",
    )
    insufficient_data_reason: Optional[str] = Field(
        default=None,
        description="Human-readable explanation returned only when insufficient_data is true",
    )


class PolicyQARequest(BaseModel):
    """Request for dual-carbon policy and standard question answering."""

    question: str = Field(
        ...,
        min_length=2,
        max_length=1000,
        description="User question about standards, formulas, or policy",
    )
    context_topic: Optional[str] = Field(
        default=None,
        max_length=200,
        description="Optional focus topic (e.g. power-generation, grid, fuel)",
    )


class PolicyQAResponse(BaseModel):
    """Response for policy and standard question answering."""

    answer: str = Field(..., description="Expert explanation and advice")
    relevant_standards: List[str] = Field(
        default_factory=list, description="Referenced official national standards"
    )
    model_used: str = Field(..., description="Model used to answer")


class ProcurementAdviceRequest(BaseModel):
    """Request for carbon quota compliance and trading procurement advice."""

    enterprise_id: Optional[Any] = None
    enterprise_name: Optional[str] = Field(default="本企业", max_length=200)
    current_emission: float = Field(
        ..., ge=0.0, description="Total verified emission in tons"
    )
    current_quota: float = Field(
        ..., ge=0.0, description="Available carbon tradable quota in tons"
    )
    current_market_price: float = Field(
        default=58.20, ge=0.0, description="Current market spot price per ton"
    )


class ProcurementAdviceResponse(BaseModel):
    """Response for carbon trading procurement and compliance advice."""

    shortfall_tons: float = Field(
        ..., description="Compliance shortfall (+) or surplus (-) in tons"
    )
    risk_level: str = Field(
        ..., description="Compliance risk level (HIGH, MEDIUM, LOW, ZERO)"
    )
    estimated_cost: float = Field(
        ..., description="Estimated cost to clear shortfall at current prices"
    )
    advice: str = Field(..., description="Executive strategy narrative")
    action_items: List[str] = Field(
        default_factory=list, description="Step-by-step actionable recommendations"
    )
    model_used: str = Field(..., description="Model used")
