import { api } from '@/lib/api';
import type {
  DepartmentContacts,
  PhonebookContact,
  PhonebookKind,
  PhonebookRole,
  TalukContacts,
} from '@/types/api';

export async function fetchDepartmentBook(): Promise<DepartmentContacts[]> {
  const { data } = await api.get<DepartmentContacts[]>('/phonebook/departments');
  return data;
}

export async function fetchTalukBook(): Promise<TalukContacts[]> {
  const { data } = await api.get<TalukContacts[]>('/phonebook/taluks');
  return data;
}

/**
 * One contact, for either book.
 *
 * <p>Sent with the fields the other book does not use omitted rather than blank: the server checks
 * the pairing between `kind` and what accompanies it, and an empty string is not nothing.
 */
export interface SaveContactPayload {
  kind: PhonebookKind;
  fullName: string;
  designation?: string;
  phoneNumber: string;
  alternatePhone?: string;
  email?: string;
  departmentId?: string;
  taluk?: string;
  role?: PhonebookRole;
}

export async function createContact(payload: SaveContactPayload): Promise<PhonebookContact> {
  const { data } = await api.post<PhonebookContact>('/admin/phonebook', payload);
  return data;
}

export async function updateContact(
  id: string,
  payload: SaveContactPayload,
): Promise<PhonebookContact> {
  const { data } = await api.put<PhonebookContact>(`/admin/phonebook/${id}`, payload);
  return data;
}

export async function deleteContact(id: string): Promise<void> {
  await api.delete(`/admin/phonebook/${id}`);
}
