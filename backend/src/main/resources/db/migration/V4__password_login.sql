-- Password becomes the sign-in credential; OTP is no longer a way in.
--
-- Two things go with the daily-OTP rule that this replaces: the stamp that recorded
-- when a user last satisfied it, and the 'login' OTP purpose. Any login codes still
-- outstanding are deleted before the CHECK constraint is narrowed, since a value the
-- constraint no longer permits would fail the ALTER.

ALTER TABLE users DROP COLUMN IF EXISTS last_otp_login_date;

DELETE FROM otp_verifications WHERE purpose = 'login';

ALTER TABLE otp_verifications DROP CONSTRAINT IF EXISTS otp_verifications_purpose_check;
ALTER TABLE otp_verifications
    ADD CONSTRAINT otp_verifications_purpose_check
    CHECK (purpose IN ('registration', 'password_reset'));

-- The seeded first admin (V3) still has no password and still starts with a reset over
-- OTP; only the sentence in V3 about last_otp_login_date is now out of date. That file is
-- left untouched on purpose: Flyway checksums applied migrations, and editing even a
-- comment in one would fail validation on every existing deployment.
