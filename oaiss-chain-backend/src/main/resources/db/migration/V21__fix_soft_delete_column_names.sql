-- V21__fix_soft_delete_column_names.sql
-- 统一软删除列名为 is_deleted
--
-- 背景：QuotaTransaction / TradeAudit / RegulatoryFinding 三个实体都继承
-- BaseEntity，而 BaseEntity 把软删除标记映射为 `is_deleted`（全仓其余
-- 20+ 张表也都是 is_deleted）。但这三张表在各自的建表迁移里写成了 `deleted`。
--
-- 后果：Hibernate 在 ddl-auto=validate（生产与 CI 的 docker profile）下校验失败，
-- 应用直接起不来：
--   Schema-validation: missing column [is_deleted] in table [quota_transaction]
--
-- 本地开发跑 ddl-auto=none 不校验，所以这个缺陷从合并起就一直在，
-- 直到 CI 第一次真正启动后端才炸出来。
--
-- 修复方式：重命名而不是加新列——语义上是同一个字段，
-- 加列会留下两个互相矛盾的软删除标记。
--
-- 幂等保护：若列已叫 is_deleted，则跳过该表。

-- 1. quota_transaction（V16 建表）
SET @c1 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quota_transaction'
             AND COLUMN_NAME = 'deleted');
SET @s1 = IF(@c1 > 0,
    'ALTER TABLE `quota_transaction` CHANGE COLUMN `deleted` `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''软删除标记''',
    'SELECT 1');
PREPARE stmt1 FROM @s1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

-- 2. trade_audit（V17 建表）
SET @c2 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_audit'
             AND COLUMN_NAME = 'deleted');
SET @s2 = IF(@c2 > 0,
    'ALTER TABLE `trade_audit` CHANGE COLUMN `deleted` `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''软删除标记''',
    'SELECT 1');
PREPARE stmt2 FROM @s2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- 3. regulatory_finding（V18 建表）
SET @c3 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'regulatory_finding'
             AND COLUMN_NAME = 'deleted');
SET @s3 = IF(@c3 > 0,
    'ALTER TABLE `regulatory_finding` CHANGE COLUMN `deleted` `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''软删除标记''',
    'SELECT 1');
PREPARE stmt3 FROM @s3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;