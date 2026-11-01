-- V20__add_chain_mark.sql
-- 链上存证标记（Chain Mark）
--
-- 背景：Mock profile 时期的上链实现只返回一个本地生成的哈希字符串（tx_mock_<时钟>_<uuid>），
-- 从不写入账本，却把该哈希当作"已上链"写进了 carbon_report / transaction。
-- 切到真实 Fabric 后，对账立刻把这些记录全部判为 MISSING_ON_CHAIN——
-- 判断本身是对的（链上确实没有），但它们不是"新出现的问题"，
-- 而是换环境前留下的历史欠账。如果和真实异常混在同一张清单里，
-- 每次对账都会产生一片永远不会消失的红色噪音，真实问题反而被淹掉。
--
-- 本迁移只加"标记"能力，不改任何既有事实：
--   * blockchain_tx_hash 原样保留（这是历史真相，抹掉就没有审计线索了）
--   * status / on_chain_at 不动（标记是加注解，不是改业务状态）
--
-- chain_mark 取值：
--   NULL             未标记（默认，绝大多数记录）
--   HISTORICAL_MOCK  历史 Mock 时钟哈希，链上不存在，已确认、不再告警

ALTER TABLE `carbon_report`
    ADD COLUMN `chain_mark`        VARCHAR(32)  DEFAULT NULL
        COMMENT '链上存证标记：NULL-正常 HISTORICAL_MOCK-历史Mock假哈希（链上不存在，已确认）',
    ADD COLUMN `chain_mark_reason` VARCHAR(200) DEFAULT NULL COMMENT '标记原因（供审计与复核）',
    ADD COLUMN `chain_marked_at`   DATETIME     DEFAULT NULL COMMENT '标记时间',
    ADD COLUMN `chain_marked_by`   BIGINT       DEFAULT NULL COMMENT '标记操作人用户ID';

CREATE INDEX `idx_carbon_report_chain_mark` ON `carbon_report` (`chain_mark`);

ALTER TABLE `transaction`
    ADD COLUMN `chain_mark`        VARCHAR(32)  DEFAULT NULL
        COMMENT '链上存证标记：NULL-正常 HISTORICAL_MOCK-历史Mock假哈希（链上不存在，已确认）',
    ADD COLUMN `chain_mark_reason` VARCHAR(200) DEFAULT NULL COMMENT '标记原因（供审计与复核）',
    ADD COLUMN `chain_marked_at`   DATETIME     DEFAULT NULL COMMENT '标记时间',
    ADD COLUMN `chain_marked_by`   BIGINT       DEFAULT NULL COMMENT '标记操作人用户ID';

CREATE INDEX `idx_transaction_chain_mark` ON `transaction` (`chain_mark`);
