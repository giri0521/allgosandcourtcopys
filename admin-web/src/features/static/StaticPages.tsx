import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { useAuth } from '@/lib/auth-context';

/**
 * Help, About and Privacy.
 *
 * <p>They share a layout because they share a job: short, factual pages an office worker reads once
 * and an auditor reads properly. Each is reachable while signed out too, so the privacy notice is
 * readable before anyone registers — which is the only moment it matters.
 */
function StaticPage({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  const { user } = useAuth();

  const body = (
    <article className="prose-none mx-auto max-w-3xl space-y-6 rounded-xl border border-line bg-surface p-6 shadow-card sm:p-8">
      {children}
    </article>
  );

  // Signed out, there is no navigation to sit inside — the shell would render a header full of
  // links to screens the reader cannot open.
  if (!user) {
    return (
      <main className="min-h-screen px-4 py-10">
        <div className="mx-auto max-w-3xl">
          <div className="mb-6 text-center">
            <img
              src="/logo.webp"
              alt=""
              width={64}
              height={64}
              className="mx-auto mb-3 h-16 w-16 rounded-full object-cover"
            />
            <h1 className="text-2xl font-semibold tracking-tight text-navy-800">{title}</h1>
            <p className="mt-1 text-sm text-slate-600">{subtitle}</p>
          </div>
          {body}
          <p className="mt-6 text-center text-sm">
            <Link to="/login" className="font-semibold text-navy-600 hover:underline">
              Back to sign in
            </Link>
          </p>
        </div>
      </main>
    );
  }

  return (
    <AppShell title={title} subtitle={subtitle}>
      {body}
    </AppShell>
  );
}

function Section({ heading, children }: { heading: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="font-semibold text-slate-900">{heading}</h2>
      <div className="mt-2 space-y-2 text-sm leading-relaxed text-slate-600">{children}</div>
    </section>
  );
}

export function HelpPage() {
  return (
    <StaticPage title="Help & Support" subtitle="How the system works, and who to ask.">
      <Section heading="Signing in">
        <p>
          Your mobile number is your identity, and your password is what signs you in. Too many
          wrong attempts locks the account for a while — that limit applies to everyone.
        </p>
        <p>
          If you have forgotten your password, use <strong>Forgot password</strong> on the sign-in
          screen. It verifies you by OTP and signs you out of every other session.
        </p>
      </Section>

      <Section heading="Getting access">
        <p>
          Registering does not grant access. An administrator reviews every request, and your
          account cannot be used until it is approved. You are notified when that happens.
        </p>
      </Section>

      <Section heading="Working with documents">
        <p>
          Once approved, you may read, download and upload in <strong>any</strong> department, not
          only your own. Browse from <Link to="/departments" className="font-medium text-navy-600 hover:underline">Departments</Link>,
          or search by part of a document&apos;s name from the box in the header.
        </p>
        <p>
          You may replace or delete a document <em>you</em> uploaded; an administrator may do so for
          any. Deleting always asks for a reason, and that reason is sent to every administrator.
          Nothing is destroyed — an administrator can restore a deleted document.
        </p>
      </Section>

      <Section heading="Something is wrong">
        <p>
          Contact your administrator. They can approve registrations, re-enable a disabled account,
          restore a deleted document, and see the full record of what happened.
        </p>
      </Section>
    </StaticPage>
  );
}

export function AboutPage() {
  return (
    <StaticPage title="About" subtitle="ALLGOSANDCOURTCOPYS — Document Management System">
      <Section heading="What this is">
        <p>
          A central store for the orders and instructions this office works from: Government Orders,
          Court Orders, Circulars, Contracts, and Acts &amp; Rules — filed by department, searchable,
          and available to every approved member.
        </p>
      </Section>

      <Section heading="How access works">
        <p>
          Approval by an administrator is the only gate. Once past it, every member has the same
          rights over documents; there are no per-folder permissions to maintain and no way for a
          document to be visible to one approved member and not another.
        </p>
      </Section>

      <Section heading="What is recorded">
        <p>
          Every sign-in, upload, preview, download, replacement, deletion and restoration is written
          to an activity log that cannot be edited from within the application. Administrators can
          read it; nobody can alter it.
        </p>
      </Section>
    </StaticPage>
  );
}

export function PrivacyPage() {
  return (
    <StaticPage title="Privacy" subtitle="What this system holds about you, and why.">
      <Section heading="What is held">
        <p>
          Your name, mobile number, department, designation and — if you supply one — your email
          address. Your password is stored only as a cryptographic hash and cannot be read back by
          anyone, including an administrator.
        </p>
      </Section>

      <Section heading="What is recorded about your use">
        <p>
          The activity log records what you did, when, and the network address the request came
          from. That includes the documents you uploaded, previewed, downloaded, replaced or
          deleted, and every sign-in and failed sign-in attempt.
        </p>
        <p>
          This exists so that the office can answer who released a document and when — the same
          question a paper register answered. It is visible to administrators.
        </p>
      </Section>

      <Section heading="One-time passwords">
        <p>
          Codes are sent by SMS to your registered number. They are stored only as a hash, expire
          within minutes, may be used once, and are never written to a log in readable form.
        </p>
      </Section>

      <Section heading="Documents">
        <p>
          Files are held in encrypted storage that is never publicly readable. A document is fetched
          through a signed link that expires within minutes, so a copied link stops working almost
          immediately.
        </p>
        <p>
          Deleting a document hides it and records who removed it and why; the file itself is
          retained so that an administrator can restore it.
        </p>
      </Section>

      <Section heading="Correcting your details">
        <p>
          You can change your name, email and designation yourself on{' '}
          <Link to="/profile" className="font-medium text-navy-600 hover:underline">My Profile</Link>.
          Your mobile number and department are changed by an administrator, because they decide
          identity and are not yours to reassign.
        </p>
      </Section>
    </StaticPage>
  );
}
