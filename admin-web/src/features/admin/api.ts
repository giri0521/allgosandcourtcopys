import { api } from '@/lib/api';
import type {
  Member,
  MemberCounts,
  PageResponse,
  RegistrationRequest,
  RegistrationStatus,
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
