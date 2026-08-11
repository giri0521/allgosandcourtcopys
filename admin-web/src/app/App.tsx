import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { MembersPage } from '@/features/admin/members/MembersPage';
import { RegistrationRequestsPage } from '@/features/admin/registrations/RegistrationRequestsPage';
import { AccessRestrictedPage } from '@/features/auth/AccessRestrictedPage';
import { ForgotPasswordPage } from '@/features/auth/ForgotPasswordPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { PendingApprovalPage } from '@/features/auth/PendingApprovalPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { SignedInPage } from '@/features/auth/SignedInPage';
import { AuthProvider } from '@/lib/AuthProvider';
import { RequireAdmin, RequireAuth } from '@/lib/RouteGuards';

/**
 * Route skeleton for the screens in docs/IMPLEMENTATION_PLAN.md.
 * Screens are filled in phase by phase; every element below is a placeholder
 * until its feature module lands.
 *
 * The guards keep members off admin URLs, but they are a courtesy only —
 * every admin endpoint enforces the same rule server-side.
 */
/**
 * Server state lives here rather than in per-screen effects, so a list refreshes itself after an
 * approval instead of each screen re-implementing the same load-and-reload dance.
 *
 * <p>Retries are off: the API's failures are decisions — 401, 403, 409 — and repeating a rejected
 * request neither helps the user nor changes the answer.
 */
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
    <BrowserRouter>
      <AuthProvider>
      <Routes>
        {/* public */}
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/register/pending" element={<PendingApprovalPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/restricted" element={<AccessRestrictedPage />} />

        {/* any active user */}
        <Route element={<RequireAuth />}>
          <Route path="/home" element={<SignedInPage />} />
          <Route path="/departments" element={<Placeholder name="Department List" />} />
          <Route path="/departments/:departmentId" element={<Placeholder name="Folders" />} />
          <Route path="/folders/:folderId" element={<Placeholder name="Folder Contents" />} />
          <Route path="/files/:fileId" element={<Placeholder name="File Preview" />} />
          <Route path="/upload" element={<Placeholder name="Upload" />} />
          <Route path="/my-uploads" element={<Placeholder name="My Uploads" />} />
          <Route path="/downloads" element={<Placeholder name="Downloads" />} />
          <Route path="/favorites" element={<Placeholder name="Favorites" />} />
          <Route path="/search" element={<Placeholder name="Search Results" />} />
          <Route path="/notifications" element={<Placeholder name="Notifications" />} />
          <Route path="/profile" element={<Placeholder name="My Profile" />} />
        </Route>

        {/* admin */}
        <Route element={<RequireAdmin />}>
          <Route path="/admin" element={<Navigate to="/admin/requests" replace />} />
          <Route path="/admin/requests" element={<RegistrationRequestsPage />} />
          <Route path="/admin/members" element={<MembersPage />} />
          <Route path="/admin/members/:memberId" element={<Placeholder name="Member Activity" />} />
          <Route path="/admin/deletions" element={<Placeholder name="Deletions Log" />} />
          <Route path="/admin/departments" element={<Placeholder name="Department Management" />} />
          <Route path="/admin/folders" element={<Placeholder name="Folder Management" />} />
          <Route path="/admin/files" element={<Placeholder name="File Management" />} />
          <Route path="/admin/reports" element={<Placeholder name="Reports" />} />
          <Route path="/admin/logs" element={<Placeholder name="Activity Logs" />} />
          <Route path="/admin/settings" element={<Placeholder name="Settings" />} />
        </Route>

        {/* static */}
        <Route path="/help" element={<Placeholder name="Help & Support" />} />
        <Route path="/about" element={<Placeholder name="About" />} />
        <Route path="/privacy" element={<Placeholder name="Privacy Policy" />} />

        <Route path="*" element={<Placeholder name="Not Found" />} />
      </Routes>
      </AuthProvider>
    </BrowserRouter>
    </QueryClientProvider>
  );
}

function Placeholder({ name }: { name: string }) {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <div className="rounded-lg border border-slate-200 bg-white px-8 py-6 text-center shadow-sm">
        <p className="text-sm uppercase tracking-wide text-navy-500">ALLGOSANDCOURTCOPYS</p>
        <h1 className="mt-2 text-2xl font-semibold text-navy-800">{name}</h1>
        <p className="mt-2 text-sm text-slate-500">Not implemented yet.</p>
      </div>
    </main>
  );
}
