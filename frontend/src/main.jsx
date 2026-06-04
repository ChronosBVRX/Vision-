// Detect Smart TV / Android TV / WebView wrapper and append class to <html>
(function() {
  const ua = navigator.userAgent.toLowerCase();
  const isTV = 
    /smart-tv|smarttv|googletv|appletv|hbbtv|netcast|opera.tv|opera.mini|netfront|viera|bravia|tizen|webos|web0s|playstation|playstation 4|xbox|playstation 5|nintendo switch|crkey|aftb|afts|firetv|roku|mitv|xiaomi|bravia|hisense|philips|sharp|panasonic|toshiba|sceptre|insignia|vizio/i.test(ua) ||
    (ua.includes('android') && ua.includes('tv')) ||
    ua.includes('smart-tv') ||
    window.location.search.includes('tv=true') ||
    window.location.search.includes('apk=true') ||
    (ua.includes('wv') && ua.includes('android')); // WebView wrapping on Android TV
  
  window.isSmartTV = isTV;
  if (isTV) {
    document.documentElement.classList.add('is-smart-tv');
    console.log('[Vision+] TV environment detected. Applying performance optimization classes.');
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

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

// Register Service Worker for offline capabilities
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').then((registration) => {
      console.log('SW registered with scope:', registration.scope);
    }).catch((error) => {
      console.error('SW registration failed:', error);
    });
  });
}
