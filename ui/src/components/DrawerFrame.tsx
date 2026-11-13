import type { ReactNode } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { LARGE_ICON_BUTTON_CLASS } from '../lib/styles';
import { DrawerCloseButton, DrawerShell, DrawerTitle } from './DrawerShell';

export interface DrawerTab<TabKey extends string> {
  key: TabKey;
  label: string;
}

/** The underline tab bar; with a single tab, that tab as a plain heading, so the drawer keeps its shape. */
function TabBar<TabKey extends string>({
  tabs,
  activeTab,
  onTabChange,
}: {
  tabs: DrawerTab<TabKey>[];
  activeTab: TabKey;
  onTabChange: (tab: TabKey) => void;
}) {
  const underline = "after:absolute after:inset-x-2 after:-bottom-px after:h-0.5 after:content-['']";
  if (tabs.length === 1) {
    return (
      <div className="flex shrink-0 gap-1 border-b px-6">
        <span className={`relative inline-flex h-10 items-center px-2 font-medium text-ink ${underline} after:bg-line-strong`}>
          {tabs[0].label}
        </span>
      </div>
    );
  }
  return (
    <div role="tablist" className="flex shrink-0 gap-1 border-b px-6">
      {tabs.map((tab) => {
        const isActive = tab.key === activeTab;
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onTabChange(tab.key)}
            className={`relative inline-flex h-10 items-center whitespace-nowrap rounded-sm px-2 font-medium transition-colors ${
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
 * The drawer of a holding, a closed position or a scanned stock, in the shared frame (`DrawerShell`). Its header —
 * `dot`, `title`, `currency`, `tags`, ‹ › and ✕, `subline`, `figures`, `warning` — stays put; only the open tab's panel
 * (`children`) scrolls.
 */
export function DrawerFrame<TabKey extends string>({
  ariaLabel,
  dot,
  title,
  currency,
  tags,
  subline,
  figures,
  warning,
  tabs,
  activeTab,
  onTabChange,
  previousLabel,
  nextLabel,
  onPrevious,
  onNext,
  onClose,
  children,
}: {
  ariaLabel: string;
  dot?: ReactNode;
  title: string;
  currency: string;
  tags?: ReactNode;
  subline?: ReactNode;
  figures: ReactNode;
  warning?: ReactNode;
  tabs: DrawerTab<TabKey>[];
  activeTab: TabKey;
  onTabChange: (tab: TabKey) => void;
  previousLabel: string;
  nextLabel: string;
  onPrevious: () => void;
  onNext: () => void;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <DrawerShell ariaLabel={ariaLabel} onClose={onClose}>
      <div className="flex flex-col gap-3 px-6 py-4">
        <div className="flex items-center gap-2">
          {dot}
          <DrawerTitle>{title}</DrawerTitle>
          <span className="text-faint">{currency}</span>
          {tags}
          <span className="ml-auto inline-flex items-center gap-0.5">
            <button
              type="button"
              aria-label="Previous"
              title={`Previous: ${previousLabel}`}
              onClick={onPrevious}
              className={LARGE_ICON_BUTTON_CLASS}
            >
              <ChevronLeft size={18} />
            </button>
            <button
              type="button"
              aria-label="Next"
              title={`Next: ${nextLabel}`}
              onClick={onNext}
              className={LARGE_ICON_BUTTON_CLASS}
            >
              <ChevronRight size={18} />
            </button>
            <span aria-hidden="true" className="mx-1 h-4 w-px bg-line" />
            <DrawerCloseButton />
          </span>
        </div>
        {subline && <div className="-mt-1.5 text-muted">{subline}</div>}
        {figures}
        {warning}
      </div>
      <TabBar tabs={tabs} activeTab={activeTab} onTabChange={onTabChange} />
      <div role={tabs.length > 1 ? 'tabpanel' : undefined} className="flex-1 overflow-y-auto px-6 pb-8 pt-5">
        {children}
      </div>
    </DrawerShell>
  );
}

/** One of the bordered key figures under a drawer's title: a label, the figure, and a line under it. */
export function KeyFigure({
  label,
  value,
  valueColorClass = '',
  extra,
}: {
  label: string;
  value: string;
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

/** The row of five key figures, side by side with hairlines between them. */
export function KeyFigures({ children }: { children: ReactNode }) {
  return (
    <div className="grid grid-cols-[1.1fr_1fr_1.25fr_1.35fr_1fr] divide-x rounded-lg border">{children}</div>
  );
}

/** The small line under a key figure: "Me 20 · Dana 20", "+13.55%", "575 days". */
export function KeyFigureExtra({ colorClass = 'text-muted', title, children }: { colorClass?: string; title?: string; children: ReactNode }) {
  return (
    <span title={title} className={`block text-caption ${colorClass}`}>
      {children}
    </span>
  );
}
