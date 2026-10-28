-- =============================================
-- 双碳链动系统种子数据
-- OAISS Chain Seed Data
-- MySQL 8.0+
-- =============================================

SET NAMES utf8mb4;

-- =============================================
-- 1. 初始管理员账户
-- 密码: admin123 (BCrypt加密)
-- =============================================

INSERT INTO `user` (`id`, `username`, `password`, `email`, `phone`, `real_name`, `user_type`, `status`, `created_at`, `updated_at`)
VALUES (1, 'admin', '$2a$10$hBOIu5glgwd4b8ohK/K/mOCFTZmHnDBskNnzr9ngA5J6jFCH57Vva', 'admin@oaiss.com', '13800138000', '系统管理员', 4, 1, NOW(), NOW());

-- =============================================
-- 2. 用户类型列表（4角色体系）
-- =============================================

INSERT INTO `user_type_list` (`id`, `type_name`, `type_code`, `description`, `default_role`, `created_at`, `updated_at`)
VALUES
(1, '企业用户', 'ENTERPRISE', '碳排放企业用户', 'ROLE_ENTERPRISE', NOW(), NOW()),
(2, '审核员', 'REVIEWER', '碳报告审核员', 'ROLE_REVIEWER', NOW(), NOW()),
(3, '第三方机构', 'THIRD_PARTY', '第三方监管机构', 'ROLE_THIRD_PARTY', NOW(), NOW()),
(4, '管理员', 'ADMIN', '系统管理员', 'ROLE_ADMIN', NOW(), NOW());

-- =============================================
-- 3. API权限
-- =============================================

INSERT INTO `entry_permission` (`id`, `user_type`, `api_path`, `http_method`, `is_allowed`, `created_at`, `updated_at`)
VALUES
(1, 4, '/api/v1/admin/**', 'GET', 1, NOW(), NOW()),
(2, 4, '/api/v1/admin/**', 'POST', 1, NOW(), NOW()),
(3, 4, '/api/v1/admin/**', 'PUT', 1, NOW(), NOW()),
(4, 4, '/api/v1/admin/**', 'DELETE', 1, NOW(), NOW()),
(5, 1, '/api/v1/enterprise/**', 'GET', 1, NOW(), NOW()),
(6, 1, '/api/v1/enterprise/**', 'POST', 1, NOW(), NOW()),
(7, 1, '/api/v1/enterprise/**', 'PUT', 1, NOW(), NOW()),
(8, 2, '/api/v1/reviewer/**', 'GET', 1, NOW(), NOW()),
(9, 2, '/api/v1/reviewer/**', 'POST', 1, NOW(), NOW()),
(10, 3, '/api/v1/third-party/**', 'GET', 1, NOW(), NOW()),
(11, 3, '/api/v1/third-party/**', 'POST', 1, NOW(), NOW());

-- =============================================
-- 4. 账户权限定义
-- =============================================

INSERT INTO `account_permission_list` (`id`, `permission_name`, `permission_code`, `description`, `module`, `sort_order`, `created_at`, `updated_at`)
VALUES
(1, '碳报告管理', 'CARBON_REPORT', '碳报告的创建、编辑、提交', 'carbon', 1, NOW(), NOW()),
(2, '碳报告审核', 'CARBON_REVIEW', '审核企业提交的碳报告', 'carbon', 2, NOW(), NOW()),
(3, '碳交易管理', 'CARBON_TRADE', '碳配额交易操作', 'trade', 3, NOW(), NOW()),
(4, '拍卖管理', 'AUCTION_MANAGE', '拍卖订单管理', 'trade', 4, NOW(), NOW()),
(5, '信誉评分管理', 'CREDIT_MANAGE', '用户信誉评分管理', 'credit', 5, NOW(), NOW()),
(6, '第三方监管', 'THIRD_PARTY_MONITOR', '第三方监管权限', 'monitor', 6, NOW(), NOW()),
(7, '系统管理', 'SYSTEM_ADMIN', '系统管理权限', 'admin', 7, NOW(), NOW()),
(8, '数据查询', 'DATA_QUERY', '数据查询与统计', 'data', 8, NOW(), NOW()),
(9, '区块链存证', 'BLOCKCHAIN_NOTARY', '区块链存证操作', 'blockchain', 9, NOW(), NOW()),
(10, '碳币管理', 'CARBON_COIN', '碳币账户管理', 'carbon_coin', 10, NOW(), NOW());

-- =============================================
-- 5. 示例企业用户
-- =============================================

INSERT INTO `user` (`id`, `username`, `password`, `email`, `phone`, `real_name`, `user_type`, `status`, `created_at`, `updated_at`)
VALUES
(2, 'enterprise001', '$2a$10$hBOIu5glgwd4b8ohK/K/mOCFTZmHnDBskNnzr9ngA5J6jFCH57Vva', 'enterprise001@example.com', '13800138001', '张三', 1, 1, NOW(), NOW()),
(3, 'enterprise002', '$2a$10$hBOIu5glgwd4b8ohK/K/mOCFTZmHnDBskNnzr9ngA5J6jFCH57Vva', 'enterprise002@example.com', '13800138002', '李四', 1, 1, NOW(), NOW()),
(7, 'enterprise003', '$2a$10$hBOIu5glgwd4b8ohK/K/mOCFTZmHnDBskNnzr9ngA5J6jFCH57Vva', 'enterprise003@example.com', '13800138006', '孙六', 1, 1, NOW(), NOW());

INSERT INTO `enterprise` (`id`, `user_id`, `enterprise_name`, `credit_code`, `address`, `industry`, `scale`, `contact_person`, `contact_phone`, `cert_status`, `carbon_quota`, `carbon_used`, `carbon_tradable`, `created_at`, `updated_at`)
VALUES
(1, 2, '绿色能源科技有限公司', '91110000MA001AABCD', '北京市海淀区中关村大街1号', '新能源', '中型', '张三', '13800138001', 2, 50000, 12000, 38000, NOW(), NOW()),
(2, 3, '低碳制造股份有限公司', '91110000MA002AABCD', '北京市朝阳区建国路100号', '制造业', '大型', '李四', '13800138002', 2, 80000, 25000, 55000, NOW(), NOW()),
(3, 7, '清洁能源发展有限公司', '91110000MA003AABCD', '北京市丰台区科技园南路8号', '清洁能源', '中型', '孙六', '13800138006', 2, 50000, 0, 50000, NOW(), NOW());

-- =============================================
-- 6. 示例审核员
-- =============================================

INSERT INTO `user` (`id`, `username`, `password`, `email`, `phone`, `real_name`, `user_type`, `status`, `created_at`, `updated_at`)
VALUES (4, 'reviewer001', '$2a$10$hBOIu5glgwd4b8ohK/K/mOCFTZmHnDBskNnzr9ngA5J6jFCH57Vva', 'reviewer001@example.com', '13800138003', '王审核', 2, 1, NOW(), NOW());

INSERT INTO `reviewer` (`id`, `user_id`, `qualification_no`, `level`, `organization`, `reviewable_industries`, `completed_reviews`, `status`, `created_at`, `updated_at`)
VALUES (1, 4, 'REV20260001', 3, '碳核算审核中心', '["新能源","制造业","化工"]', 156, 1, NOW(), NOW());

INSERT INTO `reviewer_qualification` (`id`, `reviewer_id`, `qualification_type`, `certificate_no`, `issuing_authority`, `issued_date`, `expiry_date`, `status`, `created_at`, `updated_at`)
VALUES (1, 1, '碳核算审核员', 'CERT-2026-001', '中国碳排放权交易协会', '2024-01-15', '2027-01-15', 1, NOW(), NOW());

-- =============================================
-- 7. 示例第三方机构
-- =============================================

INSERT INTO `user` (`id`, `username`, `password`, `email`, `phone`, `real_name`, `user_type`, `status`, `created_at`, `updated_at`)
VALUES (5, 'thirdparty001', '$2a$10$hBOIu5glgwd4b8ohK/K/mOCFTZmHnDBskNnzr9ngA5J6jFCH57Vva', 'thirdparty001@example.com', '13800138004', '赵监管', 3, 1, NOW(), NOW());

INSERT INTO `third_party_org` (`id`, `user_id`, `org_name`, `org_code`, `org_type`, `supervision_scope`, `contact_person`, `contact_phone`, `address`, `access_level`, `status`, `created_at`, `updated_at`)
VALUES (1, 5, '中国碳排放权注册登记机构', 'ORG001', 1, '["全部行业"]', '赵监管', '13800138004', '北京市西城区金融大街15号', 3, 1, NOW(), NOW());

-- =============================================
-- 8. 初始信誉评分
-- =============================================

INSERT INTO `credit_score` (`id`, `enterprise_id`, `score`, `level`, `trade_restricted`, `account_frozen`, `created_at`, `updated_at`)
VALUES
(1, 1, 100, 'EXCELLENT', 0, 0, NOW(), NOW()),
(2, 2, 100, 'EXCELLENT', 0, 0, NOW(), NOW()),
(3, 3, 100, 'EXCELLENT', 0, 0, NOW(), NOW());

-- =============================================
-- 9. 初始碳币账户
-- =============================================

INSERT INTO `carbon_coin_account` (`user_id`, `balance`, `total_recharged`, `total_spent`, `status`, `created_at`, `updated_at`)
VALUES
(2, 10000, 10000, 0, 1, NOW(), NOW()),
(3, 10000, 10000, 0, 1, NOW(), NOW()),
(7, 10000, 10000, 0, 1, NOW(), NOW());
