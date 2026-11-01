-- V23__update_unit_price_comment.sql
-- 单价口径注释统一：法币计价 → 碳币计价
--
-- 背景：transaction.unit_price 的代码注释长期写作「单价（法币/吨）」，但：
--   1) 余额是碳币账户，成交扣的也是碳币（CarbonCoinService 的 balance）
--   2) 系统内没有任何「法币 ↔ 碳币」汇率定义（全仓 grep 无命中），实际是隐式 1:1
--   3) 原始需求写的是「碳币价格」与「平台限价」，从未出现法币计价
-- 因此法币计价是实现的产物而非需求约定，属误导性文案。
--
-- 口径分界：2026-11-01 之前录入的成交按旧口径历史数据处理，不参与新限价；
-- 该分界由 oaiss.trade.price-limit.effective-from 在运行期判定，
-- 而不是靠本注释——注释只是给 DBA 与排查者的说明。
--
-- 注意：MySQL 的 MODIFY COLUMN 要求重述完整列定义。
-- 此处逐字复制 V1__init_schema.sql:187 的 `DECIMAL(15,2) NOT NULL`，
-- 漏写 NOT NULL 会静默把约束放宽为可空。

ALTER TABLE `transaction`
    MODIFY COLUMN `unit_price` DECIMAL(15,2) NOT NULL
    COMMENT '单价（碳币/吨；2026-11-01 前的历史记录为旧口径，见 DEV-GUIDE 口径分界说明）';