import { createRoot } from 'react-dom/client';
import { setBaseUrl } from '@workspace/api-client-react/custom-fetch';

import App from './App';
import { ErrorBoundary } from '@/components/error-boundary';

import './index.css';

// Detect if running in Capacitor/Android
const isNativeApp = window.location.protocol === 'capacitor:' ||
  window.location.hostname === 'localhost' && window.location.pathname.startsWith('/index.html');

// For native app: read server URL from localStorage or use default
if (isNativeApp) {
  const savedUrl = localStorage.getItem('dropdesk_server_url');
  if (savedUrl) {
    setBaseUrl(savedUrl);
  }
} else {
  // Web: use env var or default
  const apiUrl = import.meta.env.VITE_API_URL;
  if (apiUrl) {
    setBaseUrl(apiUrl);
  }
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
