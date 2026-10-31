-- V19__add_certificate_revocation.sql
-- 证书吊销留痕 + 存量准入证书回填
--
-- 背景：管理员点「吊销」原本只把 enterprise_admission.status 置 2，没有任何业务
-- 服务读取该字段（全仓 EnterpriseAdmissionRepository 仅活在 Service 内部），
-- 吊销是孤岛写入——企业照样登录、挂单、成交、提交报告，审核员照样审核，
-- 而 UI 与文档却宣称「没有准入证书就不能交易」。
--
-- 本迁移只做两件事：
--   1) 让吊销这件事本身可留痕（谁在什么时候因为什么被吊销）；
--   2) 给现存 ACTIVE 企业补 expiry_date，让有效期这一列第一次真正有意义，
--      否则新门禁一旦启用以「expiry_date >= today」为判据，全部存量证书
--      都会被判为过期而集体失效。
--
-- 门禁本身在代码层（CertificateGateService），不在数据库层。

-- 1. 吊销留痕
ALTER TABLE `enterprise_admission`
    ADD COLUMN `revoked_at`     DATETIME     DEFAULT NULL COMMENT '吊销时间',
    ADD COLUMN `revoke_reason`  VARCHAR(200) DEFAULT NULL COMMENT '吊销原因（管理员填写，可选）';

ALTER TABLE `reviewer_qualification`
    ADD COLUMN `revoked_at`     DATETIME     DEFAULT NULL COMMENT '吊销时间',
    ADD COLUMN `revoke_reason`  VARCHAR(200) DEFAULT NULL COMMENT '吊销原因（管理员填写，可选）';

-- 2. 存量回填：有效期缺失的 ACTIVE 证书按「签发日 + 1 年」补，签发日也缺失则按今天
UPDATE `enterprise_admission`
   SET `expiry_date` = DATE_ADD(COALESCE(`issued_date`, CURRENT_DATE), INTERVAL 1 YEAR)
 WHERE `status` = 1
   AND `expiry_date` IS NULL;

UPDATE `reviewer_qualification`
   SET `expiry_date` = DATE_ADD(COALESCE(`issued_date`, CURRENT_DATE), INTERVAL 1 YEAR)
 WHERE `status` = 1
   AND `expiry_date` IS NULL;

-- 3. 已吊销但历史未记时间的记录：以 updated_at 兜底，避免审计时出现「已吊销但无时间」
UPDATE `enterprise_admission`
   SET `revoked_at` = `updated_at`
 WHERE `status` = 2
   AND `revoked_at` IS NULL;

UPDATE `reviewer_qualification`
   SET `revoked_at` = `updated_at`
 WHERE `status` = 2
   AND `revoked_at` IS NULL;
