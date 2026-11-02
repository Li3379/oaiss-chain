-- V26__seed_power_benchmarks.sql
-- 发电行业碳排放基准值种子（2024 / 2025 / 2026）
--
-- 数据来源（三源交叉验证，逐位一致）：
--   1) 生态环境部《全国碳排放权交易市场2025、2026年度发电行业以及
--      2026年度钢铁、水泥、铝冶炼行业配额总量和分配方案》表 1
--      mee.gov.cn/xxgk2018/xxgk/xxgk03/202609/W020260903551498752549.pdf
--   2) 生态环境部《2023、2024年度全国碳排放权交易发电行业配额总量和分配方案》附 2
--      gov.cn/zhengce/zhengceku/202410/P020241022275017526919.pdf
--   3) 交叉验证：ICAP 整理表 + IGES《Progress of China's national ETS》
--      （含 2019-2024 完整序列）
--
-- 单位：tCO2/MWh（发电基准值）
--
-- 四类机组判定（官方原文）：
--   I   300MW 等级以上常规燃煤机组
--   II  300MW 等级及以下常规燃煤机组
--   III 燃煤矸石、煤泥、水煤浆等非常规燃煤机组（含燃煤循环流化床机组）
--   IV  燃气机组
--
-- 注：供热基准值（tCO2/GJ）本轮不入库——项目无供热量字段、无消费方。
--     官方 2026 值为燃煤 0.1021 / 燃气 0.0533。

INSERT INTO `industry_benchmark`
    (`created_at`, `updated_at`, `is_deleted`, `industry_code`, `unit_type`,
     `benchmark_year`, `benchmark_kind`, `benchmark_value`, `benchmark_unit`)
VALUES
    -- 2024 年度
    (NOW(), NOW(), 0, 'POWER', 'I',   2024, 'GENERATION_BENCHMARK', 0.7910, 'tCO2/MWh'),
    (NOW(), NOW(), 0, 'POWER', 'II',  2024, 'GENERATION_BENCHMARK', 0.8049, 'tCO2/MWh'),
    (NOW(), NOW(), 0, 'POWER', 'III', 2024, 'GENERATION_BENCHMARK', 0.8244, 'tCO2/MWh'),
    (NOW(), NOW(), 0, 'POWER', 'IV',  2024, 'GENERATION_BENCHMARK', 0.3288, 'tCO2/MWh'),
    -- 2025 年度
    (NOW(), NOW(), 0, 'POWER', 'I',   2025, 'GENERATION_BENCHMARK', 0.7902, 'tCO2/MWh'),
    (NOW(), NOW(), 0, 'POWER', 'II',  2025, 'GENERATION_BENCHMARK', 0.8017, 'tCO2/MWh'),
    (NOW(), NOW(), 0, 'POWER', 'III', 2025, 'GENERATION_BENCHMARK', 0.8162, 'tCO2/MWh'),
    (NOW(), NOW(), 0, 'POWER', 'IV',  2025, 'GENERATION_BENCHMARK', 0.3288, 'tCO2/MWh'),
    -- 2026 年度
    (NOW(), NOW(), 0, 'POWER', 'I',   2026, 'GENERATION_BENCHMARK', 0.7894, 'tCO2/MWh'),
    (NOW(), NOW(), 0, 'POWER', 'II',  2026, 'GENERATION_BENCHMARK', 0.7985, 'tCO2/MWh'),
    (NOW(), NOW(), 0, 'POWER', 'III', 2026, 'GENERATION_BENCHMARK', 0.8080, 'tCO2/MWh'),
    (NOW(), NOW(), 0, 'POWER', 'IV',  2026, 'GENERATION_BENCHMARK', 0.3288, 'tCO2/MWh');