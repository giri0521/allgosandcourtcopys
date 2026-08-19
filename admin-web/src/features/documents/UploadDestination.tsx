import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Combobox } from '@/components/ui/Combobox';
import type { ComboboxOption } from '@/lib/option-filter';
import { fetchDepartments, fetchDepartmentFolderTree } from '@/features/documents/api';
import { formatCategory } from '@/lib/format';

export interface Destination {
  id: string;
  name: string;
  departmentId: string;
}

/**
 * Where a document should be filed, asked only when the user did not start from inside a folder.
 *
 * <p>Every document belongs to a folder — there is no loose pile — so an upload begun from My
 * Uploads has to collect a destination before it can start. Opening the dialog from a folder skips
 * this entirely and files where you already are, which is the common path and stays one click.
 *
 * <p>Department first, then folder, because forty-three departments' worth of folders in a single
 * list is not a list anybody can use. Both are comboboxes rather than selects: the departments are
 * all named "Department of …", so scanning them is slow and the browser's own type-ahead — which
 * only matches the beginning of an option — is no help whatsoever. Typing narrows instead.
 */
export function UploadDestination({
  value,
  onChange,
  disabled,
}: {
  value: Destination | null;
  onChange: (destination: Destination | null) => void;
  disabled?: boolean;
}) {
  // Seeded from the current choice so reopening the dialog does not forget which department the
  // chosen folder was in.
  const [departmentId, setDepartmentId] = useState<string | null>(value?.departmentId ?? null);

  const departments = useQuery({ queryKey: ['departments'], queryFn: fetchDepartments });

  const folders = useQuery({
    queryKey: ['folder-tree', departmentId],
    queryFn: () => fetchDepartmentFolderTree(departmentId!),
    enabled: departmentId !== null,
  });

  const departmentOptions: ComboboxOption[] = useMemo(
    () =>
      (departments.data ?? []).map((department) => ({
        value: department.id,
        label: department.name,
        // Searchable as well as visible: people who know a department by its code type the code.
        hint: department.code ?? undefined,
      })),
    [departments.data],
  );

  const folderOptions: ComboboxOption[] = useMemo(
    () =>
      (folders.data ?? []).map(({ folder, depth }) => ({
        value: folder.id,
        label: folder.name,
        depth,
        hint: formatCategory(folder.category),
      })),
    [folders.data],
  );

  const noFolders = folders.isSuccess && folders.data.length === 0;

  return (
    <div className="space-y-3 rounded-lg border border-line bg-surface-sunken p-3.5">
      <Combobox
        label="Department"
        options={departmentOptions}
        value={departmentId}
        loading={departments.isPending}
        disabled={disabled}
        placeholder="Search departments…"
        emptyMessage="No department matches"
        error={departments.isError ? 'Could not load the departments.' : undefined}
        onChange={(next) => {
          setDepartmentId(next);
          // The folder belonged to the old department; keeping it would file the document
          // somewhere the user is no longer looking at.
          onChange(null);
        }}
      />

      <Combobox
        label="Folder"
        options={folderOptions}
        value={value?.id ?? null}
        loading={departmentId !== null && folders.isPending}
        disabled={disabled || departmentId === null || noFolders}
        placeholder={departmentId === null ? 'Choose a department first' : 'Search folders…'}
        emptyMessage="No folder matches"
        hint={
          noFolders
            ? 'This department has no folders yet. One has to be created before anything can be filed into it.'
            : undefined
        }
        error={folders.isError ? 'Could not load the folders for this department.' : undefined}
        onChange={(next) => {
          const chosen = folders.data?.find((entry) => entry.folder.id === next);
          onChange(
            chosen && departmentId
              ? { id: chosen.folder.id, name: chosen.folder.name, departmentId }
              : null,
          );
        }}
      />
    </div>
  );
}
