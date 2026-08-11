import { createContext, useContext } from 'react';
import type { Session } from '@/features/auth/api';
import type { CurrentUser } from '@/types/api';

export interface AuthState {
  user: CurrentUser | null;
  /** True until the start-up refresh has answered; guards must wait rather than assume signed out. */
  restoring: boolean;
  signIn: (session: Session) => void;
  signOut: () => Promise<void>;
}

export const AuthContext = createContext<AuthState | null>(null);

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
