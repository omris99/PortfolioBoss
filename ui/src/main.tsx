import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import './index.css';
import App from './App.tsx';

// Each page has its own address (/, /positions, /closed, /investors); the browser's history moves between them.
createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>,
);
