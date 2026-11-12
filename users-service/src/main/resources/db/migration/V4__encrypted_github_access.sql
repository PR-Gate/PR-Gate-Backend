-- Existing links remain valid identities; users must renew GitHub authorization for private access.
ALTER TABLE users.github_account_links
    ADD COLUMN access_token_ciphertext TEXT,
    ADD COLUMN access_token_expires_at TIMESTAMP WITH TIME ZONE;
