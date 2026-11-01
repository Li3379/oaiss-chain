"""
Dual-Carbon AI Copilot Service.
Supports OpenAI-compatible LLMs (DeepSeek / Qwen / Ollama) with an embedded
authoritative GB/T 32150-2015 domain expert rule engine for offline resiliency.
"""

import hashlib
import json
import logging

import httpx

from app.config import settings
from app.schemas.copilot import (
    PolicyQARequest,
    PolicyQAResponse,
    ProcurementAdviceRequest,
    ProcurementAdviceResponse,
    ReportAnalysisRequest,
    ReportAnalysisResponse,
)

logger = logging.getLogger(__name__)


class CopilotService:
    """Service providing generative carbon report analysis, policy QA, and procurement advice."""

    #: 未启用 / 不可达 LLM 时，响应体 model_used 的如实标注
    RULE_ENGINE_NAME = "builtin-rule-engine (GB/T 32150-2015, 未调用大模型)"

    def __init__(self) -> None:
        self.api_base = settings.llm_api_base
        self.api_key = settings.llm_api_key
        self.model_name = settings.llm_model
        self.ollama_base = settings.ollama_base_url
        self.ollama_model = settings.ollama_model
        self.timeout = settings.llm_timeout_seconds

    def _llm_targets(self) -> list[tuple[str, str, str]]:
        """仅在显式配置后才会产生 LLM 调用目标；不做隐式本机探测。"""
        targets: list[tuple[str, str, str]] = []
        if self.api_key:
            targets.append((self.api_base, self.api_key, self.model_name))
        if self.ollama_base:
            targets.append((self.ollama_base, "ollama", self.ollama_model))
        return targets

    def _call_llm_if_available(
        self, system_prompt: str, user_prompt: str
    ) -> tuple[str | None, str]:
        """Call external or local LLM if configured and accessible; return (content, model_used)."""
        targets = self._llm_targets()
        if not targets:
            logger.debug(
                "No LLM configured (LLM_API_KEY / OLLAMA_BASE_URL empty), using built-in rule engine"
            )

        for base_url, key, model in targets:
            try:
                url = f"{base_url.rstrip('/')}/chat/completions"
                headers = {
                    "Authorization": f"Bearer {key}",
                    "Content-Type": "application/json",
                }
                payload = {
                    "model": model,
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_prompt},
                    ],
                    "temperature": 0.3,
                    "max_tokens": 1000,
                }
                with httpx.Client(timeout=self.timeout) as client:
                    resp = client.post(url, headers=headers, json=payload)
                    if resp.status_code == 200:
                        data = resp.json()
                        content = data["choices"][0]["message"]["content"]
                        logger.info(
                            "Successfully received LLM response from %s with model %s",
                            base_url,
                            model,
                        )
                        return content, model
            except Exception as e:
                logger.debug(
                    "LLM endpoint %s not available (%s), continuing fallback",
                    base_url,
                    e,
                )

        return None, self.RULE_ENGINE_NAME

    def _insufficient_data_response(
        self, req: ReportAnalysisRequest
    ) -> ReportAnalysisResponse:
        """排放量为空/为零时如实返回「无法诊断」，不生成任何推算数值。"""
        message = (
            f"报告（{req.report_id if req.report_id is not None else '未提供编号'}）没有可用的排放量数据："
            f"排放总量与 emission_details 均为空或为 0，无法进行排放结构归因分析。"
            "请企业先完成碳核算并回填排放数据后重新发起诊断。"
        )
        full_text = f"【数据不足，无法生成诊断】\n\n{message}"
        return ReportAnalysisResponse(
            report_id=req.report_id,
            summary=message,
            key_findings=[],
            reduction_suggestions=[],
            full_text=full_text,
            analysis_hash=hashlib.sha256(full_text.encode("utf-8")).hexdigest(),
            model_used=self.RULE_ENGINE_NAME,
            insufficient_data=True,
            insufficient_data_reason=message,
        )

    def generate_report_analysis(
        self, req: ReportAnalysisRequest
    ) -> ReportAnalysisResponse:
        """Generate structured carbon audit and emission diagnosis report."""
        total = req.total_emission
        s1 = req.scope1_emission
        s2 = req.scope2_emission
        desulf = req.desulf_emission

        # 如果传入为 0，尝试从 emission_details 解析真实数值
        if total <= 0 and req.emission_details:
            try:
                ed = (
                    req.emission_details
                    if isinstance(req.emission_details, dict)
                    else json.loads(str(req.emission_details))
                )
                total = float(ed.get("totalEmission", ed.get("total_emission", 0)) or 0)
                s1 = float(
                    ed.get("scope1", 0)
                    if isinstance(ed.get("scope1"), (int, float))
                    else 0
                )
                s2 = float(
                    ed.get("scope2", 0)
                    if isinstance(ed.get("scope2"), (int, float))
                    else 0
                )
                if desulf <= 0 and isinstance(ed.get("desulf"), (int, float)):
                    desulf = float(ed.get("desulf"))
            except Exception:
                pass

        # 数据不足：绝不编造排放量冒充模型结论，直接返回可识别的「无法诊断」结果。
        # 此前这里会把 total/s1/s2/desulf 填充为 370.50/250.50/120.00/22.00，
        # 导致数据库里缺少排放量的报告被渲染成一份看似真实的诊断报告。
        if total <= 0:
            return self._insufficient_data_response(req)

        s1_pct = round((s1 / total) * 100, 1)
        s2_pct = round((s2 / total) * 100, 1)
        desulf_pct = round((desulf / total) * 100, 1)

        primary_source = (
            "化石燃料直接燃烧排放 (范围一)"
            if s1 >= s2
            else "外购电力/热力间接排放 (范围二)"
        )

        system_prompt = (
            "你是一名具备国家注册碳核查员资格的权威双碳专家，精通 GB/T 32150-2015 工业企业温室气体排放标准。"
            "请基于企业核定的排放数据，撰写专业严肃的《企业碳盘查与排放归因深度诊断报告》。"
            "语言风格要求权威、客观、具有工程指导性，禁止出现任何占位符或敷衍空话。"
        )
        user_prompt = (
            f"企业名称: {req.enterprise_name}\n"
            f"核算周期: {req.accounting_period}\n"
            f"排放总量: {total:.2f} tCO2e\n"
            f"范围一直接燃烧排放: {s1:.2f} tCO2e ({s1_pct}%)\n"
            f"范围二外购电力排放: {s2:.2f} tCO2e ({s2_pct}%)\n"
            f"脱硫过程副产排放: {desulf:.2f} tCO2e ({desulf_pct}%)\n"
            "请严格按照以下三部分输出：\n"
            "一、执行概要\n"
            "二、关键诊断发现（请列出至少3条核心要点）\n"
            "三、工艺降碳与技术优化建议（请列出至少3条具有工程可行性的减碳措施）"
        )

        llm_text, model_used = self._call_llm_if_available(system_prompt, user_prompt)

        if not llm_text:
            summary = (
                f"本期（{req.accounting_period}）{req.enterprise_name}经核定温室气体排放总量为 {total:,.2f} tCO₂e。"
                f"结构分析显示，{primary_source}为占比最高的排放源，占比达 {max(s1_pct, s2_pct)}%。"
                f"以下结论均由内置规则引擎依据上述申报数值计算得出，未经过第三方复核。"
            )
            key_findings = [
                f"主要排放源锁定：{primary_source}贡献了主要的碳负荷（共 {max(s1, s2):,.2f} 吨，占比 {max(s1_pct, s2_pct)}%）。",
                f"范围二电力清洁度：外购电力排放量为 {s2:,.2f} 吨，受区域电网平均排放因子影响，绿电消纳比例需以企业购电凭证核实。",
                f"脱硫过程转化：脱硫工序申报碳排放 {desulf:,.2f} 吨（占比 {desulf_pct}%），石灰石/碳酸盐消耗台账需由核查员另行核对。",
            ]
            reduction_suggestions = [
                "针对范围一燃烧优化：建议对锅炉燃烧系统引入风煤动态闭环优化，加强飞灰炉渣含碳量监测（节煤幅度须经能效测试确认）。",
                "针对范围二间接排放：推荐参与绿色电力交易（绿证绿电），采购屋顶分布式光伏或平价绿电抵扣外购电排放因子。",
                "数字化与资产管理：建立月度排放核算追踪台账，严控工艺指标波动，确保履约期前锁定碳配额安全边际。",
            ]
            full_text = (
                f"【企业碳盘查与排放归因诊断报告】\n\n"
                f"一、执行概要\n{summary}\n\n"
                f"二、关键诊断发现\n"
                + "\n".join(f"{i + 1}. {item}" for i, item in enumerate(key_findings))
                + "\n\n"
                "三、工艺降碳与技术优化建议\n"
                + "\n".join(
                    f"{i + 1}. {item}" for i, item in enumerate(reduction_suggestions)
                )
            )
        else:
            full_text = llm_text.strip()
            # 智能提取段落
            summary_part = ""
            findings_part: list[str] = []
            suggestions_part: list[str] = []

            lines = [
                line_text.strip()
                for line_text in full_text.split("\n")
                if line_text.strip()
            ]
            current_section = "summary"
            for line in lines:
                if "诊断发现" in line or "关键发现" in line:
                    current_section = "findings"
                    continue
                elif "建议" in line or "降碳措施" in line:
                    current_section = "suggestions"
                    continue
                elif "执行概要" in line or "概要" in line:
                    current_section = "summary"
                    continue

                if current_section == "summary":
                    if not summary_part:
                        summary_part = line
                elif current_section == "findings":
                    if line.startswith(("-", "*", "1.", "2.", "3.", "4.", "5.")):
                        clean_item = line.lstrip("-*123456789. 、")
                        if clean_item:
                            findings_part.append(clean_item)
                elif current_section == "suggestions":
                    if line.startswith(("-", "*", "1.", "2.", "3.", "4.", "5.")):
                        clean_item = line.lstrip("-*123456789. 、")
                        if clean_item:
                            suggestions_part.append(clean_item)

            summary = summary_part if summary_part else full_text[:220] + "..."
            key_findings = (
                findings_part
                if len(findings_part) >= 2
                else [
                    f"主要排放源锁定为 {primary_source}（占比 {max(s1_pct, s2_pct)}%）。",
                    f"范围一 {s1:,.2f} 吨与范围二 {s2:,.2f} 吨均已填报，可按 GB/T 32150-2015 口径复核。",
                ]
            )
            reduction_suggestions = (
                suggestions_part
                if len(suggestions_part) >= 2
                else [
                    "推进风机与水泵变频调速节能改造，压减厂用电率。",
                    "扩大分布式绿电与绿证直购比例，抵消外购电间接碳排。",
                    "强化机组燃烧工况在线调优，将飞灰含碳量控制在 2.5% 以内。",
                ]
            )

        # 核心密码学：对生成全文计算 SHA-256 存证指纹
        analysis_hash = hashlib.sha256(full_text.encode("utf-8")).hexdigest()

        return ReportAnalysisResponse(
            report_id=req.report_id,
            summary=summary,
            key_findings=key_findings,
            reduction_suggestions=reduction_suggestions,
            full_text=full_text,
            analysis_hash=analysis_hash,
            model_used=model_used,
        )

    def answer_policy_question(self, req: PolicyQARequest) -> PolicyQAResponse:
        """Answer policy and national standards questions based on knowledge retrieval."""
        q = req.question.strip()
        system_prompt = (
            "你是一名国家碳排放核算与全国碳市场政策首席顾问，熟悉 GB/T 32150-2015 通则与发电、电网行业核算指南。"
            "请用通俗易懂但专业严谨的语言回答用户的技术问题，并注明所依据的标准条款。"
        )
        llm_text, model_used = self._call_llm_if_available(system_prompt, q)

        if llm_text:
            return PolicyQAResponse(
                answer=llm_text.strip(),
                relevant_standards=[
                    "GB/T 32150-2015 工业企业温室气体排放核算和报告通则",
                    "企业温室气体排放核算方法与报告指南 发电设施",
                ],
                model_used=model_used,
            )

        # 知识库专家规则库匹配
        q_lower = q.lower()
        if "范围一" in q or "scope 1" in q_lower or "直接排放" in q:
            ans = (
                "【范围一：直接温室气体排放】指由企业直接拥有或控制的排放源所产生的排放。"
                "典型场景包括：工业锅炉燃煤燃气产生的烟气排放、窑炉直接燃烧、企业自有运输车辆汽柴油消耗，以及脱硫工序中石灰石反应分解释放的 CO₂。"
                "在 GB/T 32150 中，其计算公式为：燃料消耗量 × 低位发热量 × 单位热值含碳量 × 碳氧化率 × (44/12)。"
            )
            standards = ["GB/T 32150-2015 第 4.1 条", "ISO 14064-1"]
        elif "范围二" in q or "scope 2" in q_lower or "外购" in q or "电网" in q:
            ans = (
                "【范围二：能源间接排放】指企业消耗由外部供应的电力、蒸汽、热力或冷气所导致的间接碳排放。"
                "虽然直接排烟发生在发电厂，但由于是本企业用电驱动，按照污染者付费原则需记入本企业范围二。"
                "计算公式为：净外购电量 (MWh) × 区域电网平均排放因子 (如全国电网平均因子 0.5810 tCO₂/MWh)。"
            )
            standards = [
                "GB/T 32150-2015 第 4.2 条",
                "生态环境部《关于做好2023-2025年发电行业配额预分配工作的通知》",
            ]
        elif "氧化率" in q or "of" in q_lower:
            ans = (
                "【碳氧化率 (OF, Oxidation Factor)】表示燃料中碳被完全燃烧氧化成 CO₂ 的质量比率（范围 0~1）。"
                "国标缺省值通常取 0.98~0.99。若企业具备灰渣含碳量检测条件，可依据灰渣产率与含碳量实测反推，能更真实反映机组热效率，甚至可核减部分虚高碳排放。"
            )
            standards = ["GB/T 32150-2015 附录 A", "DL/T 567 燃煤电厂燃料试验方法"]
        elif "脱硫" in q or "石灰石" in q:
            ans = (
                "【脱硫过程排放】燃煤电厂采用石灰石-石膏湿法脱硫时，吸收剂 CaCO₃ 与烟气中 SO₂ 反应会分解产生副产物 CO₂。"
                "公式为：CaCO₃ 消耗量 × 纯度 × 转化率 × (44/100)。虽然排放量占比一般为 1%~3%，但在百万千瓦级大型机组中绝对吨数不可忽视。"
            )
            standards = ["GB/T 32150-2015 公式 (6)"]
        else:
            ans = (
                f"针对您关心的「{q}」：根据国家标准《GB/T 32150 工业企业温室气体排放核算和报告通则》，"
                "企业进行碳核算时须遵循「完整性、准确性、一致性、透明度」四大原则。"
                "建议优先核实企业边界（厂区红线内固定设施）、燃料入厂与入炉计量化验台账，并保留至少 5 年原始凭单备查。"
            )
            standards = ["GB/T 32150-2015 工业企业温室气体排放核算和报告通则"]

        return PolicyQAResponse(
            answer=ans, relevant_standards=standards, model_used=model_used
        )

    def get_procurement_advice(
        self, req: ProcurementAdviceRequest
    ) -> ProcurementAdviceResponse:
        """Analyze compliance shortfall and provide actionable carbon procurement advice."""
        shortfall = round(req.current_emission - req.current_quota, 2)
        price = req.current_market_price

        if shortfall > 5000:
            risk = "HIGH"
            cost = round(shortfall * price, 2)
            advice = (
                f"【履约高风险警报】当前企业碳排放量 ({req.current_emission:,.2f}t) 超出可用配额 ({req.current_quota:,.2f}t) 达 {shortfall:,.2f} 吨。"
                f"按当前市价 {price:.2f} 元/吨测算，存在约 {cost:,.2f} 元的硬性采购资金缺口。"
                "建议立即启动分期采购，避开履约期临近时的全网集中抢购与价格脉冲峰值。"
            )
            actions = [
                f"第一步：在双向拍卖市场以限价单分批挂买（建议出价 {price * 0.98:.2f} ~ {price * 1.01:.2f} 元），先锁定 40% 缺口（约 {shortfall * 0.4:.0f} 吨）。",
                "第二步：联系供应链上下游企业，通过系统 P2P 协议交易询价大宗碳配额，争取 2%~4% 的大宗协议折扣。",
                "第三步：核查企业名下是否有可申报核证自愿减排量 (CCER) 的绿电或光伏项目，按政策上限抵销 5% 履约额。",
            ]
        elif shortfall > 0:
            risk = "MEDIUM"
            cost = round(shortfall * price, 2)
            advice = (
                f"【履约适度敞口】企业目前存在 {shortfall:,.2f} 吨的配额缺口，预估平仓成本约为 {cost:,.2f} 元。"
                "缺口规模可控，建议结合未来 7 天 AI 碳价预测通道，在价格下探至支撑位时逢低吸筹补齐。"
            )
            actions = [
                f"观察 AI 市场预测价格通道，在预测价格位于下轨时挂买入单（建议目标价 {price * 0.97:.2f} 元）。",
                "排查本月下半程机组排产计划，适度增加高热值低硫煤使用比重以收窄排放增量。",
            ]
        else:
            risk = "ZERO"
            surplus = abs(shortfall)
            earned = round(surplus * price, 2)
            advice = (
                f"【配额资产盈余】企业当前配额充足，核算后盈余配额达 {surplus:,.2f} 吨。"
                f"若全量挂牌变现，按当前市价预计可实现碳资产收益约 {earned:,.2f} 元，亦可结转至下一履约周期储备。"
            )
            actions = [
                f"策略 A（变现增收）：在双向拍卖市场挂出部分盈余配额（如挂牌 {surplus * 0.5:.0f} 吨），价格可设定在 AI 预测上轨以获取溢价。",
                "策略 B（结转避险）：将核心盈余配额保留在账户中作为安全缓冲，以应对下个生产年度基准线收紧的风险。",
            ]

        return ProcurementAdviceResponse(
            shortfall_tons=shortfall,
            risk_level=risk,
            estimated_cost=max(0.0, shortfall * price),
            advice=advice,
            action_items=actions,
            model_used="DualCarbon-Optimizer-v1.0",
        )
