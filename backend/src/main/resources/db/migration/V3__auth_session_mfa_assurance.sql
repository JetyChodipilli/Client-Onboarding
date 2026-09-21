ALTER TABLE auth_sessions
    ADD COLUMN mfa_verified_at timestamptz;

COMMENT ON COLUMN auth_sessions.mfa_verified_at IS
    'Authentication time at which this session completed MFA; NULL means password-only assurance.';
