import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/Button';
import { AuthContext } from '@/lib/auth-context';
import type { CurrentUser } from '@/types/api';
import '@/index.css';

/**
 * A throwaway page for looking at the application frame without a backend.
 *
 * <p>The header is the piece most likely to break at 360px — logo, five nav entries, a search box,
 * the bell, a name and a sign-out button all compete for one row — and it is the piece no unit test
 * can judge. This renders it as an admin (the worst case, with the most nav entries) so it can be
 * screenshotted at each breakpoint.
 *
 * <p>Not part of the application: nothing routes here, and Vite only builds index.html, so it never
 * reaches production.
 */
const ADMIN: CurrentUser = {
  id: '00000000-0000-0000-0000-000000000001',
  fullName: 'System Administrator',
  mobileNumber: '9999999999',
  email: null,
  role: 'ADMIN',
  status: 'ACTIVE',
  departmentId: null,
  departmentName: 'Department of Information Technology and Digital Services',
  designation: 'Administrator',
  lastLoginAt: null,
  createdAt: '2026-01-01T00:00:00Z',
};

// Retries off and no refetching: the bell's count request will fail with no server, and it should
// fail quietly exactly as it does in the real app.
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/home']}>
        <AuthContext.Provider
          value={{ user: ADMIN, restoring: false, signIn: () => {}, signOut: async () => {} }}
        >
          <AppShell
            title="Welcome, System Administrator"
            subtitle="Administrator · Department of Information Technology and Digital Services"
            actions={<Button variant="secondary">An action</Button>}
          >
            <div className="rounded-xl border border-line bg-surface p-6 shadow-card">
              <h2 className="font-semibold text-slate-900">Page content</h2>
              <p className="mt-1 text-sm text-slate-600">
                Stand-in for a screen. What matters here is the frame around it.
              </p>
            </div>
          </AppShell>
        </AuthContext.Provider>
      </MemoryRouter>
    </QueryClientProvider>
  </StrictMode>,
);
