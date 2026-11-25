import { useId, useRef, useState, type ReactNode, type SelectHTMLAttributes } from 'react';
import { Calendar, ChevronDown, Info, LoaderCircle, TriangleAlert } from 'lucide-react';
import { formatDate } from '../lib/format';
import { INPUT_CLASS, isIsoDate, isoDateFromTypedDate } from '../lib/formInput';
import { PRIMARY_BUTTON_CLASS, TOGGLE_GROUP_CLASS, toggleOptionClass } from '../lib/styles';
import { Hint } from './Hint';

/** The columns a field takes in its form's grid. */
const COLUMN_SPAN_CLASSES = { 1: '', 2: 'col-span-2', 3: 'col-span-3', 4: 'col-span-4' } as const;

/**
 * One field of a form: its label ("optional" after it when it may stay empty), the control, and a hint under it.
 * `isGroup` is for controls that are several buttons, like the BUY/SELL toggle: a `<label>` around buttons would click
 * the first one whenever its text is clicked.
 */
export function Field({
  label,
  optional = false,
  hint,
  span = 1,
  isGroup = false,
  children,
}: {
  label: string;
  optional?: boolean;
  hint?: string;
  span?: keyof typeof COLUMN_SPAN_CLASSES;
  isGroup?: boolean;
  children: ReactNode;
}) {
  const labelId = useId();
  const labelText = (
    <span id={labelId} className="flex items-center gap-1.5 text-label text-muted">
      {label}
      {optional && <span className="font-normal text-faint">optional</span>}
    </span>
  );
  const fieldClass = `flex min-w-0 flex-col gap-1.5 ${COLUMN_SPAN_CLASSES[span]}`;
  const hintText = hint && <span className="text-caption text-faint">{hint}</span>;

  if (isGroup) {
    return (
      <div role="group" aria-labelledby={labelId} className={fieldClass}>
        {labelText}
        {children}
        {hintText}
      </div>
    );
  }
  return (
    <label className={fieldClass}>
      {labelText}
      {children}
      {hintText}
    </label>
  );
}

/** A two-way choice — BUY or SELL, DEPOSIT or WITHDRAWAL — written as the system writes it. */
export function Toggle<Option extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Option[];
  value: Option;
  onChange: (option: Option) => void;
}) {
  return (
    <span role="radiogroup" aria-label={label} className={TOGGLE_GROUP_CLASS}>
      {options.map((option) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={option === value}
          onClick={() => onChange(option)}
          className={toggleOptionClass(option === value)}
        >
          {option}
        </button>
      ))}
    </span>
  );
}

/** What the form keeps for a typed text: the API's 'yyyy-MM-dd' once it is a whole date, else the text as typed. */
function formValueOf(typedText: string): string {
  return isoDateFromTypedDate(typedText) ?? typedText;
}

/** What the field shows for the form's value: a whole date as dd/mm/yyyy, anything else as it was typed. */
function shownTextOf(formValue: string): string {
  return isIsoDate(formValue) ? formatDate(formValue) : formValue;
}

/**
 * A date typed as dd/mm/yyyy, the app's way of writing a date since round 6 — never the browser's own format, which
 * follows the computer's language — with a calendar button that opens the browser's date picker on a hidden date field.
 * The form gets the API's 'yyyy-MM-dd' once a whole date is typed (or picked), and the text as typed until then, so that
 * its own check names what is missing. `max`: the latest date the picker offers.
 */
export function DateInput({
  value,
  onChange,
  min,
  max,
  ariaLabel,
}: {
  value: string;
  onChange: (isoDate: string) => void;
  min?: string;
  max?: string;
  ariaLabel?: string;
}) {
  const pickerRef = useRef<HTMLInputElement>(null);
  const [typedText, setTypedText] = useState(() => shownTextOf(value));
  const [previousValue, setPreviousValue] = useState(value);
  // A value set from outside — the calendar, a prefill, a reset — replaces what was typed; one that is what was typed
  // leaves it alone, so that "1/2/2026" isn't rewritten as "01/02/2026" under the cursor.
  if (value !== previousValue) {
    setPreviousValue(value);
    if (value !== formValueOf(typedText)) setTypedText(shownTextOf(value));
  }
  const updateTypedText = (text: string) => {
    setTypedText(text);
    onChange(formValueOf(text));
  };
  const openPicker = () => {
    try {
      pickerRef.current?.showPicker();
    } catch {
      pickerRef.current?.focus();   // a browser without showPicker: the hidden field at least takes the keyboard
    }
  };
  return (
    <span className="relative block">
      <input
        type="text"
        inputMode="numeric"
        placeholder="dd/mm/yyyy"
        aria-label={ariaLabel}
        value={typedText}
        onChange={(event) => updateTypedText(event.target.value)}
        className={`${INPUT_CLASS} w-full pr-9 tabular-nums`}
      />
      <span className="absolute right-1 top-1 flex">
        <Hint text={max ? `Pick a date (up to ${formatDate(max)})` : 'Pick a date'} align="end" focusable={false}>
          <button
            type="button"
            onClick={openPicker}
            aria-label="Pick a date"
            className="inline-flex h-6 w-6 items-center justify-center rounded-full text-muted transition-colors hover:bg-accent-soft hover:text-accent"
          >
            <Calendar size={16} />
          </button>
        </Hint>
      </span>
      <input
        ref={pickerRef}
        type="date"
        tabIndex={-1}
        aria-hidden="true"
        min={min}
        max={max}
        value={isIsoDate(value) ? value : ''}
        onChange={(event) => onChange(event.target.value)}
        className="pointer-events-none absolute bottom-0 right-0 h-px w-px opacity-0"
      />
    </span>
  );
}

/** A `<select>` in the inputs' style, with its own chevron. */
export function SelectInput({ children, ...selectProps }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <span className="relative block">
      <select {...selectProps} className={`${INPUT_CLASS} w-full cursor-pointer appearance-none pr-7`}>
        {children}
      </select>
      <ChevronDown size={14} className="pointer-events-none absolute right-2 top-[9px] text-muted" />
    </span>
  );
}

/** The API's reason a save or a delete was refused, under the form, as it comes. */
export function FormError({ children }: { children: ReactNode }) {
  return (
    <div role="alert" className="mt-4 flex items-start gap-2 rounded-md bg-negative-soft px-3 py-2 text-[13px] text-ink">
      <TriangleAlert size={16} className="mt-0.5 shrink-0 text-negative" />
      <span>{children}</span>
    </div>
  );
}

/**
 * A grey band that explains something: a backfill, a read-only list. It sits above what it is about (the default), or
 * below it — under a form's fields, where it doesn't push them down as it appears.
 */
export function InfoBand({ children, placement = 'above' }: { children: ReactNode; placement?: 'above' | 'below' }) {
  const marginClass = placement === 'above' ? 'mb-4' : 'mt-4';
  return (
    <div className={`${marginClass} flex items-start gap-2 rounded-md bg-subtle px-3 py-2 text-ink`}>
      <Info size={16} className="mt-0.5 shrink-0 text-muted" />
      <span>{children}</span>
    </div>
  );
}

/** A form's submit button: "Saving…" with a spinner while the API answers. */
export function SaveButton({ isSaving, label }: { isSaving: boolean; label: string }) {
  return (
    <button type="submit" disabled={isSaving} className={PRIMARY_BUTTON_CLASS}>
      {isSaving && <LoaderCircle size={16} className="turning-icon" />}
      {isSaving ? 'Saving…' : label}
    </button>
  );
}
