import { api } from '@/lib/api';
import type { Letter, LetterSummary, LetterTemplate, PageResponse } from '@/types/api';

/** What a letter carries when it is saved. Everything the print view needs, and nothing else. */
export interface LetterDraft {
  templateId: string | null;
  referenceNo: string;
  letterDate: string;
  fromBlock: string;
  toBlock: string;
  salutation: string;
  subject: string;
  reference: string;
  body: string;
  enclosure: string;
  copyTo: string;
  signOff: string;
}

/** The templates on offer. Retired ones are not among them. */
export async function fetchTemplates(): Promise<LetterTemplate[]> {
  const { data } = await api.get<LetterTemplate[]>('/letters/templates');
  return data;
}

export async function fetchMyLetters(page = 0): Promise<PageResponse<LetterSummary>> {
  const { data } = await api.get<PageResponse<LetterSummary>>('/letters', { params: { page } });
  return data;
}

export async function fetchLetter(id: string): Promise<Letter> {
  const { data } = await api.get<Letter>(`/letters/${id}`);
  return data;
}

export async function createLetter(draft: LetterDraft): Promise<Letter> {
  const { data } = await api.post<Letter>('/letters', draft);
  return data;
}

export async function updateLetter(id: string, draft: LetterDraft): Promise<Letter> {
  const { data } = await api.put<Letter>(`/letters/${id}`, draft);
  return data;
}

export async function deleteLetter(id: string): Promise<void> {
  await api.delete(`/letters/${id}`);
}

// ------------------------------------------------------------------ templates, for admins

/** Includes retired templates, which is the only place they can be seen and brought back. */
export async function fetchAllTemplates(): Promise<LetterTemplate[]> {
  const { data } = await api.get<LetterTemplate[]>('/admin/letter-templates');
  return data;
}

export interface TemplateDraft {
  name: string;
  description: string;
  defaultSubject: string;
  body: string;
  salutation: string;
  active: boolean;
}

export async function createTemplate(draft: TemplateDraft): Promise<LetterTemplate> {
  const { data } = await api.post<LetterTemplate>('/admin/letter-templates', draft);
  return data;
}

export async function updateTemplate(id: string, draft: TemplateDraft): Promise<LetterTemplate> {
  const { data } = await api.put<LetterTemplate>(`/admin/letter-templates/${id}`, draft);
  return data;
}

/**
 * Removes a template nothing has been written from; retires one that has.
 *
 * @returns whether it was removed outright, so the screen can say which happened rather than
 *   claiming a deletion that did not occur
 */
export async function deleteTemplate(id: string): Promise<boolean> {
  const { data } = await api.delete<{ removed: boolean }>(`/admin/letter-templates/${id}`);
  return data.removed;
}
