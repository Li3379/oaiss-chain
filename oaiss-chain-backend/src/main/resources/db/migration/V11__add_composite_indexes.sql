-- V11: Remaining performance indexes (only those not yet created)
CREATE INDEX idx_mr_buyer_del ON matching_result (buyer_id, is_deleted);
CREATE INDEX idx_tx_status_del ON transaction (status, is_deleted);
CREATE INDEX idx_mr_created_at ON matching_result (created_at);
CREATE INDEX idx_tx_seller_del ON transaction (seller_id, is_deleted);
CREATE INDEX idx_notif_user_read_del ON notification (user_id, is_read, is_deleted);
CREATE INDEX idx_tx_created_at ON transaction (created_at);
CREATE INDEX idx_notif_user_del ON notification (user_id, is_deleted);
CREATE INDEX idx_mr_seller_del ON matching_result (seller_id, is_deleted);
CREATE INDEX idx_tx_trade_no ON transaction (trade_no);
CREATE INDEX idx_tx_trade_type_del ON transaction (trade_type, is_deleted);
CREATE INDEX idx_tx_buyer_del ON transaction (buyer_id, is_deleted);
CREATE INDEX idx_ao_direction_status ON auction_order (direction, status, is_deleted);
CREATE INDEX idx_ce_user_del ON credit_event (triggered_by, is_deleted);