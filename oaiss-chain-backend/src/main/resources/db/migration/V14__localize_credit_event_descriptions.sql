-- =============================================
-- V14: 信用分事件描述本地化（收敛历史硬编码英文）
--
-- 影响范围（仅这两类由生产代码生成的文案，见 CarbonService.java 历史版本）：
--   1) 认证奖励: "Carbon report certification reward"        -> 碳报告认证通过奖励
--   2) 审核驳回: "Carbon report audit rejected: <reportNo>"  -> 碳报告审核驳回：<reportNo>
--
-- 不修改的内容：测试/压测遗留的 "Deduction toward WARNING|DANGER|FROZEN"、
-- "Score recovery test"、"test" 等人工造数（非产品文案，需人工判断语义）。
-- 幂等：可重复执行，已本地化的行不会被再次匹配。
-- =============================================

UPDATE credit_event
SET event_description = '碳报告认证通过奖励'
WHERE event_description = 'Carbon report certification reward';

UPDATE credit_event
SET event_description = CONCAT('碳报告审核驳回：', SUBSTRING(event_description, CHAR_LENGTH('Carbon report audit rejected: ') + 1))
WHERE event_description LIKE 'Carbon report audit rejected: %';
