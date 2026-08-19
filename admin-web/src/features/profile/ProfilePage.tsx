import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { SkeletonRows } from '@/components/ui/Skeleton';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { ThemeModeChoice } from '@/components/ui/ThemeToggle';
import { changePassword, fetchMe, updateProfile } from '@/features/profile/api';
import { useAuth } from '@/lib/auth-context';
import { toApiError } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { initials, tone } from '@/lib/tones';

/**
 * My Profile: the three fields a member may correct about themselves, and their password.
 *
 * <p>What is *not* editable is shown anyway, greyed: mobile number, department, role and status.
 * Leaving them out entirely would look like an oversight and invite a support call; showing them as
 * fixed says who to ask instead.
 */
export function ProfilePage() {
  const queryClient = useQueryClient();

  const me = useQuery({ queryKey: ['me'], queryFn: fetchMe });

  return (
    <AppShell title="My Profile" subtitle="Your account, and what only an administrator can change.">
      {me.isPending && <SkeletonRows count={3} label="Loading your profile" />}
      {me.isError && <Alert tone="error">{toApiError(me.error).message}</Alert>}

      {me.isSuccess && (
        <div className="grid gap-6 lg:grid-cols-3">
          <div className="lg:col-span-2 space-y-6">
            <DetailsCard
              key={me.data.id}
              fullName={me.data.fullName}
              email={me.data.email ?? ''}
              designation={me.data.designation ?? ''}
              onSaved={() => {
                void queryClient.invalidateQueries({ queryKey: ['me'] });
              }}
            />
            <PasswordCard />
          </div>

          <aside className="space-y-4">
            <AppearanceCard />

            <div className="rounded-xl border border-line bg-surface p-5 shadow-card">
              <div className="flex items-center gap-3">
                <span
                  className={`flex h-12 w-12 items-center justify-center rounded-full text-sm font-bold
                    ${tone('navy').chip}`}
                >
                  {initials(me.data.fullName)}
                </span>
                <div className="min-w-0">
                  <p className="truncate font-semibold text-slate-900">{me.data.fullName}</p>
                  <p className="text-sm text-slate-500">
                    {me.data.role === 'ADMIN' ? 'Administrator' : 'Member'}
                  </p>
                </div>
              </div>

              {/* Fixed by an administrator. Shown rather than hidden, so it is clear they exist
                  and clear who to ask. */}
              <dl className="mt-5 space-y-3 text-sm">
                <Fixed label="Mobile number" value={`+91 ${me.data.mobileNumber}`} />
                <Fixed label="Department" value={me.data.departmentName ?? '—'} />
                <div>
                  <dt className="text-xs uppercase tracking-wide text-slate-500">Account status</dt>
                  <dd className="mt-1">
                    <StatusBadge status={me.data.status} />
                  </dd>
                </div>
                <Fixed label="Last sign-in" value={formatDateTime(me.data.lastLoginAt)} />
                <Fixed label="Member since" value={formatDateTime(me.data.createdAt)} />
              </dl>

              <p className="mt-4 border-t border-line pt-3 text-xs text-slate-500">
                Your mobile number is your sign-in identity. To change it, or to move to another
                department, ask an administrator.
              </p>
            </div>
          </aside>
        </div>
      )}
    </AppShell>
  );
}

function DetailsCard({
  fullName: initialName,
  email: initialEmail,
  designation: initialDesignation,
  onSaved,
}: {
  fullName: string;
  email: string;
  designation: string;
  onSaved: () => void;
}) {
  const [fullName, setFullName] = useState(initialName);
  const [email, setEmail] = useState(initialEmail);
  const [designation, setDesignation] = useState(initialDesignation);
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: () =>
      updateProfile({
        fullName: fullName.trim(),
        email: email.trim() || undefined,
        designation: designation.trim() || undefined,
      }),
    onSuccess: () => {
      setSaved(true);
      onSaved();
    },
  });

  // The confirmation is transient: it has said what it needs to after a few seconds, and leaving
  // it up makes the next save look like it did nothing.
  useEffect(() => {
    if (!saved) return;
    const timer = window.setTimeout(() => setSaved(false), 4000);
    return () => window.clearTimeout(timer);
  }, [saved]);

  const dirty =
    fullName !== initialName || email !== initialEmail || designation !== initialDesignation;
  const fieldErrors = save.isError ? (toApiError(save.error).fieldErrors ?? {}) : {};

  return (
    <section className="rounded-xl border border-line bg-surface p-6 shadow-card">
      <h2 className="font-semibold text-slate-900">Your details</h2>
      <p className="mt-1 text-sm text-slate-500">
        These appear beside the documents you upload.
      </p>

      <form
        className="mt-5 space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (dirty && fullName.trim()) save.mutate();
        }}
      >
        <TextField
          label="Full name"
          value={fullName}
          onChange={(event) => setFullName(event.target.value)}
          error={fieldErrors.fullName}
          autoComplete="name"
          required
        />
        <TextField
          label="Email (optional)"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={fieldErrors.email}
          autoComplete="email"
        />
        <TextField
          label="Designation"
          value={designation}
          onChange={(event) => setDesignation(event.target.value)}
          error={fieldErrors.designation}
          placeholder="e.g. Section Officer"
        />

        {save.isError && <Alert tone="error">{toApiError(save.error).message}</Alert>}
        {saved && <Alert tone="success">Your details have been updated.</Alert>}

        <div className="flex justify-end">
          <Button type="submit" loading={save.isPending} disabled={!dirty || !fullName.trim()}>
            Save changes
          </Button>
        </div>
      </form>
    </section>
  );
}

/**
 * Changing a password signs every session out, this one included, so a success here ends with the
 * login screen rather than a page whose token the server has already stopped accepting.
 */
function PasswordCard() {
  const navigate = useNavigate();
  const { signOut } = useAuth();

  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');

  const change = useMutation({
    mutationFn: () => changePassword(current, next),
    onSuccess: async () => {
      // The token is already dead server-side; clear it locally rather than letting the next
      // request discover that and bounce through the 401 handler.
      await signOut().catch(() => undefined);
      navigate('/login', {
        replace: true,
        state: { notice: 'Password changed. Sign in again with your new password.' },
      });
    },
  });

  const matches = next === confirm;
  const canSubmit = current.length > 0 && next.length >= 8 && matches && next !== current;

  return (
    <section className="rounded-xl border border-line bg-surface p-6 shadow-card">
      <h2 className="font-semibold text-slate-900">Password</h2>
      <p className="mt-1 text-sm text-slate-500">
        Used after your daily OTP. Changing it signs you out everywhere, including here.
      </p>

      <form
        className="mt-5 space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (canSubmit) change.mutate();
        }}
      >
        <TextField
          label="Current password"
          type="password"
          value={current}
          onChange={(event) => setCurrent(event.target.value)}
          autoComplete="current-password"
        />
        <TextField
          label="New password"
          type="password"
          value={next}
          onChange={(event) => setNext(event.target.value)}
          hint="At least 8 characters"
          autoComplete="new-password"
        />
        <TextField
          label="Confirm new password"
          type="password"
          value={confirm}
          onChange={(event) => setConfirm(event.target.value)}
          error={confirm && !matches ? 'Passwords do not match' : undefined}
          autoComplete="new-password"
        />

        {change.isError && <Alert tone="error">{toApiError(change.error).message}</Alert>}

        <div className="flex justify-end">
          <Button type="submit" loading={change.isPending} disabled={!canSubmit}>
            Change password
          </Button>
        </div>
      </form>
    </section>
  );
}

/**
 * Where the third option lives.
 *
 * <p>The button in the header is a switch — light, dark, done. This is the place to say "match my
 * machine" instead, which is the setting most people actually want and none of them want to find
 * by hunting through a header.
 *
 * <p>It sits above the identity card rather than below the password form: appearance is the one
 * thing on this screen a member can change without an administrator, and burying it under two
 * forms they cannot always use would be a poor trade.
 */
function AppearanceCard() {
  return (
    <section className="rounded-xl border border-line bg-surface p-5 shadow-card">
      <h2 className="font-semibold text-slate-900">Appearance</h2>
      <p className="mt-1 text-sm leading-relaxed text-slate-500">
        System follows your device, and changes with it when it switches at sunset.
      </p>
      <div className="mt-4">
        <ThemeModeChoice />
      </div>
    </section>
  );
}

function Fixed({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-700">{value}</dd>
    </div>
  );
}
