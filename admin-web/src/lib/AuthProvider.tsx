import { useCallback, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { setAccessToken } from '@/lib/api';
import { AuthContext } from '@/lib/auth-context';
import type { Session } from '@/features/auth/api';
import { logout as logoutRequest } from '@/features/auth/api';
import type { CurrentUser } from '@/types/api';

/**
 * Holds the session for the running tab.
 *
 * <p>The access token is kept in memory only — never localStorage — so a cross-site scripting bug
 * cannot read it. Continuity across a page reload comes from the httpOnly refresh cookie instead.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);

  const signIn = useCallback((session: Session) => {
    setAccessToken(session.accessToken);
    setUser(session.user);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await logoutRequest();
    } finally {
      setAccessToken(null);
      setUser(null);
    }
  }, []);

  const value = useMemo(() => ({ user, signIn, signOut }), [user, signIn, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
