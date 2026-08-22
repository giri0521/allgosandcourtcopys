import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SelectField, TextField } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { fetchDepartments } from '@/features/auth/api';
import { createContact, updateContact } from '@/features/phonebook/api';
import type { SaveContactPayload } from '@/features/phonebook/api';
import { toApiError } from '@/lib/errors';
import type { PhonebookContact, PhonebookKind, PhonebookRole } from '@/types/api';

interface Props {
  open: boolean;
  /** Which book is being added to, and the book an edit starts in. */
  kind: PhonebookKind;
  /** The contact being corrected, or null when adding a new one. */
  editing: PhonebookContact | null;
  onClose: () => void;
}

/**
 * Adding or correcting one number.
 *
 * <p>One dialog for both books: the fields differ by two, and two dialogs would be the same form
 * maintained twice. Which book it belongs to is a field like any other, so a contact filed under
 * the wrong one is corrected here rather than deleted and typed again.
 */
export function ContactDialog({ open, kind, editing, onClose }: Props) {
  return (
    <Modal
      open={open}
      title={editing ? 'Edit contact' : 'Add contact'}
      description="Everyone signed in can read the phonebook; only administrators change it."
      onClose={onClose}
    >
      {/*
        Keyed, so opening the dialog on a different contact gives a fresh form rather than the last
        one's details. The alternative — syncing props into state from an effect — renders the wrong
        contact for a frame and is the bug this pattern exists to avoid.
      */}
      <ContactForm key={editing?.id ?? `new-${kind}`} kind={kind} editing={editing} onClose={onClose} />
    </Modal>
  );
}

function ContactForm({
  kind,
  editing,
  onClose,
}: {
  kind: PhonebookKind;
  editing: PhonebookContact | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();

  const [book, setBook] = useState<PhonebookKind>(
    editing ? (editing.taluk ? 'TALUK' : 'DEPARTMENT') : kind,
  );
  const [form, setForm] = useState({
    fullName: editing?.fullName ?? '',
    designation: editing?.designation ?? '',
    phoneNumber: editing?.phoneNumber ?? '',
    alternatePhone: editing?.alternatePhone ?? '',
    email: editing?.email ?? '',
    district: editing?.district ?? '',
    departmentId: editing?.departmentId ?? '',
    taluk: editing?.taluk ?? '',
    role: (editing?.role ?? 'TAHSILDAR') as PhonebookRole,
  });

  const departments = useQuery({ queryKey: ['departments', 'all'], queryFn: fetchDepartments });

  const update = (key: keyof typeof form) => (value: string) =>
    setForm((current) => ({ ...current, [key]: value }));

  const save = useMutation({
    mutationFn: () => {
      // Only the fields this book uses are sent: the server refuses a contact carrying both, and
      // the database has the same rule as a constraint.
      const payload: SaveContactPayload = {
        kind: book,
        fullName: form.fullName.trim(),
        designation: form.designation.trim() || undefined,
        phoneNumber: form.phoneNumber.trim(),
        alternatePhone: form.alternatePhone.trim() || undefined,
        email: form.email.trim() || undefined,
        district: form.district.trim() || undefined,
        ...(book === 'DEPARTMENT'
          ? { departmentId: form.departmentId }
          : { taluk: form.taluk.trim(), role: form.role }),
      };

      return editing ? updateContact(editing.id, payload) : createContact(payload);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['phonebook'] });
      onClose();
    },
  });

  const complete =
    form.fullName.trim().length > 0 &&
    form.phoneNumber.trim().length > 0 &&
    (book === 'DEPARTMENT' ? form.departmentId.length > 0 : form.taluk.trim().length > 0);

  const error = save.error ? toApiError(save.error) : null;

  return (
    <div className="space-y-4">
      {error && <Alert tone="error">{error.message}</Alert>}

      <SelectField
        label="Book"
        value={book}
        onChange={(event) => setBook(event.target.value as PhonebookKind)}
        hint="Which listing this number appears in"
      >
        <option value="DEPARTMENT">Department numbers</option>
        <option value="TALUK">Tahsildars, by taluk</option>
      </SelectField>

      <TextField
        label="Name"
        value={form.fullName}
        onChange={(event) => update('fullName')(event.target.value)}
        error={error?.fieldErrors?.fullName}
      />

      {book === 'DEPARTMENT' ? (
        <>
          <SelectField
            label="Department"
            value={form.departmentId}
            onChange={(event) => update('departmentId')(event.target.value)}
            error={error?.fieldErrors?.departmentId}
          >
            <option value="">Select a department</option>
            {(departments.data ?? []).map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
              </option>
            ))}
          </SelectField>
          <TextField
            label="Designation"
            value={form.designation}
            onChange={(event) => update('designation')(event.target.value)}
            placeholder="e.g. Joint Director"
          />
        </>
      ) : (
        <>
          <TextField
            label="Taluk"
            value={form.taluk}
            onChange={(event) => update('taluk')(event.target.value)}
            hint="Typed as it should appear; a new taluk needs no setting up"
            error={error?.fieldErrors?.taluk}
          />
          <SelectField
            label="Role"
            value={form.role}
            onChange={(event) => update('role')(event.target.value)}
          >
            <option value="TAHSILDAR">Tahsildar</option>
            <option value="GROUP_MEMBER">Group member</option>
          </SelectField>
        </>
      )}

      <TextField
        label="Phone number"
        value={form.phoneNumber}
        onChange={(event) => update('phoneNumber')(event.target.value)}
        hint="An office landline or a mobile — 044-2345 6789, 98765 43210"
        error={error?.fieldErrors?.phoneNumber}
      />

      <TextField
        label="Alternate number (optional)"
        value={form.alternatePhone}
        onChange={(event) => update('alternatePhone')(event.target.value)}
        error={error?.fieldErrors?.alternatePhone}
      />

      <TextField
        label="District (optional)"
        value={form.district}
        onChange={(event) => update('district')(event.target.value)}
        hint="Where they sit — the same post in two districts is two people"
        error={error?.fieldErrors?.district}
      />

      <TextField
        label="Email (optional)"
        type="email"
        value={form.email}
        onChange={(event) => update('email')(event.target.value)}
        error={error?.fieldErrors?.email}
      />

      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button loading={save.isPending} disabled={!complete} onClick={() => save.mutate()}>
          {editing ? 'Save changes' : 'Add contact'}
        </Button>
      </div>
    </div>
  );
}
