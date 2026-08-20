-- Registration no longer sends a confirmation code: an account is created PENDING and
-- waits for an admin, which is the only gate that decides access. The password reset is
-- the one purpose left.
--
-- Outstanding registration codes are deleted before the constraint is narrowed; a value
-- the new CHECK does not permit would fail the ALTER.

DELETE FROM otp_verifications WHERE purpose = 'registration';

ALTER TABLE otp_verifications DROP CONSTRAINT IF EXISTS otp_verifications_purpose_check;
ALTER TABLE otp_verifications
    ADD CONSTRAINT otp_verifications_purpose_check
    CHECK (purpose IN ('password_reset'));
