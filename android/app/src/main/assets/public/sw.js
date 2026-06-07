const CACHE_NAME = 'vision-plus-v2';
const STATIC_ASSETS = [
  '/',
  '/index.html',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS);
    })
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    })
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  // Solo interceptamos peticiones GET
  if (event.request.method !== 'GET') return;
  
  // Evitar cachear llamadas de API de streaming de video o imagenes
  const url = new URL(event.request.url);
  if (url.pathname.includes('/api/proxy') || url.pathname.includes('/api/img-proxy') || url.pathname.endsWith('.m3u8') || url.pathname.endsWith('.mp4') || url.pathname.endsWith('.ts')) {
    return;
  }

  // Cache-First for catalog/sources API (instant load), Network-First for the rest
  if (url.pathname.startsWith('/api/')) {
    const isCachedEndpoint = url.pathname.startsWith('/api/catalog/') || url.pathname === '/api/sources';

    if (isCachedEndpoint) {
      event.respondWith(
        caches.match(event.request).then((cached) => {
          const fetchPromise = fetch(event.request).then((response) => {
            if (response && response.status === 200) {
              const resClone = response.clone();
              caches.open(CACHE_NAME).then((cache) => cache.put(event.request, resClone));
            }
            return response;
          }).catch(() => cached);
          return cached || fetchPromise;
        })
      );
    } else {
      event.respondWith(
        fetch(event.request)
          .then((response) => {
            const resClone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, resClone));
            return response;
          })
          .catch(() => caches.match(event.request))
      );
    }
  } else {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          if (response && response.status === 200) {
            const resClone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, resClone));
          }
          return response;
        })
        .catch(() => {
          return caches.match(event.request).then((cachedResponse) => {
            if (cachedResponse) return cachedResponse;
            // Fallback para offline
            if (event.request.mode === 'navigate') {
              return caches.match('/index.html');
            }
          });
        })
    );
  }
});
