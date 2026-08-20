import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { AuthLayout } from '@/features/auth/AuthLayout';
import { loginWithPassword } from '@/features/auth/api';
import { useAuth } from '@/lib/auth-context';
import { toApiError } from '@/lib/errors';

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { signIn } = useAuth();

  // Where the guard bounced them from, and anything the previous screen wants said (a completed
  // password reset, for instance).
  const state = location.state as { from?: string; notice?: string } | null;
  const destination = state?.from ?? '/home';

  const [mobile, setMobile] = useState('');
  const [password, setPassword] = useState('');
  const [notice] = useState<string | null>(state?.notice ?? null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const mobileValid = /^[6-9]\d{9}$/.test(mobile);
  const canSubmit = mobileValid && password.length > 0 && !busy;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    setError(null);
    setBusy(true);
    try {
      signIn(await loginWithPassword(mobile, password));
      navigate(destination, { replace: true });
    } catch (caught) {
      setError(toApiError(caught).message);
    } finally {
      setBusy(false);
    }
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
      {/* A real form, so the browser offers to fill it and Enter submits from either field. */}
      <form className="space-y-4" onSubmit={handleSubmit}>
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

        <TextField
          label="Password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
        />

        <Button type="submit" fullWidth loading={busy} disabled={!canSubmit}>
          Sign in
        </Button>

        <div className="text-center text-sm">
          <Link to="/forgot-password" className="font-semibold text-navy-600 hover:underline">
            Forgot password?
          </Link>
        </div>

        <div className="flex items-center justify-center gap-2 rounded-lg border border-line bg-navy-50/70 py-2.5 text-xs font-medium text-navy-700">
          <span aria-hidden>🔒</span> Secure login
        </div>
      </form>
    </AuthLayout>
  );
}
