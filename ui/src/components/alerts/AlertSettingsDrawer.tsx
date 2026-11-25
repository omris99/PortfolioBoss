import { useId, useState } from 'react';
import { useSaveAction } from '../../hooks/useSaveAction';
import { INPUT_FRAME_CLASS, numberOrNull } from '../../lib/formInput';
import { NUMBER_INPUT_CLASS, SECONDARY_BUTTON_CLASS } from '../../lib/styles';
import type { AlertSetting, AlertType } from '../../types/alerts';
import { DrawerCloseButton, DrawerShell, DrawerTitle, useCloseDrawer } from '../DrawerShell';
import { FormError, SaveButton } from '../FormFields';
import { SwitchNamedBy } from '../Switch';
import {
  ALERT_SETTING_GROUPS,
  ALERT_SETTING_LINES,
  allowedThresholdText,
  defaultThresholdText,
  isAllowedThreshold,
  type AlertSettingLine,
} from './alertTexts';

/** What is on screen for one type while the drawer is open: the switch, and the number as typed. */
interface TypedSetting {
  enabled: boolean;
  typedThreshold: string;
}

type TypedSettings = Record<AlertType, TypedSetting>;

function typedSettingsOf(settings: AlertSetting[]): TypedSettings {
  const typedSettings = {} as TypedSettings;
  settings.forEach((setting) => {
    typedSettings[setting.alertType] = {
      enabled: setting.enabled,
      typedThreshold: setting.threshold === null ? '' : String(setting.threshold),
    };
  });
  return typedSettings;
}

/** Every type's line as it will be saved; a type without a number sends none. */
function settingsToSave(settings: AlertSetting[], typedSettings: TypedSettings): AlertSetting[] {
  return settings.map((setting) => {
    const typedSetting = typedSettings[setting.alertType];
    const hasNumber = ALERT_SETTING_LINES[setting.alertType].kind === 'withNumber';
    return {
      ...setting,
      enabled: typedSetting.enabled,
      threshold: hasNumber ? numberOrNull(typedSetting.typedThreshold) : null,
    };
  });
}

/** The first line whose number the server wouldn't take, in words — `null` when every number is fine. */
function firstUnusableNumberOf(typedSettings: TypedSettings): string | null {
  for (const group of ALERT_SETTING_GROUPS) {
    for (const type of group.types) {
      const line = ALERT_SETTING_LINES[type];
      if (line.kind === 'withNumber' && !isAllowedThreshold(numberOrNull(typedSettings[type].typedThreshold), line.range)) {
        return `${line.textBeforeNumber}: ${allowedThresholdText(line.range)}.`;
      }
    }
  }
  return null;
}

/** How many types are on, as the switches stand now. */
function enabledCountOf(typedSettings: TypedSettings): number {
  return Object.values(typedSettings).filter((typedSetting) => typedSetting.enabled).length;
}

/**
 * The number inside a sentence (round 6): small, its figure on the right, red when the server wouldn't take it. A type
 * turned off keeps its number in a disabled field, for when it is on again.
 */
function ThresholdField({
  line,
  typedSetting,
  onTypedThresholdChange,
}: {
  line: Extract<AlertSettingLine, { kind: 'withNumber' }>;
  typedSetting: TypedSetting;
  onTypedThresholdChange: (typedThreshold: string) => void;
}) {
  return (
    <input
      type="text"
      inputMode="decimal"
      value={typedSetting.typedThreshold}
      disabled={!typedSetting.enabled}
      aria-label={`${line.textBeforeNumber} … ${line.textAfterNumber}`}
      aria-invalid={!isAllowedThreshold(numberOrNull(typedSetting.typedThreshold), line.range)}
      onChange={(event) => onTypedThresholdChange(event.target.value)}
      className={`${INPUT_FRAME_CLASS} ${NUMBER_INPUT_CLASS} mx-1 h-[26px] w-[52px] aria-[invalid=true]:border-negative disabled:border-line disabled:bg-subtle disabled:text-muted`}
    />
  );
}

/**
 * One kind (round 6, the design system's AlertSettings): the switch in its column, then the sentence — with its number's
 * field inside it — and, while the number isn't the default, a note naming the default. A kind turned off: its sentence
 * muted, its note faint.
 */
function AlertKindRow({
  setting,
  typedSetting,
  onTypedSettingChange,
}: {
  setting: AlertSetting;
  typedSetting: TypedSetting;
  onTypedSettingChange: (typedSetting: TypedSetting) => void;
}) {
  const sentenceId = useId();
  const line = ALERT_SETTING_LINES[setting.alertType];
  const isOff = !typedSetting.enabled;
  const isDefaultNumber = numberOrNull(typedSetting.typedThreshold) === setting.defaultThreshold;
  const defaultNote =
    line.kind === 'withNumber' && setting.defaultThreshold !== null && !isDefaultNumber
      ? defaultThresholdText(setting.defaultThreshold, line.range)
      : null;

  return (
    <li className="grid grid-cols-[36px_minmax(0,1fr)] items-center gap-x-3 py-3">
      <SwitchNamedBy
        checked={typedSetting.enabled}
        onChange={(enabled) => onTypedSettingChange({ ...typedSetting, enabled })}
        labelId={sentenceId}
      />
      <div className="flex min-w-0 flex-col gap-0.5">
        <span id={sentenceId} className={`font-medium leading-[26px] ${isOff ? 'text-muted' : 'text-ink'}`}>
          {line.kind === 'plain' ? (
            line.text
          ) : (
            <>
              {line.textBeforeNumber}
              <ThresholdField
                line={line}
                typedSetting={typedSetting}
                onTypedThresholdChange={(typedThreshold) => onTypedSettingChange({ ...typedSetting, typedThreshold })}
              />
              {line.textAfterNumber}
            </>
          )}
        </span>
        {defaultNote !== null && (
          <span className={`text-[13px] leading-[18px] ${isOff ? 'text-faint' : 'text-muted'}`}>{defaultNote}</span>
        )}
      </div>
    </li>
  );
}

/**
 * The drawer's inside: its head — "Checked on every sync · 7 of 9 on", counted as the switches stand —, the kinds in
 * their five groups, and Cancel · Save. Save checks every number first, sends every line together, and the drawer leaves
 * once it is saved; Cancel leaves without saving.
 */
function AlertSettingsForm({
  settings,
  onSave,
}: {
  settings: AlertSetting[];
  onSave: (settings: AlertSetting[]) => Promise<void>;
}) {
  const closeDrawer = useCloseDrawer();
  const [typedSettings, setTypedSettings] = useState<TypedSettings>(() => typedSettingsOf(settings));
  const { isSaving, errorMessage, setErrorMessage, runSave } = useSaveAction();
  const settingOf = (type: AlertType) => settings.find((setting) => setting.alertType === type);

  const changeTypedSetting = (type: AlertType, typedSetting: TypedSetting) =>
    setTypedSettings((previousTypedSettings) => ({ ...previousTypedSettings, [type]: typedSetting }));

  const save = async () => {
    const unusableNumber = firstUnusableNumberOf(typedSettings);
    if (unusableNumber !== null) {
      setErrorMessage(unusableNumber);
      return;
    }
    if (await runSave(() => onSave(settingsToSave(settings, typedSettings)))) closeDrawer();
  };

  return (
    <form
      className="flex min-h-0 flex-1 flex-col"
      onSubmit={(event) => {
        event.preventDefault();
        void save();
      }}
    >
      <div className="flex flex-col gap-1 border-b px-6 pb-4 pt-5">
        <div className="flex items-center gap-2">
          <DrawerTitle>Alert settings</DrawerTitle>
          <span className="ml-auto">
            <DrawerCloseButton />
          </span>
        </div>
        <p className="text-muted">
          Checked on every sync · {enabledCountOf(typedSettings)} of {settings.length} on
        </p>
      </div>
      <div className="flex flex-1 flex-col gap-5 overflow-y-auto px-6 pb-6 pt-5">
        {ALERT_SETTING_GROUPS.map((group) => (
          <section key={group.heading} aria-label={group.heading}>
            <h3 className="pb-2 text-caption font-semibold text-muted">{group.heading}</h3>
            <ul className="divide-y border-y">
              {group.types.map((type) => {
                const setting = settingOf(type);
                return (
                  setting !== undefined && (
                    <AlertKindRow
                      key={type}
                      setting={setting}
                      typedSetting={typedSettings[type]}
                      onTypedSettingChange={(typedSetting) => changeTypedSetting(type, typedSetting)}
                    />
                  )
                );
              })}
            </ul>
          </section>
        ))}
        {errorMessage && <FormError>{errorMessage}</FormError>}
      </div>
      <div className="flex items-center justify-end gap-2 border-t bg-page px-6 py-4">
        <button type="button" onClick={closeDrawer} className={SECONDARY_BUTTON_CLASS}>
          Cancel
        </button>
        <SaveButton isSaving={isSaving} label="Save" />
      </div>
    </form>
  );
}

/**
 * The alerts' settings (MY_HOLDINGS_TODO.md, decision 17; round 6's design), from the ⚙ of the bell's panel: each kind
 * on or off, and the number of the four that have one. A narrow drawer (440px). The server checks the alerts again with
 * them at once; a kind turned off is neither checked nor shown in the bell.
 */
export function AlertSettingsDrawer({
  settings,
  onSave,
  onClose,
}: {
  settings: AlertSetting[];
  onSave: (settings: AlertSetting[]) => Promise<void>;
  onClose: () => void;
}) {
  return (
    <DrawerShell ariaLabel="Alert settings" width="narrow" onClose={onClose}>
      <AlertSettingsForm settings={settings} onSave={onSave} />
    </DrawerShell>
  );
}
