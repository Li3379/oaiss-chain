-- V28__carbon_coin_issuance.sql
-- 碳币闭环：审核通过按盈余发放碳币，替代原来的配额发放
--
-- 背景：2026-11-03 裁决确认碳币模型为——
--   配额 = 产量 × 基准值（只计算，不发给企业）
--   盈余 = 配额 − 实排
--   盈余 > 0 → 发放 N 枚碳币（1:1）
--   碳币可兑换配额、可 P2P 转让，不可提现
--
-- 本迁移只做加列与索引，不动任何业务数据。
-- 存量配额余额的清理由运维脚本 scripts/db/reset-carbon-asset.sql 单独执行，
-- 不进迁移链——避免不可逆的数据删除混在自动迁移里。
--
-- ⚠️ 每条 ALTER 必须单子句：H2 2.2.224（MODE=MySQL）不支持
--    「ALTER TABLE t ADD COLUMN a, ADD COLUMN b」，单子句才通过。

-- ============ 1. 碳币账户：发行累计 ============

-- 记录历史发行总量。注意：回收（CLAWBACK）**不减少**此字段——
-- 发行是史实，回收改变的是流通量。因此守恒断言必须分开：
--   净流通 = Σ ISSUE − Σ CLAWBACK == Σ balance
--   发行累计 = Σ ISSUE == Σ total_issued
-- 若把两者写成同一个等式，测试必然红。
ALTER TABLE `carbon_coin_account` ADD COLUMN `total_issued` DECIMAL(15,2) NOT NULL DEFAULT 0
    COMMENT '历史累计发放量（回收不减少）';

-- ============ 2. 碳币流水：报告维度 + 发放序号 ============

-- 关联碳报告。发放行（ISSUE）填报告ID，回收行（CLAWBACK）也填，
-- 便于「这份报告发了多少币」可追溯。
ALTER TABLE `carbon_coin_transaction` ADD COLUMN `related_report_id` BIGINT NULL
    COMMENT '关联碳报告ID（发放/回收）';

-- 发放序号。用 reportId#seq 而非裸 reportId 做幂等键：
-- 报告被驳回后可修改重提并再次通过（ReportStatusEnum.REJECTED.isSubmittable()==true），
-- 裸 reportId 会让第二次发放撞唯一索引，新盈余被静默丢弃。
-- 回收行的 related_seq 留 NULL（MySQL 唯一索引不约束 NULL，可多行共存）。
ALTER TABLE `carbon_coin_transaction` ADD COLUMN `related_seq` INT NULL
    COMMENT '该报告的发放序号，从1开始；回收行为 NULL';

CREATE INDEX `idx_coin_report` ON `carbon_coin_transaction` (`related_report_id`);

CREATE UNIQUE INDEX `uk_coin_issue` ON `carbon_coin_transaction` (`related_report_id`, `related_seq`);
