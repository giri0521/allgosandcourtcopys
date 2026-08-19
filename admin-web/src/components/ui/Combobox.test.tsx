import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { Combobox } from '@/components/ui/Combobox';
import type { ComboboxOption } from '@/lib/option-filter';

const OPTIONS: ComboboxOption[] = [
  { value: 'agr', label: 'Department of Agriculture and Farmers Welfare', hint: 'AGR' },
  { value: 'hfw', label: 'Department of Health and Family Welfare', hint: 'HFW' },
  { value: 'hed', label: 'Department of Higher Education', hint: 'HED' },
  { value: 'hmp', label: 'Department of Highways and Minor Ports', hint: 'HMP' },
];

/** Controlled, like the real usage — the parent owns the value. */
function Harness({ onChange }: { onChange?: (value: string | null) => void }) {
  const [value, setValue] = useState<string | null>(null);
  return (
    <Combobox
      label="Department"
      options={OPTIONS}
      value={value}
      onChange={(next) => {
        setValue(next);
        onChange?.(next);
      }}
      placeholder="Search departments…"
      emptyMessage="No department matches"
    />
  );
}

const box = () => screen.getByRole('combobox');
const list = () => screen.getByRole('listbox');
const optionNames = () =>
  within(list())
    .queryAllByRole('option')
    .map((node) => node.textContent?.trim());

describe('Combobox', () => {
  it('opens on focus and offers everything', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    expect(box()).toHaveAttribute('aria-expanded', 'false');
    await user.click(box());

    expect(box()).toHaveAttribute('aria-expanded', 'true');
    expect(optionNames()).toHaveLength(4);
  });

  it('filters as you type, matching mid-name', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(box());
    await user.type(box(), 'health');

    expect(optionNames()).toEqual(['Department of Health and Family WelfareHFW']);
  });

  it('matches words in any order', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(box());
    await user.type(box(), 'family health');

    expect(optionNames()).toHaveLength(1);
  });

  it('says so when nothing matches, and offers nothing to pick', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(box());
    await user.type(box(), 'fisheries');

    expect(optionNames()).toEqual([]);
    expect(screen.getByText(/No department matches/)).toBeInTheDocument();
  });

  it('chooses with the keyboard alone', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);

    await user.click(box());
    await user.type(box(), 'high');
    // Higher Education, then Highways — arrow down once to reach the second.
    await user.keyboard('{ArrowDown}{Enter}');

    expect(onChange).toHaveBeenCalledWith('hmp');
    expect(box()).toHaveValue('Department of Highways and Minor Ports');
    expect(box()).toHaveAttribute('aria-expanded', 'false');
  });

  it('wraps at the ends rather than dead-ending', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);

    await user.click(box());
    // Four options; up from the first wraps to the last.
    await user.keyboard('{ArrowUp}{Enter}');

    expect(onChange).toHaveBeenCalledWith('hmp');
  });

  it('points aria-activedescendant at the active option, so it is announced', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(box());
    const first = box().getAttribute('aria-activedescendant');
    expect(first).toBeTruthy();

    await user.keyboard('{ArrowDown}');
    expect(box().getAttribute('aria-activedescendant')).not.toBe(first);

    // And it names an element that actually exists.
    const active = document.getElementById(box().getAttribute('aria-activedescendant')!);
    expect(active).toHaveAttribute('role', 'option');
  });

  it('closes on Escape and keeps the previous choice', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(box());
    await user.keyboard('{Enter}'); // take the first
    expect(box()).toHaveValue('Department of Agriculture and Farmers Welfare');

    await user.click(box());
    await user.type(box(), 'high');
    await user.keyboard('{Escape}');

    expect(box()).toHaveAttribute('aria-expanded', 'false');
    expect(box()).toHaveValue('Department of Agriculture and Farmers Welfare');
  });

  it('clears the choice', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);

    await user.click(box());
    await user.keyboard('{Enter}');
    expect(box()).toHaveValue('Department of Agriculture and Farmers Welfare');

    await user.click(screen.getByRole('button', { name: /clear department/i }));
    expect(onChange).toHaveBeenLastCalledWith(null);
    expect(box()).toHaveValue('');
  });

  it('marks the chosen option as selected for assistive technology', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(box());
    await user.keyboard('{Enter}');
    await user.click(box());

    const selected = within(list())
      .getAllByRole('option')
      .filter((node) => node.getAttribute('aria-selected') === 'true');
    expect(selected).toHaveLength(1);
    expect(selected[0].textContent).toContain('Agriculture');
  });

  it('does nothing at all when disabled', async () => {
    const user = userEvent.setup();
    render(
      <Combobox label="Department" options={OPTIONS} value={null} onChange={vi.fn()} disabled />,
    );

    await user.click(box());
    expect(box()).toBeDisabled();
    expect(box()).toHaveAttribute('aria-expanded', 'false');
  });
});
