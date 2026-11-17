import { useState } from 'react';
import { useSaveAction } from '../../hooks/useSaveAction';
import { numberOrNull } from '../../lib/formInput';
import { SECONDARY_BUTTON_CLASS } from '../../lib/styles';
import type { AlertSetting, AlertType } from '../../types/alerts';
import { DrawerCloseButton, DrawerShell, DrawerTitle } from '../DrawerShell';
import { FormError, SaveButton } from '../FormFields';
import { Switch } from '../Switch';
import { NumberInputWithUnit } from '../weightCalculator/WeightCalculatorPanel';
import {
  ALERT_SETTING_GROUPS,
  ALERT_SETTING_LINES,
  allowedThresholdText,
  isAllowedThreshold,
  type ThresholdRange,
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

/** "default 15%", beside a field whose number isn't the default any more. */
function DefaultThresholdHint({ setting, range }: { setting: AlertSetting; range: ThresholdRange }) {
  return (
    <span className="text-caption text-faint">
      default {setting.defaultThreshold}
      {range.unit === '%' ? '%' : ` ${range.unit}`}
    </span>
  );
}

/** One type's line: the switch with its words, and for a type with a number the field between its words. */
function AlertSettingRow({
  setting,
  typedSetting,
  onTypedSettingChange,
}: {
  setting: AlertSetting;
  typedSetting: TypedSetting;
  onTypedSettingChange: (typedSetting: TypedSetting) => void;
}) {
  const line = ALERT_SETTING_LINES[setting.alertType];
  const toggle = (enabled: boolean) => onTypedSettingChange({ ...typedSetting, enabled });
  if (line.kind === 'plain') {
    return (
      <Switch checked={typedSetting.enabled} onChange={toggle}>
        {line.text}
      </Switch>
    );
  }
  const typedNumber = numberOrNull(typedSetting.typedThreshold);
  const isDefault = typedNumber === setting.defaultThreshold;
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
      <Switch checked={typedSetting.enabled} onChange={toggle}>
        {line.textBeforeNumber}
      </Switch>
      <NumberInputWithUnit
        typedText={typedSetting.typedThreshold}
        unit={line.range.unit}
        isUnusable={!isAllowedThreshold(typedNumber, line.range)}
        onTypedTextChange={(typedThreshold) => onTypedSettingChange({ ...typedSetting, typedThreshold })}
        widthClass="w-16"
        ariaLabel={`${line.textBeforeNumber} … ${line.range.unit} ${line.textAfterNumber}`.trim()}
      />
      {line.textAfterNumber !== '' && <span className="text-ink">{line.textAfterNumber}</span>}
      {!isDefault && <DefaultThresholdHint setting={setting} range={line.range} />}
    </div>
  );
}

/**
 * The alerts' settings (MY_HOLDINGS_TODO.md, decision 17), from the ⚙ of the bell's list: each type on or off, and the
 * number of the four that have one. Save sends every line together; the server checks the alerts again with them at
 * once, and the drawer closes. A type turned off is neither checked nor shown in the bell.
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
    if (await runSave(() => onSave(settingsToSave(settings, typedSettings)))) onClose();
  };

  return (
    <DrawerShell ariaLabel="Alert settings" onClose={onClose}>
      <form
        className="flex min-h-0 flex-1 flex-col"
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <div className="flex items-center gap-2 border-b px-6 py-4">
          <DrawerTitle>Alert settings</DrawerTitle>
          <span className="ml-auto">
            <DrawerCloseButton />
          </span>
        </div>
        <div className="flex-1 overflow-y-auto px-6 pb-6 pt-4">
          <p className="mb-4 text-muted">Checked on every sync. A type turned off is neither checked nor shown.</p>
          {ALERT_SETTING_GROUPS.map((group) => (
            <section key={group.heading} className="mb-5">
              <h3 className="mb-1 text-caption font-semibold uppercase tracking-[0.04em] text-faint">{group.heading}</h3>
              <ul className="flex flex-col gap-1">
                {group.types.map((type) => {
                  const setting = settingOf(type);
                  return (
                    setting !== undefined && (
                      <li key={type}>
                        <AlertSettingRow
                          setting={setting}
                          typedSetting={typedSettings[type]}
                          onTypedSettingChange={(typedSetting) => changeTypedSetting(type, typedSetting)}
                        />
                      </li>
                    )
                  );
                })}
              </ul>
            </section>
          ))}
          {errorMessage && <FormError>{errorMessage}</FormError>}
        </div>
        <div className="flex items-center justify-end gap-2 border-t bg-page px-6 py-4">
          <button type="button" onClick={onClose} className={SECONDARY_BUTTON_CLASS}>
            Cancel
          </button>
          <SaveButton isSaving={isSaving} label="Save" />
        </div>
      </form>
    </DrawerShell>
  );
}
