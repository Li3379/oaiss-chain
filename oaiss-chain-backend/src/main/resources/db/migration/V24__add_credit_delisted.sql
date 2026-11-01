-- R-03 下市处罚：delisted 独立字段，与信誉阈值自动启停解耦（不被 checkThresholds 覆盖）
-- raw 03 L1098「删除用户=暂时退出碳交易系统」；权限仅 ADMIN（L913 + role-matrix 定稿）
ALTER TABLE credit_score ADD COLUMN delisted TINYINT(1) NOT NULL DEFAULT 0;
