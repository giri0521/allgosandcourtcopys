import { api } from '@/lib/api';
import type { CurrentUser } from '@/types/api';

/**
 * The signed-in user's own account.
 *
 * <p>None of these takes a user id: the server acts on the authenticated principal, so there is no
 * arrangement of arguments that edits somebody else.
 */

export async function fetchMe(): Promise<CurrentUser> {
  const { data } = await api.get<CurrentUser>('/me');
  return data;
}

export interface ProfileUpdate {
  fullName: string;
  email?: string;
  designation?: string;
  /** Fills the From block on every letter; editable there per letter. */
  officeAddress?: string;
}

/**
 * The three fields a member may correct.
 *
 * <p>Mobile number, department, role and status are deliberately absent — the number is the login
 * identity, and the rest are an administrator's decision.
 */
export async function updateProfile(update: ProfileUpdate): Promise<CurrentUser> {
  const { data } = await api.patch<CurrentUser>('/me', update);
  return data;
}

/**
 * Changes the password and ends every session, including this one.
 *
 * <p>The caller's token stops working the moment this returns, so the UI must sign out rather than
 * carry on with a token the server will now refuse.
 */
export async function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  await api.post('/me/password', { currentPassword, newPassword });
}
