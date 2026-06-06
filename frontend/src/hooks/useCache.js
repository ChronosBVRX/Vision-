import { useState, useEffect, useRef, useCallback } from 'react';

const DEFAULT_TTL = 5 * 60 * 1000;
const APP_VERSION = typeof import.meta !== 'undefined' && import.meta.env ? import.meta.env.VITE_APP_VERSION : '1.0.0';
const CACHE_PREFIX = 'vp_cache_' + APP_VERSION + '_';

function getCacheItem(key) {
  try {
    const raw = localStorage.getItem(CACHE_PREFIX + key);
    if (!raw) return null;
    const item = JSON.parse(raw);
    if (Date.now() - item.timestamp > item.ttl) {
      localStorage.removeItem(CACHE_PREFIX + key);
      return null;
    }
    return item.data;
  } catch {
    return null;
  }
}

function setCacheItem(key, data, ttl) {
  try {
    localStorage.setItem(CACHE_PREFIX + key, JSON.stringify({ data, timestamp: Date.now(), ttl }));
  } catch {
    // localStorage may be full
  }
}

export default function useCache(key, fetcher, ttl = DEFAULT_TTL) {
  // Allow user override via localStorage
  const userTtlKey = key.startsWith('catalog_') ? 'vp_ttl_catalog' : (key === 'sources' ? 'vp_ttl_sources' : null);
  const userTtl = userTtlKey ? parseInt(localStorage.getItem(userTtlKey), 10) : null;
  if (userTtl && userTtl > 0) ttl = userTtl * 60 * 1000;
  const [data, setData] = useState(() => getCacheItem(key));
  const [loading, setLoading] = useState(!data);
  const [error, setError] = useState(null);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const refresh = useCallback(() => {
    setLoading(true);
    const promise = fetcherRef.current();
    (promise.then ? promise : Promise.resolve(promise))
      .then(result => {
        setData(result);
        setError(null);
        setCacheItem(key, result, ttl);
      })
      .catch(err => {
        if (!data) setError(err);
      })
      .finally(() => setLoading(false));
  }, [key, ttl, data]);

  useEffect(() => {
    if (!data) {
      refresh();
    } else {
      const cached = getCacheItem(key);
      if (!cached) {
        refresh();
      } else {
        const raw = localStorage.getItem(CACHE_PREFIX + key);
        if (raw) {
          const item = JSON.parse(raw);
          if (Date.now() - item.timestamp > item.ttl * 0.5) {
            refresh();
          }
        }
      }
    }
  }, [key]);

  return { data, loading, error, refresh };
}
