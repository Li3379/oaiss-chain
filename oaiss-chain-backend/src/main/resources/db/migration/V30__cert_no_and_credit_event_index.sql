-- V30__cert_no_and_credit_event_index.sql
-- 证书编号唯一约束 + 信誉事件幂等查询索引
--
-- 1) carbon_neutral_project.cert_no 由服务端生成（CNE-{year}-{6 位序号}）。
--    并发认证请求可能算出同一序号，唯一索引是最后一道兜底；冲突时业务层转 3007。
--    普通唯一索引即可：PostgreSQL 与 H2 对多个 NULL 均不判冲突，
--    历史未认证项目的 NULL cert_no 不会被误判重复。
--
-- 2) 碳中和认证加分前需查「该企业是否已存在 event_type=9 的事件」（每企业只加一次）。
--    credit_event 只有 enterprise_id 单列索引，补 (enterprise_id, event_type) 复合索引
--    避免每次认证全表扫。

CREATE UNIQUE INDEX uk_cnp_cert_no ON carbon_neutral_project (cert_no);

CREATE INDEX idx_credit_event_enterprise_event ON credit_event (enterprise_id, event_type);
