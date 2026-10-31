-- V16__add_quota_transaction.sql
-- 碳配额变动流水账本 (Quota Ledger)
--
-- 背景：配额变动此前只内联在交易结算路径里，直接在 enterprise 的
-- carbon_quota / carbon_used / carbon_tradable / carbon_frozen 四字段上就地加减，
-- 事后无法回答「这笔额度是被哪一笔交易、哪一次冻结动的」。
-- 成交审核的裁决依据、额度周期上限的累计、监管的逐笔穿透归因，均依赖本表。
--
-- 口径：只记增量与前后值，不存余额快照——enterprise 仍是余额唯一真相源。

CREATE TABLE `quota_transaction` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tx_no`           VARCHAR(50)  NOT NULL COMMENT '流水编号',
    `enterprise_id`   BIGINT       NOT NULL COMMENT '企业ID',
    `change_type`     VARCHAR(20)  NOT NULL COMMENT '变动类型：FREEZE/UNFREEZE/TRANSFER/INCREASE/ROLLBACK',
    `amount`          DECIMAL(15, 4) NOT NULL COMMENT '变动数量（正数，方向由 change_type 决定）',
    `tradable_before` DECIMAL(15, 4) NOT NULL COMMENT '变动前可交易余额',
    `tradable_after`  DECIMAL(15, 4) NOT NULL COMMENT '变动后可交易余额',
    `frozen_before`   DECIMAL(15, 4) NOT NULL COMMENT '变动前冻结余额',
    `frozen_after`    DECIMAL(15, 4) NOT NULL COMMENT '变动后冻结余额',
    `source`          VARCHAR(20)  DEFAULT NULL COMMENT '业务来源：AUCTION/P2P/AUDIT/MANUAL',
    `related_trade_id` BIGINT      DEFAULT NULL COMMENT '关联交易ID',
    `operator_id`     BIGINT       DEFAULT NULL COMMENT '操作人用户ID',
    `remark`          VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `deleted`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除标记',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quota_tx_no` (`tx_no`),
    KEY `idx_quota_enterprise` (`enterprise_id`, `created_at`),
    KEY `idx_quota_trade` (`related_trade_id`),
    KEY `idx_quota_type` (`change_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='碳配额变动流水表';