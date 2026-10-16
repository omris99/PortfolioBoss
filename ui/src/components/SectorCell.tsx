import { useRef, useState, type KeyboardEvent, type MouseEvent } from 'react';
import { Plus } from 'lucide-react';
import { changeSector, errorMessageOf } from '../lib/apiClient';
import { INPUT_CLASS } from '../lib/formInput';
import type { Holding } from '../types/portfolio';

/** The `<datalist>` of the sectors already entered, rendered once by the app, so each is offered as you type. */
export const SECTOR_OPTIONS_LIST_ID = 'sector-options';

/**
 * Every sector entered so far — on a holding or a manual position — once, A→Z: offered wherever a sector is typed, so
 * the same one isn't spelled two ways.
 */
export function SectorOptionsList({ sectors }: { sectors: (string | null)[] }) {
  const distinctSectors = [...new Set(sectors.filter((sector) => sector !== null))].sort((firstSector, secondSector) =>
    firstSector.localeCompare(secondSector),
  );
  return (
    <datalist id={SECTOR_OPTIONS_LIST_ID}>
      {distinctSectors.map((sector) => (
        <option key={sector} value={sector} />
      ))}
    </datalist>
  );
}

const SECTOR_MAX_LENGTH = 60;   // the holding.sector column

/** A click or a key here is the sector's, not the row's: the row would open the holding's drawer. */
function keepFromTheRow(event: MouseEvent | KeyboardEvent) {
  event.stopPropagation();
}

/**
 * The sector as text that turns into a field when clicked, in the positions table and in a holding's drawer; a dashed
 * "+ add" while there is none.
 */
export function SectorCell({ holding, onDataChanged }: { holding: Holding; onDataChanged: () => Promise<void> }) {
  const [isEditing, setIsEditing] = useState(false);

  const startEditing = (event: MouseEvent) => {
    keepFromTheRow(event);
    setIsEditing(true);
  };

  if (isEditing) {
    return <SectorEditor holding={holding} onDataChanged={onDataChanged} onFinished={() => setIsEditing(false)} />;
  }
  if (holding.sector === null) {
    return (
      <button
        type="button"
        onClick={startEditing}
        className="inline-flex h-[22px] items-center gap-0.5 rounded-sm border border-dashed border-strong px-1.5 text-caption text-muted transition-colors hover:border-accent hover:text-accent"
      >
        <Plus size={12} strokeWidth={2.5} />
        add
      </button>
    );
  }
  return (
    <button
      type="button"
      title="Edit sector"
      onClick={startEditing}
      className="-mx-1 -my-0.5 cursor-text rounded-sm px-1 py-0.5 text-left hover:bg-surface hover:shadow-[inset_0_0_0_1px_var(--color-border-strong)]"
    >
      {holding.sector}
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
    keepFromTheRow(event);
    if (event.key === 'Enter') {
      event.currentTarget.blur();
    } else if (event.key === 'Escape') {
      wasCancelledRef.current = true;
      event.currentTarget.blur();
    }
  };

  return (
    <span className="flex flex-col gap-1" onClick={keepFromTheRow}>
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
        className={`${INPUT_CLASS} h-7 w-full min-w-[120px]`}
      />
      {errorMessage && <span className="whitespace-normal text-caption text-negative">{errorMessage}</span>}
    </span>
  );
}
