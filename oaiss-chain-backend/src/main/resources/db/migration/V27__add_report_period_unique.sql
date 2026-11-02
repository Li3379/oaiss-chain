-- V27__add_report_period_unique.sql
-- 同企业同核算周期唯一（组件 1 补漏）
--
-- 背景：V25 建的是普通索引 `idx_report_period`，而实体注释、计划 AC-18
-- 都按「唯一键 (enterprise_id, period_year, period_quarter)」描述。
-- 普通索引不提供任何约束——同一企业可以在同一季度提交任意多份报告，
-- 每份都能审核通过并各自领一份配额，等于配额可以靠重复填报无限增发。
--
-- 为什么本轮才补：V25 写错了，而且这个错误不会被任何单测发现——
-- 测试跑的是 Hibernate 生成的 schema（create-drop），根本执行不到这段 DDL。
--
-- 唯一键包含 is_deleted：
--   删除一份报告时 is_deleted 0→1，键值随之变化，位置自然释放；
--   若不加 is_deleted，删掉一份报告后就再也建不了同周期的第二份。
-- MySQL 唯一索引不约束 NULL，故 period_year/period_quarter 为空的历史报告
-- （V25 之前的所有报告）不会互相冲突。

-- 幂等保护：与 V24 同一手法。若目标环境已手工加过同名唯一键，
-- 直接 ALTER 会报 Duplicate key name，Flyway 失败、应用起不来。
SET @uk_period_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                         WHERE TABLE_SCHEMA = DATABASE()
                           AND TABLE_NAME = 'carbon_report'
                           AND INDEX_NAME = 'uk_report_period');
SET @uk_period_ddl = IF(@uk_period_exists > 0,
    'SELECT 1',
    'ALTER TABLE `carbon_report` ADD UNIQUE KEY `uk_report_period`
         (`enterprise_id`, `period_year`, `period_quarter`, `is_deleted`)');
PREPARE uk_period_stmt FROM @uk_period_ddl;
EXECUTE uk_period_stmt;
DEALLOCATE PREPARE uk_period_stmt;

-- 旧的普通索引保留：它是唯一键的最左前缀，但唯一键已覆盖同样的检索需求，
-- 保留会多一份写放大。这里显式删除，避免同一组列上挂两个索引。
SET @idx_period_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                          WHERE TABLE_SCHEMA = DATABASE()
                            AND TABLE_NAME = 'carbon_report'
                            AND INDEX_NAME = 'idx_report_period');
SET @idx_period_ddl = IF(@idx_period_exists > 0,
    'ALTER TABLE `carbon_report` DROP INDEX `idx_report_period`',
    'SELECT 1');
PREPARE idx_period_stmt FROM @idx_period_ddl;
EXECUTE idx_period_stmt;
DEALLOCATE PREPARE idx_period_stmt;
