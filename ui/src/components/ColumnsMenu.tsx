import { useState } from 'react';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { Check, ChevronDown, Columns3 } from 'lucide-react';
import { SECONDARY_BUTTON_CLASS } from '../lib/styles';

/** A column as the menu offers it. */
export interface ColumnChoice {
  key: string;
  title: string;
  isShownByDefault: boolean;
  /** The symbol: without it a row says nothing. */
  isAlwaysShown?: boolean;
}

/** Where this browser remembers the columns chosen; nothing else reads it. */
const STORAGE_KEY = 'portfolioboss.positions.visibleColumns';

function defaultKeysOf(columns: ColumnChoice[]): string[] {
  return columns.filter((column) => column.isShownByDefault).map((column) => column.key);
}

/**
 * The keys remembered, the ones that still exist, the always-shown ones added back; `null` when nothing usable is
 * stored — the first visit, a private window, or storage turned off (reading it can then throw).
 */
function storedKeysOf(columns: ColumnChoice[]): string[] | null {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    if (!Array.isArray(stored)) return null;
    const storedKeys = new Set(stored.filter((key): key is string => typeof key === 'string'));
    return columns
      .filter((column) => column.isAlwaysShown || storedKeys.has(column.key))
      .map((column) => column.key);
  } catch {
    return null;
  }
}

/** The columns shown, and a setter that also remembers them in this browser. */
export function useVisibleColumnKeys(columns: ColumnChoice[]): [string[], (keys: string[]) => void] {
  const [visibleKeys, setVisibleKeys] = useState<string[]>(() => storedKeysOf(columns) ?? defaultKeysOf(columns));

  const changeVisibleKeys = (keys: string[]) => {
    setVisibleKeys(keys);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(keys));
    } catch {
      // Not remembered for next time; the choice still applies now.
    }
  };

  return [visibleKeys, changeVisibleKeys];
}

function ColumnItem({
  column,
  isVisible,
  onToggle,
}: {
  column: ColumnChoice;
  isVisible: boolean;
  onToggle: () => void;
}) {
  const boxClass = isVisible
    ? column.isAlwaysShown
      ? 'bg-[var(--color-text-faint)] text-on-accent'
      : 'bg-accent text-on-accent'
    : 'shadow-[inset_0_0_0_1px_var(--color-border-strong)]';
  return (
    <DropdownMenu.CheckboxItem
      checked={isVisible}
      disabled={column.isAlwaysShown}
      onCheckedChange={onToggle}
      // Keeps the menu open, so several columns can be ticked in a row.
      onSelect={(event) => event.preventDefault()}
      className="flex h-8 cursor-pointer items-center gap-2 px-3 text-[13px] text-ink outline-none data-[disabled]:cursor-default data-[highlighted]:bg-subtle data-[disabled]:text-muted"
    >
      <span aria-hidden="true" className={`inline-flex h-4 w-4 items-center justify-center rounded-sm ${boxClass}`}>
        {isVisible && <Check size={12} strokeWidth={3} />}
      </span>
      {column.title}
      {column.isAlwaysShown && <span className="ml-auto text-[11px] text-faint">always</span>}
    </DropdownMenu.CheckboxItem>
  );
}

function GroupLabel({ children }: { children: string }) {
  return (
    <DropdownMenu.Label className="px-3 pb-1 pt-2 text-[11px] font-semibold tracking-[0.02em] text-muted">
      {children}
    </DropdownMenu.Label>
  );
}

/**
 * "Columns" on the Positions toolbar: a checkbox per column, the ones shown by default first, and a reset. The columns
 * keep the table's order whatever is ticked. Radix's menu brings the arrow keys, typeahead and Esc.
 */
export function ColumnsMenu({
  columns,
  visibleKeys,
  onChange,
}: {
  columns: ColumnChoice[];
  visibleKeys: string[];
  onChange: (keys: string[]) => void;
}) {
  const toggle = (key: string) =>
    onChange(visibleKeys.includes(key) ? visibleKeys.filter((visibleKey) => visibleKey !== key) : [...visibleKeys, key]);
  const itemOf = (column: ColumnChoice) => (
    <ColumnItem
      key={column.key}
      column={column}
      isVisible={visibleKeys.includes(column.key)}
      onToggle={() => toggle(column.key)}
    />
  );

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger className={SECONDARY_BUTTON_CLASS}>
        <Columns3 size={16} />
        Columns
        <ChevronDown size={14} />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={6}
          className="z-30 w-[220px] rounded-md border bg-surface-raised py-1 shadow-raised"
        >
          <GroupLabel>Shown by default</GroupLabel>
          {columns.filter((column) => column.isShownByDefault).map(itemOf)}
          <GroupLabel>More columns</GroupLabel>
          {columns.filter((column) => !column.isShownByDefault).map(itemOf)}
          <DropdownMenu.Separator className="my-1 h-px bg-line" />
          <DropdownMenu.Item
            onSelect={() => onChange(defaultKeysOf(columns))}
            className="mx-1 cursor-pointer rounded-sm px-2 py-1.5 text-caption font-medium text-accent outline-none data-[highlighted]:underline"
          >
            Reset to default
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
