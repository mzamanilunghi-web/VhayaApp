import { createRoot } from 'react-dom/client';

import App from './App';
import { ErrorBoundary } from '@/components/error-boundary';

import 'leaflet/dist/leaflet.css';
import './index.css';

try {
  const savedTheme = window.localStorage.getItem('vhaya-dark');
  document.documentElement.classList.toggle('dark', savedTheme !== 'false');
} catch {
  document.documentElement.classList.add('dark');
}

createRoot(document.getElementById('root')!, {
  // Keeps caught errors off reportError(), which would raise the dev overlay.
  onCaughtError: (error, errorInfo) => {
    console.error(error, errorInfo.componentStack);
  },
}).render(
  <ErrorBoundary>
    <App />
  </ErrorBoundary>,
);
