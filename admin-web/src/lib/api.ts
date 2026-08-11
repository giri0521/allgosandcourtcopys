import axios from 'axios';

export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true, // refresh token lives in an httpOnly cookie
});

let accessToken: string | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

/**
 * Server-side authorization is the source of truth, so the UI reacts to status
 * codes rather than trying to predict them:
 *   401 → session gone, back to login
 *   403 → Access Restricted screen
 * Two calls opt out. OTP_REQUIRED_TODAY is handled by the login screen itself,
 * which switches to the OTP tab; and the start-up /auth/refresh legitimately
 * 401s for anyone who is simply not signed in, so it must not bounce a visitor
 * off the register or forgot-password screen.
 */
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const code = error.response?.data?.code;
    const isSessionRestore = error.config?.url?.endsWith('/auth/refresh');

    if (isSessionRestore) {
      return Promise.reject(error);
    }

    if (status === 401) {
      setAccessToken(null);
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login');
      }
    } else if (status === 403 && code !== 'OTP_REQUIRED_TODAY') {
      window.location.assign('/restricted');
    }

    return Promise.reject(error);
  },
);
