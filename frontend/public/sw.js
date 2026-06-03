const CACHE_NAME = 'vision-plus-v1';
const STATIC_ASSETS = [
  '/',
  '/index.html',
  // Add other static assets here if necessary
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
  // Solo interceptamos peticiones GET (no mutaciones ni video streaming en la medida de lo posible)
  if (event.request.method !== 'GET') return;
  
  // Evitar cachear llamadas de API de streaming de video (.m3u8, .mp4, etc)
  const url = new URL(event.request.url);
  if (url.pathname.includes('/api/proxy') || url.pathname.includes('/api/img-proxy') || url.pathname.endsWith('.m3u8')) {
    return;
  }

  // Network First for API calls, Cache First for static assets
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          const resClone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, resClone));
          return response;
        })
        .catch(() => caches.match(event.request))
    );
  } else {
    event.respondWith(
      caches.match(event.request).then((cachedResponse) => {
        return cachedResponse || fetch(event.request).then((response) => {
          const resClone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, resClone));
          return response;
        });
      }).catch(() => {
        // Fallback para offline
        if (event.request.mode === 'navigate') {
          return caches.match('/index.html');
        }
      })
    );
  }
});
