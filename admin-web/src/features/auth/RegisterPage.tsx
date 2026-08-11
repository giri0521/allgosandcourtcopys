import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SelectField, TextField } from '@/components/ui/Field';
import { AuthLayout } from '@/features/auth/AuthLayout';
import { OtpInput } from '@/features/auth/OtpInput';
import { fetchDepartments, register, verifyRegistrationOtp } from '@/features/auth/api';
import { toApiError } from '@/lib/errors';
import type { Department } from '@/types/api';

export function RegisterPage() {
  const navigate = useNavigate();

  const [departments, setDepartments] = useState<Department[]>([]);
  /**
   * Registration is two steps: the form creates the PENDING account and sends a code, then the code
   * confirms the mobile number. Confirming grants nothing — an admin still has to approve — but it
   * proves the number is reachable before a reviewer spends time on the request.
   */
  const [step, setStep] = useState<'form' | 'verify'>('form');
  const [otp, setOtp] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const [form, setForm] = useState({
    fullName: '',
    mobileNumber: '',
    departmentId: '',
    designation: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchDepartments()
      .then(setDepartments)
      .catch((caught) => setError(toApiError(caught).message));
  }, []);

  const update = (key: keyof typeof form) => (value: string) =>
    setForm((current) => ({ ...current, [key]: value }));

  const passwordsMatch = form.password === form.confirmPassword;
  const canSubmit =
    form.fullName.trim().length > 0 &&
    /^[6-9]\d{9}$/.test(form.mobileNumber) &&
    form.departmentId.length > 0 &&
    form.password.length >= 8 &&
    passwordsMatch;

  const handleSubmit = async () => {
    setError(null);
    setFieldErrors({});
    setBusy(true);
    try {
      await register({
        fullName: form.fullName.trim(),
        mobileNumber: form.mobileNumber,
        departmentId: form.departmentId,
        designation: form.designation || undefined,
        email: form.email || undefined,
        password: form.password,
      });
      setStep('verify');
      setNotice(`We sent a 6-digit code to +91 ${form.mobileNumber}.`);
    } catch (caught) {
      const apiError = toApiError(caught);
      setError(apiError.message);
      setFieldErrors(apiError.fieldErrors ?? {});
    } finally {
      setBusy(false);
    }
  };

  const handleVerify = async () => {
    setError(null);
    setBusy(true);
    try {
      await verifyRegistrationOtp(form.mobileNumber, otp);
      navigate('/register/pending', {
        state: { mobileNumber: form.mobileNumber, verified: true },
      });
    } catch (caught) {
      setError(toApiError(caught).message);
      setOtp('');
    } finally {
      setBusy(false);
    }
  };

  /**
   * The account already exists and is pending review, so an unconfirmed number is not a dead end —
   * skipping only means the admin sees an unverified request.
   */
  const handleSkip = () =>
    navigate('/register/pending', {
      state: { mobileNumber: form.mobileNumber, verified: false },
    });

  if (step === 'verify') {
    return (
      <AuthLayout
        title="Confirm your mobile number"
        subtitle={`Enter the code we sent to +91 ${form.mobileNumber}`}
        footer={
          <button type="button" onClick={handleSkip} className="font-semibold text-navy-600 hover:underline">
            Skip for now
          </button>
        }
      >
        <div className="space-y-4">
          {notice && <Alert tone="info">{notice}</Alert>}
          {error && <Alert tone="error">{error}</Alert>}

          <div className="space-y-2">
            <span className="block text-sm font-medium text-slate-700">Enter the 6-digit OTP</span>
            <OtpInput value={otp} onChange={setOtp} disabled={busy} autoFocus />
          </div>

          <Button fullWidth loading={busy} disabled={otp.length !== 6} onClick={handleVerify}>
            Confirm number
          </Button>

          <p className="text-center text-sm text-slate-500">
            The code expires in five minutes. If it does not arrive, skip this step — your request
            has already reached the administrators.
          </p>

          <div className="rounded-lg bg-slate-50 px-4 py-3 text-xs text-slate-500">
            Confirming your number does not grant access. An administrator still has to approve your
            registration before you can sign in.
          </div>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle="An administrator reviews every registration before access is granted"
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" className="font-semibold text-navy-600 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <div className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}

        <TextField
          label="Full name"
          value={form.fullName}
          onChange={(event) => update('fullName')(event.target.value)}
          error={fieldErrors.fullName}
          autoComplete="name"
        />

        <TextField
          label="Mobile number"
          value={form.mobileNumber}
          onChange={(event) => update('mobileNumber')(event.target.value.replace(/\D/g, '').slice(0, 10))}
          error={fieldErrors.mobileNumber}
          hint="+91 · this is your login identity"
          inputMode="numeric"
          autoComplete="tel-national"
        />

        <SelectField
          label="Department"
          value={form.departmentId}
          onChange={(event) => update('departmentId')(event.target.value)}
          error={fieldErrors.departmentId}
        >
          <option value="">Select your department</option>
          {departments.map((department) => (
            <option key={department.id} value={department.id}>
              {department.name}
            </option>
          ))}
        </SelectField>

        <TextField
          label="Designation"
          value={form.designation}
          onChange={(event) => update('designation')(event.target.value)}
          error={fieldErrors.designation}
          placeholder="e.g. Section Officer"
        />

        <TextField
          label="Email (optional)"
          type="email"
          value={form.email}
          onChange={(event) => update('email')(event.target.value)}
          error={fieldErrors.email}
          autoComplete="email"
        />

        <TextField
          label="Password"
          type="password"
          value={form.password}
          onChange={(event) => update('password')(event.target.value)}
          error={fieldErrors.password}
          hint="At least 8 characters · used after your daily OTP"
          autoComplete="new-password"
        />

        <TextField
          label="Confirm password"
          type="password"
          value={form.confirmPassword}
          onChange={(event) => update('confirmPassword')(event.target.value)}
          error={form.confirmPassword && !passwordsMatch ? 'Passwords do not match' : undefined}
          autoComplete="new-password"
        />

        <Button fullWidth loading={busy} disabled={!canSubmit} onClick={handleSubmit}>
          Submit registration
        </Button>
      </div>
    </AuthLayout>
  );
}
