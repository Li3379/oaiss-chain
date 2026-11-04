-- V29_1__dedupe_cert_no_before_unique_index.sql
-- 为 V30 的唯一索引做数据前置修复（版本号 29.1，排在 V30 之前）
--
-- 历史问题：旧版 completeCertification 允许客户端自填 certNo，
-- scripts/api/project-lifecycle-test.sh 每次运行都写入同一个 CERT-TEST-001，
-- 已在库中累积多条同号记录。V30 直接 CREATE UNIQUE INDEX 在含此类数据的库上
-- 必然失败，并留下 success=0 的 Flyway 记录使应用无法启动。
--
-- 本条迁移给重号行追加 -LEGACY-{id} 后缀：原值仍可读、可追溯，且不再冲突。
-- 无重复数据的环境为空操作。
--
-- 执行顺序：本迁移 < V30。使用 29.1 版本号是为了让已经在 V30 失败过的库
-- 在 flyway repair 之后能先补跑本条，再重新执行 V30。

UPDATE carbon_neutral_project
SET cert_no = CONCAT(cert_no, '-LEGACY-', id)
WHERE cert_no IS NOT NULL
  AND cert_no IN (
    SELECT cert_no FROM (
      SELECT cert_no
      FROM carbon_neutral_project
      WHERE cert_no IS NOT NULL
      GROUP BY cert_no
      HAVING COUNT(*) > 1
    ) AS dup
  );