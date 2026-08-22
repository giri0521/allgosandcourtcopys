import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { SkeletonRows } from '@/components/ui/Skeleton';
import { ContactDialog } from '@/features/phonebook/ContactDialog';
import { deleteContact, fetchDepartmentBook, fetchTalukBook } from '@/features/phonebook/api';
import { useAuth } from '@/lib/auth-context';
import { toApiError } from '@/lib/errors';
import type { PhonebookContact, PhonebookKind } from '@/types/api';

/**
 * The office phonebook: numbers by department, and tahsildars by taluk.
 *
 * <p>Nobody in here needs an account — most of these people work in an office down the road, which
 * is the whole reason the book exists and why it is not derived from the user table.
 *
 * <p>Every approved user reads it; only an administrator changes it. The Add and Edit controls are
 * hidden from members for tidiness, and the server refuses them regardless.
 */
export function PhonebookPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const queryClient = useQueryClient();

  const [book, setBook] = useState<PhonebookKind>('DEPARTMENT');
  const [query, setQuery] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<PhonebookContact | null>(null);

  const departments = useQuery({
    queryKey: ['phonebook', 'departments'],
    queryFn: fetchDepartmentBook,
  });

  const taluks = useQuery({ queryKey: ['phonebook', 'taluks'], queryFn: fetchTalukBook });

  const remove = useMutation({
    mutationFn: (contact: PhonebookContact) => deleteContact(contact.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['phonebook'] }),
  });

  const term = query.trim().toLowerCase();

  /** Matches a person by anything printed beside them — a name, a role, or the number itself. */
  const matches = (contact: PhonebookContact) =>
    !term ||
    [contact.fullName, contact.designation, contact.phoneNumber, contact.alternatePhone, contact.email]
      .some((field) => field?.toLowerCase().includes(term));

  const departmentGroups = useMemo(
    () =>
      (departments.data ?? [])
        .map((group) => ({ ...group, contacts: group.contacts.filter(matches) }))
        // A department whose every contact was filtered out is not an empty department; it is one
        // that does not answer the search, and showing its heading alone would suggest otherwise.
        .filter((group) => group.contacts.length > 0),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [departments.data, term],
  );

  const talukGroups = useMemo(
    () =>
      (taluks.data ?? [])
        .map((group) => ({
          ...group,
          tahsildars: group.tahsildars.filter(matches),
          groupMembers: group.groupMembers.filter(matches),
        }))
        .filter((group) => group.tahsildars.length + group.groupMembers.length > 0),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [taluks.data, term],
  );

  const loading = book === 'DEPARTMENT' ? departments.isLoading : taluks.isLoading;
  const error = departments.error ?? taluks.error ?? remove.error;
  const empty =
    book === 'DEPARTMENT' ? departmentGroups.length === 0 : talukGroups.length === 0;

  const openAdd = () => {
    setEditing(null);
    setDialogOpen(true);
  };

  const openEdit = (contact: PhonebookContact) => {
    setEditing(contact);
    setDialogOpen(true);
  };

  return (
    <AppShell
      title="Phonebook"
      subtitle="Numbers by department, and the tahsildars of each taluk"
      actions={isAdmin ? <Button onClick={openAdd}>Add contact</Button> : undefined}
    >
      {error && <Alert tone="error">{toApiError(error).message}</Alert>}

      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div className="inline-flex flex-wrap gap-1 rounded-lg bg-surface-sunken p-1 ring-1 ring-line">
          {(
            [
              { value: 'DEPARTMENT', label: 'By department' },
              { value: 'TALUK', label: 'Tahsildars' },
            ] as const
          ).map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => setBook(option.value)}
              className={`rounded-md px-4 py-2 text-sm font-semibold transition-colors
                duration-[--duration-base] ease-[--ease-settle] ${
                  book === option.value
                    ? 'bg-brand text-on-brand shadow-card'
                    : 'text-slate-600 hover:bg-navy-50/70 hover:text-navy-700'
                }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        <div className="w-full sm:w-72">
          <TextField
            label="Search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Name, designation or number"
          />
        </div>
      </div>

      {loading && <SkeletonRows count={4} label="Loading the phonebook" />}

      {!loading && empty && (
        <p className="rounded-xl border border-line bg-surface p-6 text-sm text-slate-500 shadow-card">
          {term
            ? `Nothing in the phonebook matches “${query}”.`
            : isAdmin
              ? 'The phonebook is empty. Add the first contact to start it.'
              : 'The phonebook is empty. An administrator adds the numbers.'}
        </p>
      )}

      {!loading && book === 'DEPARTMENT' && (
        <div className="stagger space-y-4">
          {departmentGroups.map((group) => (
            <Group key={group.departmentId} heading={group.departmentName} count={group.contacts.length}>
              {group.contacts.map((contact) => (
                <ContactRow
                  key={contact.id}
                  contact={contact}
                  canEdit={isAdmin}
                  onEdit={() => openEdit(contact)}
                  onDelete={() => remove.mutate(contact)}
                  removing={remove.isPending && remove.variables?.id === contact.id}
                />
              ))}
            </Group>
          ))}
        </div>
      )}

      {!loading && book === 'TALUK' && (
        <div className="stagger space-y-4">
          {talukGroups.map((group) => (
            <Group
              key={group.taluk}
              heading={group.taluk}
              count={group.tahsildars.length + group.groupMembers.length}
            >
              {[...group.tahsildars, ...group.groupMembers].map((contact) => (
                <ContactRow
                  key={contact.id}
                  contact={contact}
                  canEdit={isAdmin}
                  onEdit={() => openEdit(contact)}
                  onDelete={() => remove.mutate(contact)}
                  removing={remove.isPending && remove.variables?.id === contact.id}
                />
              ))}
            </Group>
          ))}
        </div>
      )}

      {isAdmin && (
        <ContactDialog
          open={dialogOpen}
          kind={book}
          editing={editing}
          onClose={() => setDialogOpen(false)}
        />
      )}
    </AppShell>
  );
}

/** A department or a taluk, and everyone under it. */
function Group({
  heading,
  count,
  children,
}: {
  heading: string;
  count: number;
  children: React.ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-xl border border-line bg-surface shadow-card">
      <div className="flex items-center justify-between gap-3 border-b border-line bg-surface-sunken px-5 py-3">
        <h2 className="font-semibold text-slate-900">{heading}</h2>
        <span className="text-xs text-slate-500">
          {count} {count === 1 ? 'contact' : 'contacts'}
        </span>
      </div>
      <ul className="divide-y divide-slate-100">{children}</ul>
    </section>
  );
}

function ContactRow({
  contact,
  canEdit,
  onEdit,
  onDelete,
  removing,
}: {
  contact: PhonebookContact;
  canEdit: boolean;
  onEdit: () => void;
  onDelete: () => void;
  removing: boolean;
}) {
  return (
    <li className="group/row flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3.5 transition-colors
      duration-[--duration-base] hover:bg-navy-50/70">
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium text-slate-900 transition-colors duration-[--duration-base]
          group-hover/row:text-navy-800">
          {contact.fullName}
          {contact.role === 'TAHSILDAR' && (
            <span className="ml-2 rounded-full bg-navy-50 px-2 py-0.5 text-xs font-semibold text-navy-700
              ring-1 ring-inset ring-navy-500/15">
              Tahsildar
            </span>
          )}
        </p>
        {(contact.designation || contact.email) && (
          <p className="truncate text-xs text-slate-500">
            {[contact.designation, contact.email].filter(Boolean).join(' · ')}
          </p>
        )}
      </div>

      {/*
        A number in a phonebook is there to be rung: `tel:` dials it on a phone and hands it to the
        desk software on a computer, which is the difference between a directory and a picture of
        one. `tabular-nums` keeps a column of them aligned.
      */}
      <div className="flex flex-col items-start sm:items-end">
        <a
          href={`tel:${contact.phoneNumber.replace(/[^\d+]/g, '')}`}
          className="rounded-md font-medium tabular-nums text-navy-700 outline-none transition-colors
            duration-[--duration-base] hover:text-navy-800 hover:underline
            focus-visible:ring-2 focus-visible:ring-navy-300"
        >
          {contact.phoneNumber}
        </a>
        {contact.alternatePhone && (
          <a
            href={`tel:${contact.alternatePhone.replace(/[^\d+]/g, '')}`}
            className="rounded-md text-xs tabular-nums text-slate-500 outline-none transition-colors
              duration-[--duration-base] hover:text-navy-700 hover:underline
              focus-visible:ring-2 focus-visible:ring-navy-300"
          >
            {contact.alternatePhone}
          </a>
        )}
      </div>

      {canEdit && (
        <div className="flex gap-1">
          <Button variant="ghost" size="sm" onClick={onEdit}>
            Edit
          </Button>
          <Button
            variant="ghost"
            size="sm"
            loading={removing}
            onClick={onDelete}
            className="text-red-600! hover:bg-red-50!"
          >
            Remove
          </Button>
        </div>
      )}
    </li>
  );
}
