import { createContext, useContext, useMemo } from 'react';
import useCache from '../hooks/useCache';

const CatalogContext = createContext(null);

function fetchJson(url) {
  return fetch(url).then(r => r.json());
}

export function CatalogProvider({ children }) {
  const movies = useCache('catalog_movie', () => fetchJson('/api/catalog/movie?limit=500'), 10 * 60 * 1000);
  const series = useCache('catalog_series', () => fetchJson('/api/catalog/series?limit=500'), 10 * 60 * 1000);
  const sources = useCache('sources', () => fetchJson('/api/sources?includePlutoTV=true'), 5 * 60 * 1000);

  const value = useMemo(() => ({
    movies: movies.data?.items || [],
    sources: sources.data?.sources || [],
    series: series.data?.items || [],
    categories: sources.data?.categories || [],
    categoriesDetailed: sources.data?.categoriesDetailed || [],
    loading: movies.loading || series.loading || sources.loading,
    refresh: () => { movies.refresh(); series.refresh(); sources.refresh(); },
  }), [movies.data, series.data, sources.data, movies.loading, series.loading, sources.loading]);

  return (
    <CatalogContext.Provider value={value}>
      {children}
    </CatalogContext.Provider>
  );
}

export function useCatalog() {
  const ctx = useContext(CatalogContext);
  if (!ctx) throw new Error('useCatalog must be used within CatalogProvider');
  return ctx;
}
