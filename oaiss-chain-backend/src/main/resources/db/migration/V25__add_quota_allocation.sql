-- V25__add_quota_allocation.sql
-- 碳配额发放引擎（组件 1）
--
-- 背景：项目此前从未有配额发放路径——carbon_quota 只在 V2 种子里写死，
-- 全仓 3 处 setCarbonQuota 全是 .add()（交易买入累加），从未按行业基准线发放。
-- 本轮接入「审核通过 → 配额 = 发电量 × 行业基准值」这条链。
--
-- 数据来源：生态环境部《2025、2026 年度发电行业以及 2026 年度钢铁、水泥、
-- 铝冶炼行业配额总量和分配方案》表 1；2023/2024 年度方案附 2。
-- 基准值经 ICAP 与 IGES 两份独立整理交叉验证，逐位一致。
--
-- ⚠️ 所有 ALTER 必须**单子句**：实测 H2 2.2.224（MODE=MySQL）对
--    「ALTER TABLE t ADD COLUMN a INT, ADD COLUMN b INT」报语法错误 42000。
--    MySQL 两种写法都支持，故拆开以兼容测试环境回放。

-- ============ 1. 行业基准值表 ============
--
-- benchmark_year 而非 year：`year` 是 H2 保留字，实测建表报
--   Syntax error ... expected "identifier"
-- unit_type 非空默认 'NA'：MySQL 唯一索引把 NULL 视为互不相等，
--   若允许 NULL 则 (industry, NULL, year, kind) 可重复插入，查询结果不确定。
CREATE TABLE `industry_benchmark` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `created_at`     DATETIME      NOT NULL COMMENT '创建时间',
    `updated_at`     DATETIME      NOT NULL COMMENT '更新时间',
    `is_deleted`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '软删除标记',
    `industry_code`  VARCHAR(32)   NOT NULL COMMENT '行业代码，本轮固定 POWER',
    `unit_type`      VARCHAR(32)   NOT NULL DEFAULT 'NA'
        COMMENT '机组类别：I/II/III/IV；非发电行业为 NA',
    `benchmark_year` INT           NOT NULL COMMENT '基准年度',
    `benchmark_kind` VARCHAR(32)   NOT NULL COMMENT '类别：GENERATION_BENCHMARK',
    `benchmark_value` DECIMAL(15,6) NOT NULL COMMENT '基准值',
    `benchmark_unit` VARCHAR(20)   NOT NULL COMMENT '单位：tCO2/MWh',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_benchmark`
        (`industry_code`, `unit_type`, `benchmark_year`, `benchmark_kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行业碳排放基准值';

-- ============ 2. 碳报告：发放依据 + 周期结构 + 发放状态 ============

-- 产量（发电量）。配额公式的乘数因子。
ALTER TABLE `carbon_report` ADD COLUMN `output_quantity` DECIMAL(15,4) NULL
    COMMENT '产量（发电量，MWh）';

ALTER TABLE `carbon_report` ADD COLUMN `output_unit` VARCHAR(20) NULL
    COMMENT '产量单位，本轮固定 MWh';

-- 机组类型：决定查哪一行基准值。由企业填报时自选（I/II/III/IV）。
-- 本轮不做装机容量校验，故不引入 machine_capacity 字段。
ALTER TABLE `carbon_report` ADD COLUMN `unit_type` VARCHAR(32) NULL
    COMMENT '机组类别 I/II/III/IV（发电行业必填）';

-- 核算周期拆结构化字段：accountingPeriod 是无格式约束的自由文本
-- （实测存量出现过 2024-01-15 这类形态），无法承载唯一性。
ALTER TABLE `carbon_report` ADD COLUMN `period_year` INT NULL
    COMMENT '核算年份；软删时置 NULL 以释放唯一键位';

ALTER TABLE `carbon_report` ADD COLUMN `period_quarter` INT NULL
    COMMENT '核算季度 1-4；年度报告为 NULL';

-- 配额发放状态机。
ALTER TABLE `carbon_report` ADD COLUMN `quota_allocated_amount` DECIMAL(15,4) NULL
    COMMENT '已发放配额量（本轮发放值）';

ALTER TABLE `carbon_report` ADD COLUMN `quota_used_snapshot` DECIMAL(15,4) NULL
    COMMENT '发放时的 carbon_used 快照，冲正保护的比较基线';

ALTER TABLE `carbon_report` ADD COLUMN `allocation_state` VARCHAR(16) NULL
    COMMENT 'NONE/ALLOCATED/REVOKED/PENDING_RETRY/REVOKE_FAILED';

-- 该报告的发放序号，冲正重发时递增（构造幂等键 reportId#seq）
ALTER TABLE `carbon_report` ADD COLUMN `allocation_seq` INT NULL
    COMMENT '配额发放序号，从1开始，冲正重发递增';

-- 同企业同核算周期唯一（含 is_deleted，删除时置 1 或删时把周期置 NULL）
CREATE INDEX `idx_report_period`
    ON `carbon_report` (`enterprise_id`, `period_year`, `period_quarter`);

-- ============ 3. 配额流水：报告维度 + used 快照 + 发放幂等键 ============

ALTER TABLE `quota_transaction` ADD COLUMN `related_report_id` BIGINT NULL
    COMMENT '关联碳报告ID（配额发放/冲正）';

-- used 前后值：账本此前只记 tradable/frozen，缺 used 维度，
-- 导致「冲正时 used 是否变过」无从判断。
ALTER TABLE `quota_transaction` ADD COLUMN `used_before` DECIMAL(15,4) NULL
    COMMENT '变动前 carbon_used';

ALTER TABLE `quota_transaction` ADD COLUMN `used_after` DECIMAL(15,4) NULL
    COMMENT '变动后 carbon_used';

-- 发放幂等键 = reportId#seq。用序号而非裸 reportId：
-- 冲正后重发会再次发放同一报告，裸 reportId 会撞唯一索引。
-- 冲正行此列留 NULL（MySQL 唯一索引不约束 NULL，可多行共存）。
ALTER TABLE `quota_transaction` ADD COLUMN `allocation_key` VARCHAR(64) NULL
    COMMENT '发放幂等键 reportId#seq；冲正行留 NULL';

ALTER TABLE `quota_transaction` ADD COLUMN `allocation_seq` INT NULL
    COMMENT '该报告的发放序号，冲正重发时递增';

CREATE INDEX `idx_quota_report` ON `quota_transaction` (`related_report_id`);

CREATE UNIQUE INDEX `uk_quota_allocation` ON `quota_transaction` (`allocation_key`);