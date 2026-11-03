-- V29__add_shortfall_offset.sql
-- 超排缺口抵扣记录（碳信用抵消）
--
-- 背景：审核通过时若实排 > 配额，报告上会留下一个缺口
--   （quotaAllocatedAmount − scope1Emission 为负）。
-- 企业可以用碳中和项目的碳信用抵扣这个缺口——抵扣后缺口归零，
-- 但**必须记录抵扣了多少**，否则无法判断缺口是否真的被补上。
--
-- 为什么记在报告上而不是新建表：
-- 缺口本身就是报告的一个属性（配额 − 实排），抵扣也是针对同一份报告的，
-- 放同一行比拉一张关联表更直白，也让「这份报告还差多少」一次查询就能得到。
--
-- 单子句 ALTER：H2 2.2.224（MODE=MySQL）不支持多子句。

ALTER TABLE `carbon_report` ADD COLUMN `shortfall_offset` DECIMAL(15,4) NOT NULL DEFAULT 0
    COMMENT '已用碳信用抵扣的超排缺口（吨）；缺口 = scope1 − quotaAllocatedAmount + shortfall_offset';
