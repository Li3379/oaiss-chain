-- V17__add_trade_audit.sql
-- 成交审核（Trade Audit）
--
-- 背景：撮合成交原本在单事务内一口气从「创建」走到「COMPLETED + 额度转移 +
-- 碳币结算 + 发事件上链」，没有任何人工裁决空间。审核功能把「结算」从
-- 「成交」里拆出来：待审成交持有冻结资产，通过才结算并上链，驳回原路退回。
--
-- 设计取舍：审核态用 transaction.audit_status 独立字段，不并入 TradeStatusEnum。
-- 两者语义正交（status=生命周期，audit_status=是否经过人工裁决），
-- 而给状态枚举加新码会连带前端状态渲染、i18n、以及全部依赖状态码的既有断言。

-- 1. 交易主表增加审核态与意见摘要（高频查询、需要索引，故在主表）
ALTER TABLE `transaction`
    ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 0
        COMMENT '成交审核态：0-无需审核 1-待审核 2-已通过 3-已驳回',
    ADD COLUMN `audit_remark` VARCHAR(500) DEFAULT NULL COMMENT '审核意见摘要（完整意见见 trade_audit）';

CREATE INDEX `idx_transaction_audit_status` ON `transaction` (`audit_status`);

-- 2. 审核记录表（低频大字段：风险理由、完整意见、审核人、裁决时间）
CREATE TABLE `trade_audit` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trade_id`         BIGINT       NOT NULL COMMENT '关联交易ID',
    `audit_status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '审核态：0-无需 1-待审 2-通过 3-驳回',
    `risk_reasons`     TEXT         DEFAULT NULL COMMENT '命中的风险理由（JSON 数组字符串）',
    `decision_reason`  VARCHAR(32)  DEFAULT NULL COMMENT '裁决主理由：LARGE_AMOUNT/PRICE_DEVIATION/LOW_CREDIT/WASH_TRADING/OTHER',
    `audit_remark`     TEXT         DEFAULT NULL COMMENT '审核意见',
    `auditor_id`       BIGINT       DEFAULT NULL COMMENT '审核人用户ID',
    `audited_at`       DATETIME     DEFAULT NULL COMMENT '裁决时间',
    `fallback_decided` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否由管理员兜底裁决',
    `deleted`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除标记',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_trade_audit_trade` (`trade_id`),
    KEY `idx_trade_audit_status` (`audit_status`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='成交审核记录表';

-- 3. 碳币流水新增「审核驳回冲正」类型
--    与正常买入/卖出区分，使监管能识别「因审核驳回而回滚」而非新交易
ALTER TABLE `carbon_coin_transaction`
    MODIFY COLUMN `tx_type` TINYINT NOT NULL COMMENT '交易类型：1-充值 2-购买配额 3-出售配额 4-历史转账 5-审核驳回冲正';