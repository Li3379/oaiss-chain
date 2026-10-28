-- V12__add_escrow_fields.sql
-- 交易履约与资产冻结闭环 (Trade & Escrow)

-- 1. 企业表增加冻结碳配额字段 (卖单挂单时锁定)
ALTER TABLE `enterprise`
    ADD COLUMN `carbon_frozen` DECIMAL(15, 4) NOT NULL DEFAULT 0.0000 COMMENT '冻结碳配额（卖单挂单中）';

-- 2. 碳币账户增加冻结资金字段 (买单挂单时锁定)
ALTER TABLE `carbon_coin_account`
    ADD COLUMN `frozen_balance` DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT '冻结碳币金额（买单挂单中）';

-- 3. 拍卖挂单表增加冻结资产记录字段
ALTER TABLE `auction_order`
    ADD COLUMN `frozen_amount` DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT '挂单冻结资产额（买单为冻结碳币，卖单为冻结配额）';
