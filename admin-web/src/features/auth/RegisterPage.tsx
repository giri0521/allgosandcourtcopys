import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SelectField, TextField } from '@/components/ui/Field';
import { AuthLayout } from '@/features/auth/AuthLayout';
import { fetchDepartments, register } from '@/features/auth/api';
import { toApiError } from '@/lib/errors';
import type { Department } from '@/types/api';

export function RegisterPage() {
  const navigate = useNavigate();

  const [departments, setDepartments] = useState<Department[]>([]);
  /**
   * One step. The form creates the PENDING account and that is the whole of it — an administrator
   * approving the request is the only thing that grants access, so there is nothing for a second
   * screen to add.
   */
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
      navigate('/register/pending', { state: { mobileNumber: form.mobileNumber } });
    } catch (caught) {
      const apiError = toApiError(caught);
      setError(apiError.message);
      setFieldErrors(apiError.fieldErrors ?? {});
    } finally {
      setBusy(false);
    }
  };

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
          hint="At least 8 characters · this is how you sign in"
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
