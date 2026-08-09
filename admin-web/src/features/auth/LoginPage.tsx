import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { AuthLayout } from '@/features/auth/AuthLayout';
import { OtpInput } from '@/features/auth/OtpInput';
import { loginWithOtp, loginWithPassword, sendLoginOtp } from '@/features/auth/api';
import { useAuth } from '@/lib/auth-context';
import { toApiError } from '@/lib/errors';

const RESEND_SECONDS = 45;
type Tab = 'password' | 'otp';

export function LoginPage() {
  const navigate = useNavigate();
  const { signIn } = useAuth();

  const [tab, setTab] = useState<Tab>('password');
  const [mobile, setMobile] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  const mobileValid = /^[6-9]\d{9}$/.test(mobile);

  // Ticks the 45-second resend window down to zero.
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setTimeout(() => setCooldown(cooldown - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [cooldown]);

  const handlePasswordLogin = async () => {
    setError(null);
    setBusy(true);
    try {
      signIn(await loginWithPassword(mobile, password));
      navigate('/home');
    } catch (caught) {
      const apiError = toApiError(caught);
      if (apiError.code === 'OTP_REQUIRED_TODAY') {
        // The server enforces one OTP per day; move the user to the tab that can satisfy it.
        setTab('otp');
        setNotice(apiError.message);
        setError(null);
      } else {
        setError(apiError.message);
      }
    } finally {
      setBusy(false);
    }
  };

  const handleSendOtp = async () => {
    setError(null);
    setBusy(true);
    try {
      await sendLoginOtp(mobile);
      setOtpSent(true);
      setCooldown(RESEND_SECONDS);
      setNotice(`We sent a 6-digit code to +91 ${mobile}.`);
    } catch (caught) {
      setError(toApiError(caught).message);
    } finally {
      setBusy(false);
    }
  };

  const handleVerifyOtp = async () => {
    setError(null);
    setBusy(true);
    try {
      signIn(await loginWithOtp(mobile, otp));
      navigate('/home');
    } catch (caught) {
      setError(toApiError(caught).message);
      setOtp('');
    } finally {
      setBusy(false);
    }
  };

  const switchTab = (next: Tab) => {
    setTab(next);
    setError(null);
  };

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Use your registered mobile number"
      footer={
        <>
          Don&apos;t have access? <Link to="/register" className="font-semibold text-navy-600 hover:underline">Register</Link>{' '}
          or contact your administrator.
        </>
      }
    >
      <div className="mb-5 grid grid-cols-2 gap-1 rounded-lg bg-slate-100 p-1">
        {(['password', 'otp'] as const).map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => switchTab(option)}
            className={`rounded-md px-3 py-2 text-sm font-semibold transition ${
              tab === option ? 'bg-white text-navy-700 shadow-sm' : 'text-slate-600 hover:text-navy-700'
            }`}
          >
            {option === 'password' ? 'Password' : 'OTP'}
          </button>
        ))}
      </div>

      <div className="space-y-4">
        <TextField
          label="Mobile number"
          value={mobile}
          onChange={(event) => setMobile(event.target.value.replace(/\D/g, '').slice(0, 10))}
          placeholder="10-digit mobile number"
          inputMode="numeric"
          autoComplete="tel-national"
          hint="Prefixed with +91"
        />

        {notice && <Alert tone="info">{notice}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}

        {tab === 'password' ? (
          <>
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
            />
            <Button
              fullWidth
              loading={busy}
              disabled={!mobileValid || password.length === 0}
              onClick={handlePasswordLogin}
            >
              Sign in
            </Button>
            <p className="text-center text-xs text-slate-500">
              The first sign-in each day requires an OTP.
            </p>
          </>
        ) : (
          <>
            {!otpSent ? (
              <Button fullWidth loading={busy} disabled={!mobileValid} onClick={handleSendOtp}>
                Send OTP
              </Button>
            ) : (
              <>
                <div className="space-y-2">
                  <span className="block text-sm font-medium text-slate-700">Enter the 6-digit OTP</span>
                  <OtpInput value={otp} onChange={setOtp} disabled={busy} autoFocus />
                </div>
                <Button
                  fullWidth
                  loading={busy}
                  disabled={otp.length !== 6}
                  onClick={handleVerifyOtp}
                >
                  Verify &amp; sign in
                </Button>
                <div className="text-center text-sm">
                  {cooldown > 0 ? (
                    <span className="text-slate-500">Resend OTP in {cooldown}s</span>
                  ) : (
                    <button
                      type="button"
                      onClick={handleSendOtp}
                      className="font-semibold text-navy-600 hover:underline"
                    >
                      Resend OTP
                    </button>
                  )}
                </div>
              </>
            )}
          </>
        )}

        <div className="flex items-center justify-center gap-2 rounded-lg bg-slate-50 py-2.5 text-xs text-slate-500">
          <span aria-hidden>🔒</span> Secure login
        </div>
      </div>
    </AuthLayout>
  );
}
