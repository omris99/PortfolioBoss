import { useRef, useState, type KeyboardEvent } from 'react';
import { changeSector, errorMessageOf } from '../lib/apiClient';
import type { Holding } from '../types/portfolio';

/** The `<datalist>` of the sectors already entered, rendered once by the table, so each is offered as you type. */
export const SECTOR_OPTIONS_LIST_ID = 'sector-options';

const SECTOR_MAX_LENGTH = 60;   // the holding.sector column

/** The sector as a button; clicking it turns the cell into a text field until the value is saved or cancelled. */
export function SectorCell({ holding, onDataChanged }: { holding: Holding; onDataChanged: () => Promise<void> }) {
  const [isEditing, setIsEditing] = useState(false);

  if (isEditing) {
    return <SectorEditor holding={holding} onDataChanged={onDataChanged} onFinished={() => setIsEditing(false)} />;
  }
  return (
    <button
      type="button"
      title="Edit sector"
      onClick={() => setIsEditing(true)}
      className={`rounded px-1 transition-colors hover:bg-slate-800 ${holding.sector === null ? 'text-slate-500' : ''}`}
    >
      {holding.sector ?? '+ add'}
    </button>
  );
}

/** "  Technology " → "Technology"; nothing but spaces → `null`, which clears the sector. */
function sectorFromTypedText(typedText: string): string | null {
  const trimmedText = typedText.trim();
  return trimmedText === '' ? null : trimmedText;
}

/**
 * Enter or leaving the field saves (if the value changed), Esc cancels, an empty field clears the sector. Enter
 * saves by taking the focus away, so leaving the field is the only place a save starts and one key press never
 * saves twice.
 */
function SectorEditor({
  holding,
  onDataChanged,
  onFinished,
}: {
  holding: Holding;
  onDataChanged: () => Promise<void>;
  onFinished: () => void;
}) {
  const [typedSector, setTypedSector] = useState(holding.sector ?? '');
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const wasCancelledRef = useRef(false);

  const saveIfChanged = async () => {
    const newSector = sectorFromTypedText(typedSector);
    if (wasCancelledRef.current || newSector === holding.sector) {
      onFinished();
      return;
    }
    setIsSaving(true);
    setErrorMessage(null);
    try {
      await changeSector(holding.id, newSector);
      await onDataChanged();
      onFinished();
    } catch (error) {
      setErrorMessage(errorMessageOf(error));
    } finally {
      setIsSaving(false);
    }
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.currentTarget.blur();
    } else if (event.key === 'Escape') {
      wasCancelledRef.current = true;
      event.currentTarget.blur();
    }
  };

  return (
    <div className="flex flex-col gap-1">
      <input
        autoFocus
        list={SECTOR_OPTIONS_LIST_ID}
        value={typedSector}
        maxLength={SECTOR_MAX_LENGTH}
        readOnly={isSaving}
        placeholder="Sector"
        aria-label={`Sector of ${holding.symbol}`}
        onChange={(event) => setTypedSector(event.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={() => void saveIfChanged()}
        className="w-36 rounded-md border border-slate-700 bg-slate-900 px-2 py-1 font-sans text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
      />
      {errorMessage && <span className="whitespace-normal font-sans text-[11px] text-rose-400">{errorMessage}</span>}
    </div>
  );
}
