import { useEffect } from 'react';

const APP_NAME = 'PortfolioBoss';

/**
 * The browser tab's title, the page first so that open tabs tell apart (round 6): "Positions · PortfolioBoss", and with
 * a drawer open its item before it, "NVDA · Positions · PortfolioBoss". Back to "PortfolioBoss" when the page goes —
 * during the first load no page is shown.
 */
export function useDocumentTitle(pageName: string, openItem: string | null = null) {
  useEffect(() => {
    document.title = [openItem, pageName, APP_NAME].filter((part) => part !== null).join(' · ');
    return () => {
      document.title = APP_NAME;
    };
  }, [pageName, openItem]);
}
