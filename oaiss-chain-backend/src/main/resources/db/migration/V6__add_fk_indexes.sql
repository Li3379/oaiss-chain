-- =============================================
-- V6: Add indexes on foreign key columns
-- Improves JOIN and lookup performance for FK columns
-- that lack indexes (excludes columns covered by unique constraints)
-- =============================================

-- carbon_report FK indexes
CREATE INDEX idx_carbon_report_enterprise_id ON carbon_report (enterprise_id);
CREATE INDEX idx_carbon_report_submitter_id ON carbon_report (submitter_id);
CREATE INDEX idx_carbon_report_reviewer_id ON carbon_report (reviewer_id);

-- transaction FK indexes
CREATE INDEX idx_transaction_seller_id ON transaction (seller_id);
CREATE INDEX idx_transaction_buyer_id ON transaction (buyer_id);
CREATE INDEX idx_transaction_report_id ON transaction (report_id);

-- auction_order FK index
CREATE INDEX idx_auction_order_user_id ON auction_order (user_id);

-- matching_result FK indexes
CREATE INDEX idx_matching_result_buy_order_id ON matching_result (buy_order_id);
CREATE INDEX idx_matching_result_sell_order_id ON matching_result (sell_order_id);
CREATE INDEX idx_matching_result_buyer_id ON matching_result (buyer_id);
CREATE INDEX idx_matching_result_seller_id ON matching_result (seller_id);
CREATE INDEX idx_matching_result_transaction_id ON matching_result (transaction_id);

-- rsa_key_pair FK index
CREATE INDEX idx_rsa_key_pair_user_id ON rsa_key_pair (user_id);

-- credit_event FK indexes
CREATE INDEX idx_credit_event_enterprise_id ON credit_event (enterprise_id);
CREATE INDEX idx_credit_event_related_report_id ON credit_event (related_report_id);
CREATE INDEX idx_credit_event_related_trade_id ON credit_event (related_trade_id);
CREATE INDEX idx_credit_event_triggered_by ON credit_event (triggered_by);

-- carbon_coin_transaction FK indexes
CREATE INDEX idx_carbon_coin_transaction_user_id ON carbon_coin_transaction (user_id);
CREATE INDEX idx_carbon_coin_transaction_related_trade_id ON carbon_coin_transaction (related_trade_id);
CREATE INDEX idx_carbon_coin_transaction_counterpart_id ON carbon_coin_transaction (counterpart_id);

-- emission_rating FK indexes
CREATE INDEX idx_emission_rating_enterprise_id ON emission_rating (enterprise_id);
CREATE INDEX idx_emission_rating_rated_by ON emission_rating (rated_by);

-- carbon_neutral_project FK indexes
CREATE INDEX idx_carbon_neutral_project_owner_id ON carbon_neutral_project (owner_id);
CREATE INDEX idx_carbon_neutral_project_reviewer_id ON carbon_neutral_project (reviewer_id);
CREATE INDEX idx_carbon_neutral_project_verifier_id ON carbon_neutral_project (verifier_id);

-- reviewer_qualification FK index
CREATE INDEX idx_reviewer_qualification_reviewer_id ON reviewer_qualification (reviewer_id);
