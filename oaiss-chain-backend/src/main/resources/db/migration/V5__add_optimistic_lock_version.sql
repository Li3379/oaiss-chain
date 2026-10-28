-- Add optimistic lock version column to financial entities
-- CON-02: Prevents lost-update anomaly on concurrent modifications

ALTER TABLE enterprise ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE carbon_coin_account ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE auction_order ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
