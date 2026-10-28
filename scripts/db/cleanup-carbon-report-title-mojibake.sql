-- 清洗 carbon_report.title 的 UTF-8 字节被按 latin1 解读产生的乱码（如 "[æµ‹è¯•æ ·æœ¬] xxx" → "[测试样本] xxx"）
-- 背景：早期脚本以 GBK/latin1 通道写入中文标题，导致 6+ 页面真实渲染乱码（登记：.planning/debug/eli5-ux-deep-2026-09-10.md UX-120）
-- 用法：mysql --default-character-set=utf8mb4 -u<user> -p<pass> -D oaiss_chain < scripts/cleanup-carbon-report-title-mojibake.sql
-- 安全：先建备份表；WHERE 仅命中含 latin1 高位字符（ä å æ ç è é）的乱码行，正常中文/英文标题不受影响；可重复执行（幂等）。

-- 1) 盘点（执行前先看命中行数，应为「异常行」而非全表）
SELECT COUNT(*) AS mojibake_candidates
FROM carbon_report
WHERE title REGEXP '[äåæçèé]';

SELECT COUNT(*) AS total_rows FROM carbon_report;

-- 2) 备份命中行（重复执行会先删除旧备份表）
DROP TABLE IF EXISTS carbon_report_title_bak_20260910;
CREATE TABLE carbon_report_title_bak_20260910 AS
SELECT id, title AS title_before, NOW() AS backed_up_at
FROM carbon_report
WHERE title REGEXP '[äåæçèé]';

-- 3) 定向还原：latin1 字节 → utf8mb4
UPDATE carbon_report
SET title = CONVERT(CAST(CONVERT(title USING latin1) AS BINARY) USING utf8mb4)
WHERE title REGEXP '[äåæçèé]';

-- 4) 复核：剩余乱码应为 0；抽样对照备份前后
SELECT COUNT(*) AS remaining_mojibake FROM carbon_report WHERE title REGEXP '[äåæçèé]';

SELECT b.id, b.title_before, r.title AS title_after
FROM carbon_report_title_bak_20260910 b
JOIN carbon_report r ON r.id = b.id
ORDER BY b.id
LIMIT 10;

-- 5) 回滚（仅在确认误伤时执行）
-- UPDATE carbon_report r
-- JOIN carbon_report_title_bak_20260910 b ON b.id = r.id
-- SET r.title = b.title_before;
