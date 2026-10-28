"""
FastAPI router for AI Dual-Carbon Copilot endpoints.
"""

from fastapi import APIRouter, Depends

from app.schemas.copilot import (
    PolicyQARequest,
    PolicyQAResponse,
    ProcurementAdviceRequest,
    ProcurementAdviceResponse,
    ReportAnalysisRequest,
    ReportAnalysisResponse,
)
from app.security import require_ml_service_secret
from app.services.copilot_service import CopilotService

router = APIRouter(prefix="/copilot", tags=["AI Copilot"])
_copilot_service = CopilotService()


@router.post(
    "/report-analysis",
    response_model=ReportAnalysisResponse,
    summary="Generate comprehensive carbon report audit and emission analysis",
)
def analyze_report(
    request: ReportAnalysisRequest,
    _auth: None = Depends(require_ml_service_secret),
) -> ReportAnalysisResponse:
    """Generate professional carbon accounting and abatement diagnosis narrative."""
    return _copilot_service.generate_report_analysis(request)


@router.post(
    "/policy-qa",
    response_model=PolicyQAResponse,
    summary="Dual-carbon standards and GB/T 32150 compliance QA",
)
def policy_qa(
    request: PolicyQARequest,
    _auth: None = Depends(require_ml_service_secret),
) -> PolicyQAResponse:
    """Answer technical and regulatory questions using carbon standard knowledge base."""
    return _copilot_service.answer_policy_question(request)


@router.post(
    "/procurement-advice",
    response_model=ProcurementAdviceResponse,
    summary="Generate carbon quota procurement and compliance advice",
)
def procurement_advice(
    request: ProcurementAdviceRequest,
    _auth: None = Depends(require_ml_service_secret),
) -> ProcurementAdviceResponse:
    """Calculate shortfall and output optimized trading strategy."""
    return _copilot_service.get_procurement_advice(request)
