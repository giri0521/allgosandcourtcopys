import { api } from '@/lib/api';
import type { CurrentUser, Department, UserStatus } from '@/types/api';

export interface Session {
  accessToken: string;
  expiresInSeconds: number;
  user: CurrentUser;
}

export interface RegisterPayload {
  fullName: string;
  mobileNumber: string;
  departmentId: string;
  designation?: string;
  email?: string;
  password: string;
}

export async function fetchDepartments(): Promise<Department[]> {
  const { data } = await api.get<Department[]>('/auth/departments');
  return data;
}

export async function register(
  payload: RegisterPayload,
): Promise<{ userId: string; status: UserStatus; message: string }> {
  const { data } = await api.post('/auth/register', payload);
  return data;
}

export async function sendLoginOtp(mobileNumber: string): Promise<void> {
  await api.post('/auth/otp/send', { mobileNumber });
}

export async function loginWithOtp(mobileNumber: string, otp: string): Promise<Session> {
  const { data } = await api.post<Session>('/auth/otp/verify', { mobileNumber, otp });
  return data;
}

export async function loginWithPassword(mobileNumber: string, password: string): Promise<Session> {
  const { data } = await api.post<Session>('/auth/login', { mobileNumber, password });
  return data;
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}

/**
 * Exchanges the httpOnly refresh cookie for a fresh access token.
 *
 * <p>This is how a session survives a page reload: the access token is deliberately held in memory
 * only, so after a refresh the tab has no token — but it still has the cookie, which script cannot
 * read and therefore cannot leak. The server re-checks status and token version on the way through,
 * so a disabled account cannot restore a session.
 */
export async function restoreSession(): Promise<Session> {
  const { data } = await api.post<Session>('/auth/refresh');
  return data;
}

export async function sendPasswordResetOtp(mobileNumber: string): Promise<void> {
  await api.post('/auth/password/forgot', { mobileNumber });
}

export async function resetPassword(
  mobileNumber: string,
  otp: string,
  newPassword: string,
): Promise<void> {
  await api.post('/auth/password/reset', { mobileNumber, otp, newPassword });
}

/** Confirms the mobile number given at registration. Issues no session: the account is pending. */
export async function verifyRegistrationOtp(mobileNumber: string, otp: string): Promise<void> {
  await api.post('/auth/otp/verify-registration', { mobileNumber, otp });
}
