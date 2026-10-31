-- AC-B6：年度评级可追溯
--
-- 背景：评级从「最后一份报告的排放量」改为「该年度权威报告汇总」（D10）后，
-- 界面上的总量变成聚合值，但表里没有任何字段说明「这个数是几份报告加出来的」。
-- 审计方看到等级和总量无法回溯到源报告，评级因此不可复核。
--
-- 补齐：source_report_count = 本次评级汇总所依据的报告份数。
-- 存量记录留 NULL，表示「旧口径生成、来源未知」，不伪造为 1。
ALTER TABLE emission_rating
    ADD COLUMN source_report_count INT NULL COMMENT '本次评级汇总所依据的权威报告份数（NULL=旧数据，来源未知）'
    AFTER rating_score;