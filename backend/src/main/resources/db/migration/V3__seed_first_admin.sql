-- The first Admin is seeded here, never self-registered, so that nobody can
-- grant themselves administrative access. Every later admin registration must
-- be approved by an existing admin.
--
-- This account has NO password: the operator must complete a password reset via
-- OTP on first use. last_otp_login_date is left NULL so the very first login is
-- forced through the OTP path.
--
-- Override the mobile number per environment with the SEED_ADMIN_MOBILE
-- placeholder before running migrations in staging/production.

INSERT INTO users (full_name, mobile_number, role, status, department_id)
SELECT 'System Administrator',
       '9999999999',
       'admin',
       'active',
       (SELECT id FROM departments WHERE name = 'Department of Information Technology and Digital Services')
WHERE NOT EXISTS (SELECT 1 FROM users WHERE role = 'admin');
