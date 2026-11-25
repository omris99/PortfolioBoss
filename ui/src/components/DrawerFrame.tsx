import type { ReactNode } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { ACCOUNT_CURRENCY } from '../lib/format';
import { LARGE_ICON_BUTTON_CLASS } from '../lib/styles';
import { DrawerCloseButton, DrawerTitle } from './DrawerShell';

export interface DrawerTab<TabKey extends string> {
  key: TabKey;
  label: string;
}

/** ‹ ›: the items before and after this one in the page's list, round its ends, and what each button opens. */
export interface DrawerNavigation {
  previousLabel: string;
  nextLabel: string;
  onPrevious: () => void;
  onNext: () => void;
}

/** ‹ › and ✕ at the header's right; ‹ › disabled while there is no list to step through (the first load). */
function HeaderButtons({ navigation }: { navigation: DrawerNavigation | null }) {
  return (
    <span className="ml-auto inline-flex items-center gap-0.5">
      <button
        type="button"
        aria-label="Previous"
        title={navigation === null ? undefined : `Previous: ${navigation.previousLabel}`}
        disabled={navigation === null}
        onClick={navigation?.onPrevious}
        className={LARGE_ICON_BUTTON_CLASS}
      >
        <ChevronLeft size={18} />
      </button>
      <button
        type="button"
        aria-label="Next"
        title={navigation === null ? undefined : `Next: ${navigation.nextLabel}`}
        disabled={navigation === null}
        onClick={navigation?.onNext}
        className={LARGE_ICON_BUTTON_CLASS}
      >
        <ChevronRight size={18} />
      </button>
      <span aria-hidden="true" className="mx-1 h-4 w-px bg-line" />
      <DrawerCloseButton />
    </span>
  );
}

/**
 * The underline tab bar, its first tab flush with the content above and below it (round 6: no padding, 20px apart, the
 * underline as wide as the label); with a single tab, that tab as a plain heading, so the drawer keeps its shape.
 */
function TabBar<TabKey extends string>({
  tabs,
  activeTab,
  onTabChange,
}: {
  tabs: DrawerTab<TabKey>[];
  activeTab: TabKey;
  onTabChange: (tab: TabKey) => void;
}) {
  const underline = "after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:content-['']";
  if (tabs.length === 1) {
    return (
      <div className="flex shrink-0 gap-5 border-b px-6">
        <span className={`relative inline-flex h-10 items-center font-medium text-ink ${underline} after:bg-line-strong`}>
          {tabs[0].label}
        </span>
      </div>
    );
  }
  return (
    <div role="tablist" className="flex shrink-0 gap-5 border-b px-6">
      {tabs.map((tab) => {
        const isActive = tab.key === activeTab;
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onTabChange(tab.key)}
            className={`relative inline-flex h-10 items-center whitespace-nowrap rounded-sm font-medium transition-colors ${
              isActive ? `text-accent ${underline} after:bg-accent` : 'text-muted hover:text-ink'
            }`}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

/**
 * What a drawer of a holding, a closed position or a scanned stock holds, inside the shell the page keeps open
 * (`DrawerShell`). Its header — `dot`, `title`, `currency` (shown only when it isn't the account's), `tags`, ‹ › and ✕,
 * `subline`, `figures`, `warning` — stays put; only the open tab's panel (`children`) scrolls. The header's rhythm
 * (round 6): the name and its line under it 4px apart, then 16px between the blocks and 20px before the tabs.
 *
 * The panel fades in when a tab opens (round 6's Motion), and so does a new item's, since the page gives each item a
 * frame of its own: ‹ › change the header at once and fade the panel in, nothing slides.
 */
export function DrawerFrame<TabKey extends string>({
  dot,
  title,
  currency = ACCOUNT_CURRENCY,
  tags,
  subline,
  figures,
  warning,
  tabs,
  activeTab,
  onTabChange,
  navigation,
  children,
}: {
  dot?: ReactNode;
  title: ReactNode;
  currency?: string;
  tags?: ReactNode;
  subline?: ReactNode;
  figures: ReactNode;
  warning?: ReactNode;
  tabs: DrawerTab<TabKey>[];
  activeTab: TabKey;
  onTabChange: (tab: TabKey) => void;
  navigation: DrawerNavigation | null;
  children: ReactNode;
}) {
  return (
    <>
      <div className="flex flex-col gap-4 px-6 pb-5 pt-5">
        <div className="flex items-center gap-2">
          {dot}
          <DrawerTitle>{title}</DrawerTitle>
          {currency !== ACCOUNT_CURRENCY && <span className="text-faint">{currency}</span>}
          {tags}
          <HeaderButtons navigation={navigation} />
        </div>
        {subline && <div className="-mt-3 text-muted">{subline}</div>}
        {figures}
        {warning}
      </div>
      <TabBar tabs={tabs} activeTab={activeTab} onTabChange={onTabChange} />
      <div
        key={activeTab}
        role={tabs.length > 1 ? 'tabpanel' : undefined}
        className="fade-in-motion flex-1 overflow-y-auto px-6 pb-8 pt-5"
      >
        {children}
      </div>
    </>
  );
}

/**
 * One of the bordered key figures under a drawer's title: a label, the figure, and a line under it. The figure may be a
 * `SkeletonBar` while the first load runs.
 */
export function KeyFigure({
  label,
  value,
  valueColorClass = '',
  extra,
}: {
  label: string;
  value: ReactNode;
  valueColorClass?: string;
  extra?: ReactNode;
}) {
  return (
    <div className="min-w-0 px-3 py-2.5">
      <span className="block text-label text-muted">{label}</span>
      <span className={`mt-0.5 block text-base font-medium leading-[22px] ${valueColorClass}`}>{value}</span>
      {extra}
    </div>
  );
}

/** The row of five key figures, side by side with hairlines between them, of equal widths (round 6). */
export function KeyFigures({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-5 divide-x rounded-lg border">{children}</div>;
}

/** The small line under a key figure: "Me 20 · Dana 20", "+13.55%", "575 days". */
export function KeyFigureExtra({ colorClass = 'text-muted', title, children }: { colorClass?: string; title?: string; children: ReactNode }) {
  return (
    <span title={title} className={`block text-caption ${colorClass}`}>
      {children}
    </span>
  );
}
