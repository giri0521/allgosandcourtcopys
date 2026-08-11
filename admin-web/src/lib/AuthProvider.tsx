import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { setAccessToken } from '@/lib/api';
import { AuthContext } from '@/lib/auth-context';
import type { Session } from '@/features/auth/api';
import { logout as logoutRequest, restoreSession } from '@/features/auth/api';
import type { CurrentUser } from '@/types/api';

/**
 * Holds the session for the running tab.
 *
 * <p>The access token is kept in memory only — never localStorage — so a cross-site scripting bug
 * cannot read it. Continuity across a page reload comes from the httpOnly refresh cookie instead:
 * on mount we try to exchange it for a new access token, and only then are the guarded routes
 * allowed to decide anything. Rendering them before that answer arrives would sign the user out on
 * every refresh, which is exactly what {@code restoring} exists to prevent.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [restoring, setRestoring] = useState(true);

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

  useEffect(() => {
    let cancelled = false;

    restoreSession()
      .then((session) => {
        if (!cancelled) {
          signIn(session);
        }
      })
      // No cookie, an expired one, or an account that has since been disabled: all of them simply
      // mean "not signed in", which the guards handle from here.
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) {
          setRestoring(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [signIn]);

  const value = useMemo(
    () => ({ user, restoring, signIn, signOut }),
    [user, restoring, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
