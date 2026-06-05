// Detect Smart TV / Android TV / WebView wrapper and append class to <html>
(function() {
  const ua = navigator.userAgent.toLowerCase();

  // CrKey covers Chromecast (1st gen dongle).
  // GoogleTV / googletv covers Chromecast with Google TV (2020+) and Google TV native browser.
  // Android TV WebView wrapper: has 'wv' AND 'android' but NOT 'mobile' (TV has no mobile UA).
  // Additional coverage: common OLED/QLED manufacturers and stick devices.
  const isTV =
    /smart-tv|smarttv|googletv|appletv|hbbtv|netcast|opera\.tv|opera\.mini|netfront|viera|bravia|tizen|webos|web0s|playstation|xbox|nintendo.switch|crkey|aftb|afts|aftt|firetv|roku|mitv|xiaomi|hisense|philips|sharp|panasonic|toshiba|sceptre|insignia|vizio|chromecast|google.tv|androidtv|android.tv/i.test(ua) ||
    // Android TV: has 'android' + 'tv' in UA, or is a WebView on Android without 'mobile'
    (ua.includes('android') && (ua.includes(' tv') || ua.includes('tv '))) ||
    // Android WebView wrapper (our APK): contains 'wv' and 'android' without 'mobile'
    (ua.includes('wv') && ua.includes('android') && !ua.includes('mobile')) ||
    // Allow manual override via URL param for testing or Chromecast side-loading
    window.location.search.includes('tv=true') ||
    window.location.search.includes('apk=true') ||
    window.location.search.includes('googletv=1');

  window.isSmartTV = isTV;
  if (isTV) {
    document.documentElement.classList.add('is-smart-tv');
    console.log('[Vision+] TV environment detected. UA:', navigator.userAgent.substring(0, 120));
    console.log('[Vision+] Applying performance optimization classes.');
  }
})();

// Interceptar peticiones fetch a /api para direccionarlas al backend en producción
const originalFetch = window.fetch;
window.fetch = (input, init) => {
  if (typeof input === 'string' && input.startsWith('/api')) {
    const apiBase = import.meta.env.VITE_API_URL || '';
    input = apiBase + input;
  }
  return originalFetch(input, init);
};

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { CatalogProvider } from './context/CatalogContext.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <CatalogProvider>
      <App />
    </CatalogProvider>
  </StrictMode>,
)

// Register Service Worker for offline capabilities with auto-refresh on update
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').then((registration) => {
      console.log('SW registered with scope:', registration.scope);
      
      // Auto-reload the app when a new Service Worker updates/installs successfully
      registration.onupdatefound = () => {
        const installingWorker = registration.installing;
        if (installingWorker == null) return;
        
        installingWorker.onstatechange = () => {
          if (installingWorker.state === 'installed') {
            if (navigator.serviceWorker.controller) {
              console.log('[Vision+] New version available. Refreshing page to apply updates.');
              window.location.reload();
            }
          }
        };
      };
    }).catch((error) => {
      console.error('SW registration failed:', error);
    });
  });
}
