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
