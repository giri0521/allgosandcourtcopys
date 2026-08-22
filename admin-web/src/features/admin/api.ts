import type { AxiosError } from 'axios';
import { api } from '@/lib/api';
import type {
  ActivityReport,
  AuditEntry,
  Member,
  MemberActivity,
  MemberCounts,
  PageResponse,
  RegistrationRequest,
  RegistrationStatus,
  Role,
  SystemStats,
  UserStatus,
} from '@/types/api';

/** "all" clears the filter; the server reads it as no filter rather than a status. */
export type RequestFilter = RegistrationStatus | 'all';
export type MemberFilter = UserStatus | 'all';

export async function fetchRegistrationRequests(
  status: RequestFilter,
  page = 0,
): Promise<PageResponse<RegistrationRequest>> {
  const { data } = await api.get<PageResponse<RegistrationRequest>>('/admin/registration-requests', {
    params: { status: status.toLowerCase(), page },
  });
  return data;
}

export async function approveRequest(id: string): Promise<RegistrationRequest> {
  const { data } = await api.post<RegistrationRequest>(`/admin/registration-requests/${id}/approve`);
  return data;
}

export async function rejectRequest(id: string, reason: string): Promise<RegistrationRequest> {
  const { data } = await api.post<RegistrationRequest>(
    `/admin/registration-requests/${id}/reject`,
    { reason },
  );
  return data;
}

export async function fetchMembers(
  status: MemberFilter,
  query: string,
  page = 0,
): Promise<PageResponse<Member>> {
  const { data } = await api.get<PageResponse<Member>>('/admin/members', {
    params: { status: status.toLowerCase(), q: query || undefined, page },
  });
  return data;
}

export async function fetchMemberCounts(): Promise<MemberCounts> {
  const { data } = await api.get<MemberCounts>('/admin/members/summary');
  return data;
}

/** Enable or disable an account. The server refuses anything other than these two. */
export async function changeMemberStatus(
  id: string,
  status: 'ACTIVE' | 'INACTIVE',
): Promise<Member> {
  const { data } = await api.patch<Member>(`/admin/members/${id}/status`, { status });
  return data;
}

/**
 * Grant or withdraw administrative access.
 *
 * <p>The server signs the member out as part of this, so a promotion shows up for them on their
 * next sign-in rather than immediately.
 */
export async function changeMemberRole(id: string, role: Role): Promise<Member> {
  const { data } = await api.patch<Member>(`/admin/members/${id}/role`, { role });
  return data;
}

// ------------------------------------------------------------------------- monitoring

export async function fetchSystemStats(): Promise<SystemStats> {
  const { data } = await api.get<SystemStats>('/admin/stats');
  return data;
}

/** The dashboard's activity feed: the whole trail, newest first. */
export async function fetchRecentActivity(size = 10): Promise<PageResponse<AuditEntry>> {
  const { data } = await api.get<PageResponse<AuditEntry>>('/admin/activity', { params: { size } });
  return data;
}

export async function fetchMemberActivity(
  memberId: string,
  page = 0,
): Promise<MemberActivity> {
  const { data } = await api.get<MemberActivity>(`/admin/members/${memberId}/activity`, {
    params: { page },
  });
  return data;
}

export interface AuditFilters {
  actorId?: string;
  action?: string;
  /** Plain dates (YYYY-MM-DD). The server resolves them in Asia/Kolkata and treats both as inclusive. */
  from?: string;
  to?: string;
}

export async function fetchAuditLogs(
  filters: AuditFilters,
  page = 0,
): Promise<PageResponse<AuditEntry>> {
  const { data } = await api.get<PageResponse<AuditEntry>>('/admin/audit-logs', {
    params: {
      actorId: filters.actorId || undefined,
      action: filters.action || undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
      page,
    },
  });
  return data;
}

/** The action names the filter offers, read from the server's own constants. */
export async function fetchAuditActions(): Promise<string[]> {
  const { data } = await api.get<string[]>('/admin/audit-logs/actions');
  return data;
}

// ---------------------------------------------------------------------------- reports

export type ReportRange = { from?: string; to?: string };

export async function fetchReport(range: ReportRange): Promise<ActivityReport> {
  const { data } = await api.get<ActivityReport>('/admin/reports', {
    params: { from: range.from || undefined, to: range.to || undefined },
  });
  return data;
}

export type ReportType = 'departments' | 'uploaders' | 'monthly';

/**
 * Downloads a report as CSV.
 *
 * <p>Fetched rather than linked: the endpoint needs the Authorization header, which a plain anchor
 * cannot send. The response is turned into an object URL and clicked, then revoked — leaving it
 * alive would pin the whole file in memory for the life of the tab.
 *
 * <p>`responseType: 'blob'` applies to the failures too, which is the trap: a 500 arrives as a Blob
 * containing the JSON error rather than as an object, so every reader of it — `toApiError`, and the
 * interceptor that checks `data.code` — sees nothing and reports a network problem instead of what
 * the server actually said. The one path where a failure is invisible should not be the one you are
 * standing on when a download silently does nothing, so the body is unwrapped here.
 */
export async function downloadReportCsv(type: ReportType, range: ReportRange): Promise<void> {
  let response;
  try {
    response = await api.get(`/admin/reports/export`, {
      params: { type, from: range.from || undefined, to: range.to || undefined },
      responseType: 'blob',
    });
  } catch (caught) {
    throw await withReadableErrorBody(caught);
  }

  const filename =
    filenameFromDisposition(response.headers['content-disposition']) ?? `${type}.csv`;

  const url = URL.createObjectURL(response.data as Blob);
  try {
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}

/**
 * Reads a blob error body back into the shape the rest of the client expects.
 *
 * <p>Returns the error untouched if there is nothing to unwrap — a network failure with no
 * response, or a body that is not the JSON envelope — so the caller's own handling still applies.
 */
export async function withReadableErrorBody(caught: unknown): Promise<unknown> {
  const response = (caught as AxiosError)?.response;
  if (!(response?.data instanceof Blob)) return caught;

  try {
    const parsed: unknown = JSON.parse(await response.data.text());
    if (parsed && typeof parsed === 'object' && 'code' in parsed) {
      response.data = parsed;
    }
  } catch {
    // Not JSON — an HTML error page from a proxy, or an empty body. Leave it as it was; the
    // caller falls back to its own wording, which is better than throwing from an error handler.
  }
  return caught;
}

/** The server names the file; this only reads that name back off the header. */
function filenameFromDisposition(header: unknown): string | null {
  if (typeof header !== 'string') return null;
  const match = /filename="?([^"]+)"?/.exec(header);
  return match ? match[1] : null;
}
