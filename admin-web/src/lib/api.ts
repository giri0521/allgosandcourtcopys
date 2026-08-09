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
 * The one special case is OTP_REQUIRED_TODAY, which the login screen handles
 * itself by switching to the OTP tab.
 */
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const code = error.response?.data?.code;

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
