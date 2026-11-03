-- R-03 下市处罚：delisted 独立字段，与信誉阈值自动启停解耦（不被 checkThresholds 覆盖）
-- raw 03 L1098「删除用户=暂时退出碳交易系统」；权限仅 ADMIN（L913 + role-matrix 定稿）
--
-- 幂等保护（2026-11-02）：早期分支曾以 V22 文件名落地同名 ALTER，仓库这一侧删掉了
-- 该文件、迁移记录却留在库里；升级到本迁移时列已存在，MySQL 报
-- "Duplicate column name 'delisted'"，Flyway 失败、应用起不来。
-- 与 V21 同一手法：先查 information_schema，列已存在则跳过 DDL。

SET @delisted_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'credit_score'
                          AND COLUMN_NAME = 'delisted');
SET @delisted_ddl = IF(@delisted_exists > 0,
    'SELECT 1',
    'ALTER TABLE credit_score ADD COLUMN delisted TINYINT(1) NOT NULL DEFAULT 0');
PREPARE delisted_stmt FROM @delisted_ddl;
EXECUTE delisted_stmt;
DEALLOCATE PREPARE delisted_stmt;
