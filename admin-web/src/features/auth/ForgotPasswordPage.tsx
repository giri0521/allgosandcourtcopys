import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { AuthLayout } from '@/features/auth/AuthLayout';
import { OtpInput } from '@/features/auth/OtpInput';
import { resetPassword, sendPasswordResetOtp } from '@/features/auth/api';
import { toApiError } from '@/lib/errors';

const RESEND_SECONDS = 45;

/**
 * Password reset by OTP. There is no email link: the mobile number is the identity.
 *
 * <p>The request step always reports success, whether or not the number is registered, so this
 * screen cannot be used to discover who has an account. A reset also revokes existing sessions
 * server-side, in case the account had been taken over.
 */
export function ForgotPasswordPage() {
  const navigate = useNavigate();

  const [step, setStep] = useState<'request' | 'reset'>('request');
  const [mobile, setMobile] = useState('');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  const mobileValid = /^[6-9]\d{9}$/.test(mobile);
  const passwordsMatch = password === confirm;

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setTimeout(() => setCooldown(cooldown - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [cooldown]);

  const handleSend = async () => {
    setError(null);
    setBusy(true);
    try {
      await sendPasswordResetOtp(mobile);
      setStep('reset');
      setCooldown(RESEND_SECONDS);
      setNotice(`If ${mobile} is registered, we have sent it a 6-digit code.`);
    } catch (caught) {
      setError(toApiError(caught).message);
    } finally {
      setBusy(false);
    }
  };

  const handleReset = async () => {
    setError(null);
    setFieldErrors({});
    setBusy(true);
    try {
      await resetPassword(mobile, otp, password);
      navigate('/login', {
        state: { notice: 'Password changed. Sign in with an OTP to start today’s session.' },
      });
    } catch (caught) {
      const apiError = toApiError(caught);
      setError(apiError.message);
      setFieldErrors(apiError.fieldErrors ?? {});
      setOtp('');
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      title="Reset your password"
      subtitle="We verify it with an OTP on your registered mobile number"
      footer={
        <Link to="/login" className="font-semibold text-navy-600 hover:underline">
          Back to sign in
        </Link>
      }
    >
      <div className="space-y-4">
        {notice && <Alert tone="info">{notice}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}

        <TextField
          label="Mobile number"
          value={mobile}
          onChange={(event) => setMobile(event.target.value.replace(/\D/g, '').slice(0, 10))}
          disabled={step === 'reset'}
          placeholder="10-digit mobile number"
          inputMode="numeric"
          autoComplete="tel-national"
          hint="Prefixed with +91"
        />

        {step === 'request' ? (
          <Button fullWidth loading={busy} disabled={!mobileValid} onClick={handleSend}>
            Send OTP
          </Button>
        ) : (
          <>
            <div className="space-y-2">
              <span className="block text-sm font-medium text-slate-700">Enter the 6-digit OTP</span>
              <OtpInput value={otp} onChange={setOtp} disabled={busy} autoFocus />
            </div>

            <TextField
              label="New password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              error={fieldErrors.newPassword}
              hint="At least 8 characters"
              autoComplete="new-password"
            />

            <TextField
              label="Confirm new password"
              type="password"
              value={confirm}
              onChange={(event) => setConfirm(event.target.value)}
              error={confirm && !passwordsMatch ? 'Passwords do not match' : undefined}
              autoComplete="new-password"
            />

            <Button
              fullWidth
              loading={busy}
              disabled={otp.length !== 6 || password.length < 8 || !passwordsMatch}
              onClick={handleReset}
            >
              Change password
            </Button>

            <div className="text-center text-sm">
              {cooldown > 0 ? (
                <span className="text-slate-500">Resend OTP in {cooldown}s</span>
              ) : (
                <button
                  type="button"
                  onClick={handleSend}
                  className="font-semibold text-navy-600 hover:underline"
                >
                  Resend OTP
                </button>
              )}
            </div>

            <p className="text-center text-xs text-slate-500">
              Changing your password signs you out everywhere. The first sign-in of each day still
              needs an OTP.
            </p>
          </>
        )}
      </div>
    </AuthLayout>
  );
}
