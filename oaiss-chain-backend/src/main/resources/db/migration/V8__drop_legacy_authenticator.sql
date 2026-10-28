-- Retire legacy authenticator table.
-- The runtime role baseline is ENTERPRISE / REVIEWER / THIRD_PARTY / ADMIN.
-- Older environments may still carry this unused table from V1.

DROP TABLE IF EXISTS `authenticator`;
