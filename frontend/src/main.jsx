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

  // Mobile detection
  const isMobile = !isTV && /mobile|iphone|ipod|android.*mobile|blackberry|iemobile|opera.mobi|mini|touch/i.test(ua);
  window.isMobile = isMobile;
  if (isMobile) {
    document.documentElement.classList.add('is-mobile');
    console.log('[Vision+] Mobile environment detected.');
  }

  if (isTV) {
    document.documentElement.classList.add('is-smart-tv');
    console.log('[Vision+] TV environment detected. UA:', navigator.userAgent.substring(0, 120));
    console.log('[Vision+] Applying performance optimization classes.');

    // HACK: Evitar que el botón Atrás cierre el navegador en Google TV / Chromecast
    window.history.pushState({ noExit: true }, '');
    window.addEventListener('popstate', (event) => {
      console.log('[Vision+] TV Back button intercepted via popstate');
      // Restaurar el estado falso para atrapar el próximo "Atrás"
      window.history.pushState({ noExit: true }, '');
      
      // Disparar evento Escape/Backspace globalmente para que los modales/reproductores lo capturen
      const escEvent = new KeyboardEvent('keydown', {
        key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true
      });
      document.dispatchEvent(escEvent);
    });

    // Fallback: prevenir la acción por defecto del Backspace/GoBack en teclados de TV
    window.addEventListener('keydown', (e) => {
      if (e.key === 'Backspace' || e.key === 'GoBack' || e.keyCode === 461 || e.keyCode === 8) {
        const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
        if (activeTag !== 'input' && activeTag !== 'textarea') {
          // Si no es un campo de texto, detenemos el Backspace para evitar salida del navegador
          // y dejamos que Vision+ procese el evento original una sola vez.
          e.preventDefault();
        }
      }
    }, { capture: true });
  }
})();

// Bridge: recibe eventos de teclado re-enviados desde index.html (APK Android TV via iframe)
window.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'FORWARD_KEYDOWN') {
    const d = event.data;
    document.dispatchEvent(new KeyboardEvent('keydown', {
      key: d.key, code: d.code, keyCode: d.keyCode, which: d.which, bubbles: true, cancelable: true
    }));
  }
});

// --- Auto-detección de túnel ---
// Solo aplica para Capacitor APK (Android TV). En navegador local se usa Express directo.
const GITHUB_PAGES_URL = 'https://chronosbvrx.github.io/Vision-/';
const CACHE_KEY = 'vp_tunnel_url';

async function detectTunnelUrl() {
  const hostname = window.location.hostname;
  const isOnTunnel = hostname.includes('trycloudflare.com');
  const isSmartTV = window.isSmartTV === true;
  const isDirectLocal = (hostname === 'localhost' || hostname === '127.0.0.1') && !isSmartTV;

  // Si ya estamos en un túnel, usamos URLs relativas (mismo origen) — no necesitamos CORS
  if (isOnTunnel) {
    console.log('[Tunnel] Modo túnel directo, usando URLs relativas');
    window.__API_BASE = '';
    localStorage.removeItem(CACHE_KEY);
    return;
  }

  // Capacitor APK se ejecuta en localhost pero necesita túnel
  if (isDirectLocal) {
    console.log('[Tunnel] Modo localhost directo, Express sin túnel');
    return;
  }

  const cached = localStorage.getItem(CACHE_KEY);
  if (cached) {
    window.__API_BASE = cached;
  }

  try {
    const res = await window.fetch(GITHUB_PAGES_URL);
    const html = await res.text();
    const match = html.match(/window\.location\.href\s*=\s*"([^"]+)"/) || html.match(/href="(https:\/\/[^"]+\.trycloudflare\.com[^"]*)"/);
    if (match) {
      const tunnelUrl = match[1].replace(/\/+$/, '');
      window.__API_BASE = tunnelUrl;
      localStorage.setItem(CACHE_KEY, tunnelUrl);
      console.log('[Tunnel] Detectado:', tunnelUrl);
    }
  } catch (err) {
    console.warn('[Tunnel] Error detectando URL, usando cache:', err.message);
  }

  if (!window.__API_BASE) {
    console.warn('[Tunnel] No hay URL de túnel disponible');
  }
}

// Interceptar peticiones fetch a /api para direccionarlas al backend
const originalFetch = window.fetch;
window.fetch = (input, init) => {
  if (typeof input === 'string' && input.startsWith('/api')) {
    const hostname = window.location.hostname;
    const isSmartTV = window.isSmartTV === true;
    const isDirectLocal = (hostname === 'localhost' || hostname === '127.0.0.1') && !isSmartTV;
    if (!isDirectLocal) {
      input = (window.__API_BASE || '') + input;
    }
  }
  return originalFetch(input, init);
};

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { CatalogProvider } from './context/CatalogContext.jsx'

// Detectar túnel antes de montar React para que CatalogProvider tenga la URL lista
detectTunnelUrl().then(() => {
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <CatalogProvider>
        <App />
      </CatalogProvider>
    </StrictMode>,
  );
});

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
