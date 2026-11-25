import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import './index.css';
import App from './App.tsx';
import { HintsProvider } from './components/Hint';

// Each page has its own address (/, /positions, /closed, /investors); the browser's history moves between them. Every
// hint and "?" of the app waits by the same rule (HintsProvider).
createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <HintsProvider>
      <App />
    </HintsProvider>
  </BrowserRouter>,
);
