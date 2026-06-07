const CACHE_NAME = 'vision-plus-v4';
const STATIC_ASSETS = ['/', '/index.html'];

self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS))
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.map((key) => caches.delete(key)))
    ).then(() => caches.open(CACHE_NAME))
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;

  const url = new URL(event.request.url);
  if (url.pathname.includes('/api/proxy') || url.pathname.includes('/api/img-proxy') || url.pathname.endsWith('.m3u8') || url.pathname.endsWith('.mp4') || url.pathname.endsWith('.ts')) {
    return;
  }

  const cacheThenNetwork = () => {
    return caches.match(event.request).then((cached) => {
      const fetchPromise = fetch(event.request).then((response) => {
        if (response && response.status === 200) {
          const resClone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, resClone));
        }
        return response;
      }).catch(() => new Response(null, { status: 503 }));
      return cached || fetchPromise;
    });
  };

  if (url.pathname.startsWith('/api/')) {
    if (url.pathname.startsWith('/api/catalog/') || url.pathname === '/api/sources') {
      event.respondWith(cacheThenNetwork());
    } else {
      event.respondWith(
        fetch(event.request)
          .then((response) => {
            const resClone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, resClone));
            return response;
          })
          .catch(() => caches.match(event.request).then(cached => cached || new Response(null, { status: 503 })))
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
            return caches.match('/index.html');
          });
        })
    );
  }
});
