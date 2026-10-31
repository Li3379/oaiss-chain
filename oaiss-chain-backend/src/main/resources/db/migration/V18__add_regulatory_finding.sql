-- V18__add_regulatory_finding.sql
-- 监管线索（Regulatory Finding）
--
-- 背景：原始需求要求监管「进行数据的定期核查」，但系统里既没有核查任务、
-- 也没有核查记录——监管只有两只能看的表，没有任何"发现异常"的系统能力。
--
-- 设计约束：
--   * 只产出、不处置：线索不改变任何业务状态，处置走独立监督意见，
--     以守住「监管不干预市场」的定位
--   * 幂等：同企业同规则若已有未处置线索则不再产出，
--     否则定时扫描会让清单被自身重复项淹没

CREATE TABLE `regulatory_finding` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `enterprise_id` BIGINT      NOT NULL COMMENT '企业ID',
    `rule_code`     VARCHAR(32) NOT NULL
        COMMENT '规则代码：REPEATED_REJECTION/QUOTA_OVERDRAFT/CREDIT_PLUNGE/WASH_TRADING_BLOCKED',
    `severity`      VARCHAR(16) NOT NULL COMMENT '严重等级：LOW/MEDIUM/HIGH/CRITICAL',
    `evidence`      TEXT        DEFAULT NULL COMMENT '触发依据（含实际数值，供监管判断）',
    `resolved`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已处置',
    `resolved_by`   BIGINT      DEFAULT NULL COMMENT '处置人用户ID',
    `resolved_at`   DATETIME    DEFAULT NULL COMMENT '处置时间',
    `resolve_note`  VARCHAR(500) DEFAULT NULL COMMENT '处置说明',
    `deleted`       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '软删除标记',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_finding_enterprise` (`enterprise_id`, `resolved`),
    KEY `idx_finding_unresolved` (`resolved`, `severity`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='监管线索表';