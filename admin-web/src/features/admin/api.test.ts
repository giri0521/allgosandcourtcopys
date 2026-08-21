import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { withReadableErrorBody } from '@/features/admin/api';

/**
 * The CSV export asks axios for a Blob, and that applies to the failures too — a 500 arrives as a
 * Blob holding the JSON error rather than as an object. Everything downstream reads `data.code`,
 * finds nothing, and reports a network problem, so the one request whose failure is invisible is a
 * download that appears to do nothing at all. This is the unwrapping that fixes that.
 */
function blobError(body: string, status = 500): AxiosError {
  const error = new AxiosError('Request failed');
  error.response = {
    status,
    statusText: '',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data: new Blob([body], { type: 'application/json' }),
  };
  return error;
}

describe('withReadableErrorBody', () => {
  it('reads the server envelope out of a blob body', async () => {
    const unwrapped = (await withReadableErrorBody(
      blobError('{"code":"REPORT_UNKNOWN","message":"Unknown report: monthy"}'),
    )) as AxiosError<{ code: string; message: string }>;

    expect(unwrapped.response?.data.code).toBe('REPORT_UNKNOWN');
    expect(unwrapped.response?.data.message).toBe('Unknown report: monthy');
  });

  it('leaves a body it cannot parse alone rather than throwing from an error path', async () => {
    const html = blobError('<html><body>502 Bad Gateway</body></html>');
    const unwrapped = (await withReadableErrorBody(html)) as AxiosError;

    expect(unwrapped).toBe(html);
    expect(unwrapped.response?.data).toBeInstanceOf(Blob);
  });

  it('passes through an error with no response at all — a connection that never landed', async () => {
    const offline = new AxiosError('Network Error');
    expect(await withReadableErrorBody(offline)).toBe(offline);
    expect(await withReadableErrorBody(new Error('something else'))).toBeInstanceOf(Error);
  });
});
