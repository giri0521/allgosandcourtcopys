import { AxiosError } from 'axios';
import type { ApiError } from '@/types/api';

/**
 * Pulls the server's error out of a failed request.
 *
 * <p>The server decides both the code the UI switches on and the wording shown to the user, so the
 * client never invents its own message for a failure it did not detect itself.
 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof AxiosError && error.response?.data?.code) {
    return error.response.data as ApiError;
  }
  return {
    code: 'NETWORK_ERROR',
    message: 'Could not reach the server. Check your connection and try again.',
  };
}
