"""
Tests for AI Dual-Carbon Copilot service and endpoints.
"""

from fastapi.testclient import TestClient

from app.config import settings
from app.main import app
from app.schemas.copilot import (
    PolicyQARequest,
    ProcurementAdviceRequest,
    ReportAnalysisRequest,
)
from app.services.copilot_service import CopilotService

client = TestClient(app)
AUTH_HEADERS = {"X-ML-Service-Secret": settings.ml_service_secret}


def test_copilot_service_report_analysis():
    """Verify report analysis generation, key findings, and SHA-256 hash calculation."""
    service = CopilotService()
    req = ReportAnalysisRequest(
        report_id=180,
        enterprise_name="绿色能源科技有限公司",
        accounting_period="2026-Q4",
        total_emission=370.5,
        scope1_emission=250.5,
        scope2_emission=120.0,
        desulf_emission=22.0,
    )
    res = service.generate_report_analysis(req)

    assert res.report_id == 180
    assert len(res.summary) > 20
    assert len(res.key_findings) >= 2
    assert len(res.reduction_suggestions) >= 2
    assert len(res.analysis_hash) == 64  # SHA-256 hex length
    assert "tCO" in res.full_text


def test_copilot_service_report_analysis_insufficient_data():
    """排放量缺失时必须如实返回「数据不足」，不得编造任何数值充当模型结论。"""
    service = CopilotService()
    req = ReportAnalysisRequest(
        report_id=999,
        enterprise_name="无数据企业",
        accounting_period="2026-Q4",
        total_emission=0.0,
    )
    res = service.generate_report_analysis(req)

    assert res.insufficient_data is True
    assert res.insufficient_data_reason
    assert "数据不足" in res.full_text
    assert res.key_findings == []
    assert res.reduction_suggestions == []
    assert len(res.analysis_hash) == 64
    # 历史伪造常量绝不能再出现在输出里
    for fabricated in ("370.50", "250.50", "120.00", "22.00"):
        assert fabricated not in res.full_text


def test_copilot_service_report_analysis_recovers_from_emission_details():
    """主字段为 0 但 emission_details 有真实数据时应正常分析，而非走数据不足分支。"""
    service = CopilotService()
    req = ReportAnalysisRequest(
        report_id=1001,
        enterprise_name="明细企业",
        accounting_period="2026-Q1",
        total_emission=0.0,
        emission_details='{"totalEmission": 500.0, "scope1": 300.0, "scope2": 200.0, "desulf": 10.0}',
    )
    res = service.generate_report_analysis(req)

    assert res.insufficient_data is False
    assert "500.00" in res.full_text
    assert len(res.key_findings) >= 2


def test_copilot_service_policy_qa():
    """Verify policy QA for Scope 1, Scope 2, and default questions."""
    service = CopilotService()

    # Scope 1
    req1 = PolicyQARequest(question="请问什么是范围一直接排放？")
    res1 = service.answer_policy_question(req1)
    assert "直接" in res1.answer
    assert len(res1.relevant_standards) > 0

    # Scope 2
    req2 = PolicyQARequest(question="什么是范围二外购电力？")
    res2 = service.answer_policy_question(req2)
    assert "间接" in res2.answer

    # Oxidation factor
    req3 = PolicyQARequest(question="碳氧化率OF是什么意思？")
    res3 = service.answer_policy_question(req3)
    assert "氧化" in res3.answer


def test_copilot_service_procurement_advice():
    """Verify shortfall calculation and procurement strategy advice."""
    service = CopilotService()

    # Shortfall > 0 (Risk)
    req_shortfall = ProcurementAdviceRequest(
        enterprise_id=1,
        current_emission=50000.0,
        current_quota=38000.0,
        current_market_price=58.20,
    )
    res_shortfall = service.get_procurement_advice(req_shortfall)
    assert res_shortfall.shortfall_tons == 12000.0
    assert res_shortfall.risk_level == "HIGH"
    assert res_shortfall.estimated_cost > 0
    assert len(res_shortfall.action_items) >= 2

    # Surplus (Quota sufficient)
    req_surplus = ProcurementAdviceRequest(
        enterprise_id=1,
        current_emission=30000.0,
        current_quota=50000.0,
        current_market_price=58.20,
    )
    res_surplus = service.get_procurement_advice(req_surplus)
    assert res_surplus.shortfall_tons == -20000.0
    assert res_surplus.risk_level == "ZERO"
    assert res_surplus.estimated_cost == 0.0


def test_copilot_api_endpoints():
    """Verify HTTP API endpoints for Copilot router."""
    # 1. Report Analysis
    res = client.post(
        "/copilot/report-analysis",
        json={
            "report_id": "test-1",
            "enterprise_name": "测试企业",
            "accounting_period": "2026-Q1",
            "total_emission": 1000.0,
            "scope1_emission": 800.0,
            "scope2_emission": 200.0,
        },
        headers=AUTH_HEADERS,
    )
    assert res.status_code == 200
    data = res.json()
    assert len(data["analysis_hash"]) == 64

    # 2. Policy QA
    res_qa = client.post(
        "/copilot/policy-qa",
        json={"question": "什么是脱硫过程排放？"},
        headers=AUTH_HEADERS,
    )
    assert res_qa.status_code == 200
    assert "脱硫" in res_qa.json()["answer"]

    # 3. Procurement Advice
    res_adv = client.post(
        "/copilot/procurement-advice",
        json={
            "current_emission": 500.0,
            "current_quota": 400.0,
            "current_market_price": 50.0,
        },
        headers=AUTH_HEADERS,
    )
    assert res_adv.status_code == 200
    assert res_adv.json()["shortfall_tons"] == 100.0
