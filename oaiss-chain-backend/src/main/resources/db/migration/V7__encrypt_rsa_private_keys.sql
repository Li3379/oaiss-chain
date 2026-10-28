-- Add encrypted flag column to track which private keys are AES-256-GCM encrypted
ALTER TABLE rsa_key_pair ADD COLUMN encrypted TINYINT(1) NOT NULL DEFAULT 0;
