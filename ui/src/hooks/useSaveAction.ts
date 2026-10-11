import { useState } from 'react';
import { errorMessageOf } from '../lib/apiClient';

/**
 * What every form and confirm dialog does when it sends something to the API: "Saving…" meanwhile, and the API's
 * reason when it refuses, shown as it comes. `runSave` answers whether the save went through, so the caller knows
 * whether to close.
 */
export function useSaveAction() {
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const runSave = async (save: () => Promise<void>): Promise<boolean> => {
    setIsSaving(true);
    setErrorMessage(null);
    try {
      await save();
      return true;
    } catch (error) {
      setErrorMessage(errorMessageOf(error));
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  return { isSaving, errorMessage, setErrorMessage, runSave };
}
