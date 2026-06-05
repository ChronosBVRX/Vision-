import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { Play, RefreshCw, Film, Tv, Loader, X, Search } from 'lucide-react';
import VideoPlayer from '../components/VideoPlayer';
import DetailsModal from '../components/DetailsModal';
import { HeroBanner, CatalogRow, CatalogCard } from '../components/CatalogComponents';
import LoadingScreen from '../components/LoadingScreen';
import useCache from '../hooks/useCache';



// ─── Genre priority order ─────────────────────────────────────────────────────
const GENRE_ORDER = [
  '⭐ Destacadas', '🔥 Recientes',
  'Acción', 'Drama', 'Comedia', 'Terror', 'Sci-Fi',
  'Suspenso', 'Romance', 'Aventura', 'Animación',
  'Crimen', 'Documental', 'Familia', 'Fantasía',
  'Misterio', 'Historia', 'Musical', 'Guerra', 'Western', 'Infantil', 'General'
];

// ─── Main Movies/Series Catalog Page ─────────────────────────────────────────
export default function Movies({ contentType = 'movie' }) {
  const { data: catalogData, loading: cacheLoading, refresh: refreshCache } = useCache(`catalog_${contentType}`, () =>
    fetch(`/api/catalog/${contentType}?limit=500`).then(r => r.json()), 10 * 60 * 1000
  );
  const [catalog, setCatalog]       = useState([]);
  const [isLoading, setIsLoading]   = useState(true);
  const [isSyncing, setIsSyncing]   = useState(false);
  const [syncMsg, setSyncMsg]       = useState('');
  const [activeSeries, setActiveSeries] = useState(null);
  const [seriesSeasons, setSeriesSeasons] = useState([]);
  const [isLoadingSeries, setIsLoadingSeries] = useState(false);
  const [seriesError, setSeriesError] = useState(null);
  const [selectedSeasonIdx, setSelectedSeasonIdx] = useState(0);
  const epSelectorRef = useRef(null);
  const [activeItem, setActiveItem] = useState(null);
  const [detailsItem, setDetailsItem] = useState(null);
  const [activeRow, setActiveRow]   = useState(0);
  const [isResolving, setIsResolving] = useState(false);
  const [resolveAttempts, setResolveAttempts] = useState([]);
  const [resolveError, setResolveError] = useState(null);
  const pageRef = useRef(null);
  const resolvingRef = useRef(false);
  const resolveErrorBtnRef = useRef(null);
  const emptyCatalogRef = useRef(null);

  useEffect(() => {
    if (catalog.length === 0) {
      setTimeout(() => emptyCatalogRef.current?.focus(), 100);
    }
  }, [catalog.length]);

  // Auto-focus close button when error overlay appears
  useEffect(() => {
    if (resolveError) setTimeout(() => resolveErrorBtnRef.current?.focus(), 100);
  }, [resolveError]);

  // Search and filter states
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedGenre, setSelectedGenre] = useState('Todos');

  // Dynamic genres lists
  const allGenres = useMemo(() => {
    const genresSet = new Set();
    catalog.forEach(item => {
      if (item.genres && Array.isArray(item.genres)) {
        item.genres.forEach(g => {
          if (g && !g.startsWith('⭐') && !g.startsWith('🔥') && !g.startsWith('🎬') && g !== 'General') {
            genresSet.add(g);
          }
        });
      }
    });
    const sorted = Array.from(genresSet).sort();
    return ['Todos', ...sorted];
  }, [catalog]);

  // Filtered catalog content based on genre selection and search query
  const filteredCatalog = useMemo(() => {
    let result = catalog;
    if (selectedGenre !== 'Todos') {
      result = result.filter(item => item.genres && item.genres.includes(selectedGenre));
    }
    if (searchQuery.trim().length > 0) {
      const q = searchQuery.toLowerCase().trim();
      result = result.filter(item => 
        (item.title && item.title.toLowerCase().includes(q)) ||
        (item.description && item.description.toLowerCase().includes(q)) ||
        (item.genres && item.genres.some(g => g.toLowerCase().includes(q)))
      );
    }
    return result;
  }, [catalog, selectedGenre, searchQuery]);

  const isFilteringOrSearching = selectedGenre !== 'Todos' || searchQuery.trim().length > 0;

  const handlePlay = useCallback((item) => {
    setDetailsItem(null);
    if (!item) return;

    // Prevent concurrent resolution attempts (e.g. double click or StrictMode)
    if (resolvingRef.current) {
      console.log("[Movies] Ya hay una resolución en progreso, ignorando click.");
      return;
    }

    // Sports and already-resolved items go directly to player
    if (item.provider === 'internal-resolver' || item.isSports || item.type === 'tv') {
      setActiveItem(item);
      return;
    }

    // If it's a series, open episode selector
    if (item.type === 'series' || item.type === 'anime_series') {
      setActiveSeries(item);
      setSelectedSeasonIdx(0);
      setIsLoadingSeries(true);
      setSeriesError(null);
      setSeriesSeasons([]);
      fetch(`/api/series/${item.id}/episodes`)
        .then(r => r.json())
        .then(data => {
          if (data.success && data.seasons && data.seasons.length > 0) {
            setSeriesSeasons(data.seasons);
          } else {
            setSeriesError("No se encontraron temporadas/episodios para esta serie.");
          }
        })
        .catch(err => setSeriesError("Error al cargar episodios: " + err.message))
        .finally(() => setIsLoadingSeries(false));
      return;
    }

    // Show loading state while backend resolves (for movies)
    resolvingRef.current = true;
    setIsResolving(true);
    setResolveError(null);
    setResolveAttempts([]);

    // Show a transient loading overlay (item with isResolving flag)
    setActiveItem({ ...item, isResolving: true });

    // [antigravity] Usar directamente el endpoint /resolve (ya probado y funcional).
    // El endpoint /play duplicaba las sesiones de Playwright y saturaba el servidor.
    fetch(`/api/movies/${item.id}/resolve?lang=auto&debug=true`)
      .then(r => r.json())
      .then(data => {
        if (data.success) {
          const playableSource = {
            id: item.id,
            title: item.title,
            type: item.type,
            poster: item.poster,
            provider: "internal-resolver",
            selectedLanguage: data.selectedLanguage,
            selectedServer: data.selectedServer,
            streams: [
              {
                name: data.selectedServer,
                url: data.stream.url,
                type: data.stream.type,
                headers: data.stream.headers || {},
                quality: data.stream.quality || "auto",
                resolver: "direct"
              }
            ],
            alternatives: [],
            attempts: data.attempts || []
          };
          setActiveItem(playableSource);
          setIsResolving(false);
        } else {
          setActiveItem(null);
          setIsResolving(false);
          setResolveError(data.error || "No hay fuentes reproducibles disponibles en este momento.");
          setResolveAttempts(data.attempts || []);
        }
      })
      .catch(err => {
        console.error("Resolution error:", err);
        setActiveItem(null);
        setIsResolving(false);
        setResolveError("Error de conexión al resolver la fuente. Intenta de nuevo.");
      })
      .finally(() => {
        setIsResolving(false);
        resolvingRef.current = false;
      });
  }, []);

  const playEpisode = useCallback((series, episode) => {
    setActiveSeries(null); // close modal
    resolvingRef.current = true;
    setIsResolving(true);
    setResolveError(null);
    setResolveAttempts([]);
    setActiveItem({ ...series, title: `${series.title} - ${episode.title}`, isResolving: true });

    fetch(`/api/series/episode/resolve?url=${encodeURIComponent(episode.url)}&siteName=${encodeURIComponent(series.siteName || '')}&lang=auto&debug=true`)
      .then(r => r.json())
      .then(data => {
        if (data.success) {
          const playableSource = {
            id: series.id,
            title: `${series.title} - ${episode.title}`,
            type: series.type,
            poster: series.poster,
            provider: "internal-resolver",
            selectedLanguage: data.selectedLanguage,
            selectedServer: data.selectedServer,
            streams: [
              {
                name: data.selectedServer,
                url: data.stream.url,
                type: data.stream.type,
                headers: data.stream.headers || {},
                quality: data.stream.quality || "auto",
                resolver: "direct"
              }
            ],
            attempts: data.attempts || [],
            // Extra series details:
            isSeriesEpisode: true,
            seriesInfo: series,
            seasons: seriesSeasons,
            currentEpisode: episode
          };
          setActiveItem(playableSource);
          setIsResolving(false);
        } else {
          setActiveItem(null);
          setIsResolving(false);
          setResolveError(data.error || "No hay fuentes reproducibles disponibles para este episodio.");
          setResolveAttempts(data.attempts || []);
        }
      })
      .catch(err => {
        console.error("Resolution error:", err);
        setActiveItem(null);
        setIsResolving(false);
        setResolveError("Error de conexión al resolver el episodio. Intenta de nuevo.");
      })
      .finally(() => {
        setIsResolving(false);
        resolvingRef.current = false;
      });
  }, [seriesSeasons]);

  const isMovie = contentType === 'movie';
  const pageTitle = isMovie ? 'Películas' : 'Series';

  const normalizeCatalogItems = useCallback((items) => {
    return (items || []).map(item => {
      const normGenres = (item.genres || []).map(g => 
        g.replace(/^(Pluto TV - |Planeta Play - |Canales - )/i, '').trim()
      );
      return {
        ...item,
        genres: normGenres
      };
    });
  }, []);

  // Sync catalog from cache/background refresh
  useEffect(() => {
    if (!catalogData) return;
    setCatalog(normalizeCatalogItems(catalogData.items || []));
    if (catalogData.syncing) {
      setIsSyncing(true);
    } else {
      setIsSyncing(false);
    }
    setIsLoading(false);
  }, [catalogData, normalizeCatalogItems]);

  useEffect(() => {
    setActiveRow(0);
    setSearchQuery('');
    setSelectedGenre('Todos');
  }, [contentType]);

  // Poll for sync completion if syncing
  useEffect(() => {
    if (!isSyncing) return;
    const interval = setInterval(() => {
      refreshCache();
    }, 8000);
    const timeout = setTimeout(() => {
      setIsSyncing(false);
      setSyncMsg('La sincronización tomó demasiado tiempo.');
      setTimeout(() => setSyncMsg(''), 4000);
    }, 120000);
    return () => { clearInterval(interval); clearTimeout(timeout); };
  }, [isSyncing, refreshCache]);

  // Show sync completion message when syncing stops
  useEffect(() => {
    if (!isSyncing && syncMsg && !syncMsg.includes('tomó')) {
      setSyncMsg('¡Catálogo actualizado!');
      setTimeout(() => setSyncMsg(''), 4000);
    }
  }, [isSyncing]);

  // Trigger background sync
  const triggerSync = () => {
    setIsSyncing(true);
    setSyncMsg('Sincronizando desde PelisPlus, Cuevana, PelisOnline...');
    fetch('/api/catalog/sync', { method: 'POST' })
      .then(r => r.json())
      .then(data => { if (!data.syncing) setSyncMsg(data.message || ''); })
      .catch(() => { setIsSyncing(false); setSyncMsg('Error al sincronizar.'); });
  };



  // ── Expanded genre grid state ──────────────────────────────────────────────
  const [expandedGenre, setExpandedGenre] = useState(null);

  // ── Group catalog into genre rows ────────────────────────────────────────────
  const genreGroups = useMemo(() => {
    const g = {};
    const topRated = catalog.filter(m => (m.rating && m.rating >= 7) || m.featured);
    if (topRated.length > 0) g['⭐ Destacadas'] = topRated;
    if (catalog.length > 0)  g['🔥 Recientes']  = catalog.slice(0, 50);
    catalog.forEach(item => {
      const genres = item.genres?.length > 0 ? item.genres : ['General'];
      genres.forEach(genre => {
        if (!g[genre]) g[genre] = [];
        g[genre].push(item);
      });
    });
    return g;
  }, [catalog]);

  // All items for the expanded genre grid
  const allExpandedItems = useMemo(() => {
    if (!expandedGenre) return [];
    return genreGroups[expandedGenre] || [];
  }, [expandedGenre, genreGroups]);

  const rowKeys = useMemo(() => {
    const keys = Object.keys(genreGroups);
    return [
      ...GENRE_ORDER.filter(k => keys.includes(k)),
      ...keys.filter(k => !GENRE_ORDER.includes(k))
    ];
  }, [genreGroups]);

  // ── TV keyboard navigation ─────────────────────────────────────────────────
  useEffect(() => {
    if (activeItem || activeSeries) return;
    const isTV = typeof window !== 'undefined' && window.isSmartTV;
    const scrollOpt = { behavior: isTV ? 'auto' : 'smooth', block: 'nearest' };
    const handleKey = (e) => {
      const activeEl = document.activeElement;
      const inSidebar = activeEl && !!activeEl.closest('.sidebar');
      if (inSidebar) return; // Let the sidebar handle its own Up/Down navigation

      // If in grid view, let useSpatialNavigation handle ArrowUp/Down
      if (isFilteringOrSearching) return;

      const inTopBar = activeEl && !!activeEl.closest('.catalog-filter-bar');

      if (['ArrowUp', 'ArrowDown'].includes(e.key)) {
        if (inTopBar) {
          if (e.key === 'ArrowDown') {
            e.preventDefault();
            setActiveRow(0);
            const firstCard = document.querySelector('#crow-0 .catalog-card');
            firstCard?.focus();
          }
          return;
        }

        e.preventDefault();
        setActiveRow(prev => {
          if (e.key === 'ArrowUp') {
            if (prev === 0) {
              const topBarItems = Array.from(document.querySelectorAll('.catalog-filter-bar .focusable'));
              if (topBarItems.length > 0) {
                topBarItems[0].focus();
                window.scrollTo({ top: 0, behavior: isTV ? 'auto' : 'smooth' });
              }
              return 0;
            }
            const next = prev - 1;
            const el = document.getElementById(`crow-${next}`);
            el?.scrollIntoView(scrollOpt);
            return next;
          } else {
            const next = Math.min(rowKeys.length - 1, prev + 1);
            const el = document.getElementById(`crow-${next}`);
            el?.scrollIntoView(scrollOpt);
            return next;
          }
        });
      }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [activeItem, rowKeys.length, isFilteringOrSearching]);

  // When user clicks a card, show details first
  const handleDetails = useCallback((item) => {
    setDetailsItem(item);
  }, []);



  // ── Episode selector: auto-focus on mount ───────────────────────────────────
  useEffect(() => {
    if (!activeSeries || isLoadingSeries) return;
    const timer = setTimeout(() => {
      const target = document.querySelector('.season-tab') || document.querySelector('.episode-card');
      if (target) target.focus();
    }, 50);
    return () => clearTimeout(timer);
  }, [activeSeries, isLoadingSeries]);

  // ── Episode selector keyboard navigation ────────────────────────────────────
  useEffect(() => {
    if (!activeSeries) return;
    const handleKey = (e) => {
      const el = document.activeElement;

      // Stop ALL arrow keys from reaching the catalog handler behind
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
        e.preventDefault();
        e.stopPropagation();
      }

      if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'BrowserBack' || e.key === 'GoBack') {
        setActiveSeries(null);
        return;
      }
      if (e.key === 'Enter') {
        const insideOverlay = el && el.closest('.ep-selector-overlay');
        if (insideOverlay && (el?.tagName === 'BUTTON' || el?.tagName === 'A')) {
          el.click();
        } else if (!insideOverlay || el === document.body) {
          const first = document.querySelector('.episode-card') || document.querySelector('.season-tab');
          if (first) first.focus();
        }
        return;
      }

      // No focus inside overlay → focus first element
      const insideOverlay = el && el.closest('.ep-selector-overlay');
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key) && !insideOverlay) {
        const first = document.querySelector('.episode-card') || document.querySelector('.season-tab');
        if (first) first.focus();
        return;
      }

      if (el?.classList.contains('season-tab') && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
        const tabs = document.querySelectorAll('.season-tab');
        if (!tabs.length) return;
        const cur = Array.from(tabs).indexOf(el);
        const dir = e.key === 'ArrowRight' ? 1 : -1;
        const next = (cur + dir + tabs.length) % tabs.length;
        setSelectedSeasonIdx(next);
        setTimeout(() => {
          const firstEp = document.querySelector('.episode-card');
          if (firstEp) firstEp.focus();
          else tabs[next]?.focus();
        }, 50);
        return;
      }
      if (el?.classList.contains('episode-card')) {
        const cards = Array.from(document.querySelectorAll('.episode-card'));
        const cur = cards.indexOf(el);
        if (cur === -1) return;
        const cols = 4;
        let target = -1;
        if (e.key === 'ArrowRight') target = cur + 1;
        else if (e.key === 'ArrowLeft') target = cur - 1;
        else if (e.key === 'ArrowDown') target = cur + cols;
        else if (e.key === 'ArrowUp') target = cur - cols;
        if (target >= 0 && target < cards.length) {
          cards[target].focus();
        }
      }
    };
    window.addEventListener('keydown', handleKey, true);
    return () => window.removeEventListener('keydown', handleKey, true);
  }, [activeSeries]);

  // ── Handle source change (server switch) ───────────────────────────────────
  const handleSourceChange = useCallback((updatedSource) => {
    setActiveItem(updatedSource);
  }, []);

  // ── Handle play next ────────────────────────────────────────────────────────
  const playNext = useCallback(() => {
    if (!activeItem) return;
    const idx = catalog.findIndex(m => m.id === activeItem.id);
    if (idx >= 0 && idx < catalog.length - 1) handlePlay(catalog[idx + 1]);
  }, [activeItem, catalog, handlePlay]);

  if (isLoading) {
    return <LoadingScreen type="catalog" title={`Cargando ${pageTitle}...`} />;
  }

  // ── Empty state ─────────────────────────────────────────────────────────────
  if (catalog.length === 0) {
    return (
      <div className="catalog-empty">
        <div className="catalog-empty-icon">
          {isMovie ? <Film size={80} /> : <Tv size={80} />}
        </div>
        <h2 className="catalog-empty-title">
          {isSyncing ? 'Indexando catálogo...' : `No hay ${pageTitle} aún`}
        </h2>
        <p className="catalog-empty-sub">
          {isSyncing
            ? `Buscando contenido en PelisPlus, Cuevana, PoseidonHD y más sitios. Esto puede tardar 2-3 minutos...`
            : `Sincroniza para descubrir contenido de PelisPlus, Cuevana, PelisOnline, PoseidonHD y más.`
          }
        </p>
        {isSyncing ? (
          <div className="catalog-sync-progress">
            <RefreshCw size={20} className="spin-anim" />
            <span>{syncMsg || 'Sincronizando...'}</span>
          </div>
        ) : (
          <button ref={emptyCatalogRef} className="btn btn-primary catalog-sync-btn focusable" tabIndex={0} onClick={triggerSync}>
            <RefreshCw size={16} />
            Sincronizar Catálogo
          </button>
        )}
      </div>
    );
  }

  return (
    <div ref={pageRef} className="catalog-page catalog-page--no-hero">

      {/* ── SEARCH & CATEGORY FILTER BAR ──────────────────────────────────── */}
      <div className="catalog-filter-bar">
        {/* Search wrapper */}
        <div className="catalog-search-wrapper">
          <input
            type="text"
            className="catalog-search-input focusable"
            placeholder={`Buscar por título, género...`}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            tabIndex={0}
          />
          <Search className="catalog-search-icon" size={18} />
          {searchQuery && (
            <button 
              className="catalog-search-clear focusable"
              onClick={() => setSearchQuery('')}
              tabIndex={0}
              title="Limpiar búsqueda"
            >
              <X size={16} />
            </button>
          )}
        </div>

        {/* Category/Genre chips */}
        <div className="catalog-genre-chips-container">
          {allGenres.map((genre) => (
            <button
              key={genre}
              className={`catalog-genre-chip-pill focusable ${selectedGenre === genre ? 'active' : ''}`}
              onClick={() => setSelectedGenre(genre)}
              tabIndex={0}
            >
              {genre === 'Todos' ? '🌐 Todos' : genre}
            </button>
          ))}
        </div>
      </div>



      {/* ── SYNC STATUS BAR ───────────────────────────────────────────────── */}
      {(isSyncing || syncMsg) && (
        <div className="catalog-sync-bar">
          {isSyncing && <RefreshCw size={14} className="spin-anim" />}
          <span>{syncMsg || 'Sincronizando catálogo en segundo plano...'}</span>
        </div>
      )}

      {/* ── CONTENT (GRID OR ROWS) ────────────────────────────────────────── */}
      {isFilteringOrSearching ? (
        <>
          <div className="catalog-grid-header">
            <h2>{searchQuery ? 'Resultados de Búsqueda' : `Categoría: ${selectedGenre}`}</h2>
            <span className="catalog-grid-count">
              {filteredCatalog.length} {filteredCatalog.length === 1 ? 'título encontrado' : 'títulos encontrados'}
            </span>
          </div>

          {filteredCatalog.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '80px 20px', color: 'var(--text-secondary)' }}>
              <p style={{ fontSize: '1.2rem', marginBottom: '10px', fontWeight: 600 }}>No se encontraron títulos coincidentes.</p>
              <p style={{ fontSize: '0.9rem' }}>Intenta con palabras clave diferentes o selecciona otra categoría.</p>
            </div>
          ) : (
            <div className="catalog-grid">
              {filteredCatalog.map((item, idx) => (
                <CatalogCard
                  key={`${item.id}-grid-${idx}`}
                  item={item}
                  onPlay={handleDetails}
                  onFocus={() => {}}
                />
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="catalog-rows">
          {rowKeys.map((genre, rowIdx) => {
            const genreItems = genreGroups[genre] || [];
            return (
              <div key={genre} className="catalog-row-group">
                <CatalogRow
                  id={`crow-${rowIdx}`}
                  title={genre}
                  items={genreItems}
                  isActive={activeRow === rowIdx}
                  onPlay={handleDetails}
                  onFocus={() => setActiveRow(rowIdx)}
                />
                {genreItems.length > 50 && (
                  <button
                    className="catalog-row-showall focusable"
                    tabIndex={0}
                    onClick={() => setExpandedGenre(expandedGenre === genre ? null : genre)}
                  >
                    {expandedGenre === genre ? '▲ Mostrar menos' : `Ver todas (${genreItems.length}) →`}
                  </button>
                )}
                {expandedGenre === genre && (
                  <div className="catalog-grid">
                    {allExpandedItems.map((item, idx) => (
                      <CatalogCard
                        key={`${item.id}-full-${idx}`}
                        item={item}
                        onPlay={handleDetails}
                        onFocus={() => {}}
                      />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* ── FOOTER INFO ───────────────────────────────────────────────────── */}
      <div className="catalog-footer">
        <span>{catalog.length} {isMovie ? 'películas' : 'series'} en catálogo</span>
        <button
          className="catalog-footer-sync"
          onClick={triggerSync}
          disabled={isSyncing}
        >
          {isSyncing ? <><RefreshCw size={12} className="spin-anim" /> Sincronizando...</> : '↻ Actualizar catálogo'}
        </button>
      </div>

      {/* ── RESOLVER LOADING OVERLAY ──────────────────────────────────────── */}
      {(isResolving || (activeItem && activeItem.isResolving)) && (
        <LoadingScreen 
          type="resolver" 
          title={activeItem?.title || `Cargando ${pageTitle.toLowerCase()}...`} 
          onCancel={() => { setActiveItem(null); setIsResolving(false); }} 
        />
      )}

      {/* ── RESOLVER ERROR / DEBUG OVERLAY ────────────────────────────────── */}
      {resolveError && (
        <div className="watch-overlay" style={{ justifyContent: 'center', alignItems: 'center', zIndex: 9999 }}>
          <div style={{ textAlign: 'center', maxWidth: '550px', padding: '24px' }} className="glass-panel form-card">
            <h3 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--accent)', marginBottom: '12px' }}>
              Error de Reproducción
            </h3>
            <p className="text-secondary" style={{ marginBottom: '20px', fontSize: '0.95rem' }}>
              {resolveError}
            </p>
            
            {import.meta.env.VITE_DEBUG_SOURCES === 'true' && resolveAttempts.length > 0 && (
              <div style={{ textAlign: 'left', maxHeight: '200px', overflowY: 'auto', marginBottom: '20px', background: 'rgba(0,0,0,0.3)', padding: '12px', borderRadius: '6px' }}>
                <h4 style={{ fontSize: '0.9rem', color: '#fff', marginBottom: '8px' }}>Intentos de resolución:</h4>
                {resolveAttempts.map((att, i) => (
                  <div key={i} style={{ fontSize: '0.8rem', color: att.status === 'SUCCESS' ? '#4caf50' : '#f44336', marginBottom: '4px' }}>
                    • [{att.language}] {att.sourceName}: {att.status} ({att.reason})
                  </div>
                ))}
              </div>
            )}
            
            <button ref={resolveErrorBtnRef} className="btn btn-secondary focusable" tabIndex={0} onClick={() => setResolveError(null)}>
              <X size={18} /> Cerrar
            </button>
          </div>
        </div>
      )}

      {/* ── DETAILS MODAL ─────────────────────────────────────────────────── */}
      {detailsItem && (
        <DetailsModal 
          item={detailsItem} 
          onClose={() => setDetailsItem(null)} 
          onPlay={handlePlay} 
        />
      )}

      {/* ── VIDEO PLAYER OVERLAY ──────────────────────────────────────────── */}
      {activeItem && !activeItem.isResolving && (
        <VideoPlayer
          source={activeItem}
          onClose={() => setActiveItem(null)}
          onNext={playNext}
          onNextEpisode={setActiveItem}
          onPrevEpisode={setActiveItem}
          onSourceChange={handleSourceChange}
        />
      )}

      {/* ── EPISODE SELECTOR OVERLAY ─────────────────────────────────────── */}
      {activeSeries && (
        <div ref={epSelectorRef} className="watch-overlay ep-selector-overlay" style={{ justifyContent: 'center', alignItems: 'center', zIndex: 9998, padding: '20px' }}>
          <div className="glass-panel ep-selector-panel">
            <div className="ep-selector-header">
              <div>
                <h2>{activeSeries.title}</h2>
                <p>Selecciona temporada y episodio</p>
              </div>
              <button className="watch-close focusable" tabIndex={0} onClick={() => setActiveSeries(null)}>
                <X size={24} />
              </button>
            </div>

            {seriesSeasons.length > 1 && (
              <div className="ep-season-tabs">
                {seriesSeasons.map((season, i) => (
                  <button
                    key={i}
                    className={`season-tab focusable ${selectedSeasonIdx === i ? 'active' : ''}`}
                    tabIndex={0}
                    onClick={() => setSelectedSeasonIdx(i)}
                  >
                    {season.title}
                  </button>
                ))}
              </div>
            )}

            <div className="ep-selector-body">
              {isLoadingSeries ? (
                <div className="ep-selector-loading">
                  <Loader className="spin-anim" size={40} style={{ color: 'var(--primary-light)' }} />
                  <p>Buscando capítulos disponibles...</p>
                </div>
              ) : seriesError ? (
                <div className="ep-selector-loading">
                  <p className="ep-error-msg">{seriesError}</p>
                  <button className="btn btn-secondary focusable" tabIndex={0} onClick={() => setActiveSeries(null)}>Volver</button>
                </div>
              ) : seriesSeasons.length > 0 && seriesSeasons[selectedSeasonIdx] ? (
                <div className="ep-grid">
                  {seriesSeasons[selectedSeasonIdx].episodes.map((ep, j) => (
                    <button
                      key={j}
                      className="episode-card focusable"
                      tabIndex={0}
                      onClick={() => playEpisode(activeSeries, ep)}
                    >
                      <span className="ep-card-num">{ep.episodeNum}</span>
                      <span className="ep-card-title">{ep.title}</span>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="ep-selector-loading">
                  <p style={{ color: 'var(--text-secondary)', marginBottom: '16px' }}>No hay episodios disponibles.</p>
                  <button className="btn btn-secondary focusable" tabIndex={0} onClick={() => setActiveSeries(null)}>
                    <X size={16} style={{ marginRight: '6px' }} />Cerrar
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
