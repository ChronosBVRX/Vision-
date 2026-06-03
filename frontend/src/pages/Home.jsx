import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { Play, Star, Loader, X, RefreshCw, Search, Tv } from 'lucide-react';
import VideoPlayer from '../components/VideoPlayer';
import DetailsModal from '../components/DetailsModal';
import { HeroBanner, CatalogRow, CatalogCard } from '../components/CatalogComponents';

function getNormalizedTVCategory(channel) {
  const title = (channel.title || '').toLowerCase();
  let cat = (channel.category || '').replace(/^(Planeta Play - |Pluto TV - |Canales - )/i, '').trim();

  const titleLower = title.toLowerCase();
  
  if (titleLower.includes('anime') || titleLower.includes('animax') || titleLower.includes('locomotion')) {
    return 'Anime';
  }
  if (
    titleLower.includes('espn') || titleLower.includes('fox sports') || titleLower.includes('sportv') ||
    titleLower.includes('deportes') || titleLower.includes('sports') || titleLower.includes('tudn') ||
    titleLower.includes('win sports') || titleLower.includes('golf') || titleLower.includes('f1') ||
    titleLower.includes('ufc') || titleLower.includes('nba') || titleLower.includes('nascar') ||
    titleLower.includes('bein') || titleLower.includes('tyc') || titleLower.includes('arena') ||
    titleLower.includes('laliga') || titleLower.includes('futbol') || titleLower.includes('garage') ||
    titleLower.includes('motorvision') || titleLower.includes('dazn')
  ) {
    return 'Deportes';
  }
  if (
    titleLower.includes('disney') || titleLower.includes('cartoon') || titleLower.includes('nickelodeon') ||
    titleLower.includes('nick ') || titleLower.includes('discovery kids') || titleLower.includes('boing') ||
    titleLower.includes('infantil') || titleLower.includes('kids') || titleLower.includes('baby') ||
    titleLower.includes('toonline') || titleLower.includes('laika')
  ) {
    return 'Kids / Infantil';
  }
  if (
    titleLower.includes('hbo') || titleLower.includes('cine') || titleLower.includes('pelicula') ||
    titleLower.includes('movie') || titleLower.includes('tnt') || titleLower.includes('space') ||
    titleLower.includes('amc') || titleLower.includes('axn') || titleLower.includes('fx') ||
    titleLower.includes('fox channel') || titleLower.includes('star channel') || titleLower.includes('cinecanal') ||
    titleLower.includes('golden') || titleLower.includes('multipremier') || titleLower.includes('studio universal') ||
    titleLower.includes('paramount') || titleLower.includes('h&h') || titleLower.includes('universal tv')
  ) {
    return 'Cine & Series';
  }
  if (
    titleLower.includes('cnn') || titleLower.includes('noticias') || titleLower.includes('news') ||
    titleLower.includes('24 horas') || titleLower.includes('rt') || titleLower.includes('telesur') ||
    titleLower.includes('dw') || titleLower.includes('prensa') || titleLower.includes('la nacion') ||
    titleLower.includes('todo noticias')
  ) {
    return 'Noticias';
  }
  if (
    titleLower.includes('discovery') || titleLower.includes('national geographic') ||
    titleLower.includes('nat geo') || titleLower.includes('history') || titleLower.includes('animal planet') ||
    titleLower.includes('documental') || titleLower.includes('biography') || titleLower.includes('investigation')
  ) {
    return 'Documentales';
  }
  if (
    titleLower.includes('mtv') || titleLower.includes('musica') || titleLower.includes('music') ||
    titleLower.includes('viva') || titleLower.includes('vh1') || titleLower.includes('htv') ||
    titleLower.includes('telehit')
  ) {
    return 'Música';
  }

  if (cat.toLowerCase() === 'canales en vivo' || cat.toLowerCase() === 'general' || cat.toLowerCase() === 'importado' || !cat) {
    return 'Variedades / General';
  }

  return cat;
}

// ─── Home Page — Muestra películas del catálogo real + fuentes en vivo ─────────
export default function Home({ selectedCategoryFilter }) {
  // ── Catalog state ──────────────────────────────────────────────────────────
  const [movies, setMovies]         = useState([]);   // from /api/catalog/movie
  const [series, setSeries]         = useState([]);   // from /api/catalog/series
  const [sources, setSources]       = useState([]);   // from /api/sources (TV/live)
  const [isLoading, setIsLoading]   = useState(true);
  const [tvSearchQuery, setTvSearchQuery] = useState('');

  // ── Player / modal state ───────────────────────────────────────────────────
  const [activeItem, setActiveItem]         = useState(null);
  const [detailsItem, setDetailsItem]       = useState(null);
  const [activeRow, setActiveRow]           = useState(0);
  const [isResolving, setIsResolving]       = useState(false);
  const [resolveAttempts, setResolveAttempts] = useState([]);
  const [resolveError, setResolveError]       = useState(null);

  // Series custom states
  const [activeSeries, setActiveSeries] = useState(null);
  const [seriesSeasons, setSeriesSeasons] = useState([]);
  const [isLoadingSeries, setIsLoadingSeries] = useState(false);
  const [seriesError, setSeriesError] = useState(null);
  const [selectedSeasonIdx, setSelectedSeasonIdx] = useState(0);
  const epSelectorRef = useRef(null);

  const resolvingRef = useRef(false);
  const pageRef      = useRef(null);

  // ── Load catalog movies, series AND live sources ───────────────────────────
  const loadAll = useCallback(() => {
    setIsLoading(true);
    Promise.all([
      fetch('/api/catalog/movie').then(r => r.json()).catch(() => ({ items: [] })),
      fetch('/api/catalog/series').then(r => r.json()).catch(() => ({ items: [] })),
      fetch('/api/sources').then(r => r.json()).catch(() => ({ sources: [] })),
    ]).then(([moviesData, seriesData, sourcesData]) => {
      setMovies(moviesData.items || []);
      setSeries(seriesData.items || []);
      setSources(sourcesData.sources || []);
    }).finally(() => setIsLoading(false));
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  // ── Combine all content into one flat list ─────────────────────────────────
  const allContent = useMemo(() => {
    // Only keep live TV sources from sources array to avoid duplicating custom movies/series
    const tvSources = sources.filter(s => s.type === 'tv');
    const normalizedSources = tvSources.map(s => ({
      ...s,
      poster: s.poster || s.thumbnailUrl || s.logo || '',
      rating: s.rating || null,
      genres: s.genres || (s.category ? [s.category] : []),
      description: s.description || '',
    }));
    return [...movies, ...series, ...normalizedSources];
  }, [movies, series, sources]);

  // ── Apply sidebar category filter ─────────────────────────────────────────
  const filteredContent = useMemo(() => {
    if (!selectedCategoryFilter || selectedCategoryFilter === 'all') return allContent;
    return allContent.filter(item => {
      if (selectedCategoryFilter === 'Canales en Vivo') {
        return item.type === 'tv';
      }
      if (item.type === 'tv') {
        const normCat = getNormalizedTVCategory(item);
        return normCat === selectedCategoryFilter;
      }
      return item.category === selectedCategoryFilter;
    });
  }, [allContent, selectedCategoryFilter]);

  const isTVMode = useMemo(() => {
    if (!selectedCategoryFilter) return false;
    if (selectedCategoryFilter === 'Canales en Vivo') return true;
    return allContent.some(item => item.type === 'tv' && getNormalizedTVCategory(item) === selectedCategoryFilter);
  }, [selectedCategoryFilter, allContent]);

  const tvChannels = useMemo(() => allContent.filter(item => item.type === 'tv'), [allContent]);



  // ── Group into rows ────────────────────────────────────────────────────────
  const genreGroups = useMemo(() => {
    const g = {};
    const movieItems  = filteredContent.filter(s => s.type === 'movie');
    const seriesItems = filteredContent.filter(s => s.type === 'series');
    const tvItems     = filteredContent.filter(s => s.type === 'tv');

    if (movieItems.length > 0) {
      // Destacadas: top rated
      const topRated = movieItems.filter(m => m.featured || (m.rating && m.rating >= 7));
      if (topRated.length >= 4) {
        g['⭐ Películas Destacadas'] = [...topRated].sort(() => 0.5 - Math.random()).slice(0, 24);
      }
      // Recientes: all shuffled
      g['🎬 Películas para Ti'] = [...movieItems].sort(() => 0.5 - Math.random()).slice(0, 24);

      // Genre rows from movie genres
      movieItems.forEach(item => {
        const genres = item.genres?.length > 0 ? item.genres.filter(g2 => !/^[⭐🔥🎬]/.test(g2)) : [];
        genres.forEach(genre => {
          if (!g[genre]) g[genre] = [];
          if (g[genre].length < 24) g[genre].push(item);
        });
      });

      // Top rated separate row if many
      const topR = movieItems.filter(m => m.rating && m.rating >= 8);
      if (topR.length >= 4) g['🏆 Mejor Valoradas'] = topR.slice(0, 24);
    }

    if (seriesItems.length > 0) {
      g['📺 Series del Momento'] = [...seriesItems].sort(() => 0.5 - Math.random()).slice(0, 24);
    }

    if (tvItems.length > 0) {
      g['📡 Canales en Vivo'] = tvItems.slice(0, 24);
    }

    return g;
  }, [filteredContent]);

  const rowKeys = useMemo(() => Object.keys(genreGroups), [genreGroups]);

  // ── TV keyboard nav between rows ───────────────────────────────────────────
  useEffect(() => {
    if (activeItem) return;
    const handleKey = (e) => {
      const activeEl = document.activeElement;
      const inSidebar = activeEl && !!activeEl.closest('.sidebar');
      if (inSidebar) return; // Let the sidebar handle its own Up/Down navigation

      if (['ArrowUp', 'ArrowDown'].includes(e.key)) {
        e.preventDefault();
        setActiveRow(prev => {
          const next = e.key === 'ArrowUp'
            ? Math.max(0, prev - 1)
            : Math.min(rowKeys.length - 1, prev + 1);
          const el = document.getElementById(`home-crow-${next}`);
          el?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
          return next;
        });
      }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [activeItem, rowKeys.length]);

  // ── Series Episode Player Callback ─────────────────────────────────────────
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

  // ── Same resolver as Movies.jsx ────────────────────────────────────────────
  const handlePlay = useCallback((item) => {
    setDetailsItem(null);
    if (!item) return;
    if (resolvingRef.current) return;

    // Sports / already resolved
    if (item.provider === 'internal-resolver' || item.isSports) {
      setActiveItem(item);
      return;
    }

    // TV channels: use live resolver
    if (item.type === 'tv') {
      if (item.streams && item.streams.length > 0) {
        resolvingRef.current = true;
        setIsResolving(true);
        setResolveError(null);
        setResolveAttempts([]);
        setActiveItem({ ...item, isResolving: true });

        fetch('/api/live/resolve', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ streams: item.streams, type: 'tv' })
        })
          .then(r => r.json())
          .then(data => {
            if (data.success) {
              setActiveItem({
                ...item,
                provider: 'live-resolver',
                streams: [{ name: data.selectedServer, url: data.stream.url, type: data.stream.type, headers: data.stream.headers || {}, quality: 'auto', resolver: 'direct' }],
              });
            } else {
              setActiveItem(null);
              setResolveError(data.error || 'No hay señal disponible.');
            }
          })
          .catch(() => { setActiveItem(null); setResolveError('Error de conexión al sintonizar el canal.'); })
          .finally(() => { setIsResolving(false); resolvingRef.current = false; });
      } else {
        setActiveItem(item);
      }
      return;
    }

    // Series → open episode selector
    if (item.type === 'series') {
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

    // Movies: use movie resolver
    resolvingRef.current = true;
    setIsResolving(true);
    setResolveError(null);
    setResolveAttempts([]);
    setActiveItem({ ...item, isResolving: true });

    fetch(`/api/movies/${item.id}/resolve?lang=auto&debug=true`)
      .then(r => r.json())
      .then(data => {
        if (data.success) {
          setActiveItem({
            id: item.id, title: item.title, type: item.type, poster: item.poster,
            provider: 'internal-resolver',
            selectedLanguage: data.selectedLanguage,
            selectedServer: data.selectedServer,
            streams: [{ name: data.selectedServer, url: data.stream.url, type: data.stream.type, headers: data.stream.headers || {}, quality: data.stream.quality || 'auto', resolver: 'direct' }],
            attempts: data.attempts || []
          });
          setIsResolving(false);
        } else {
          setActiveItem(null);
          setIsResolving(false);
          setResolveError(data.error || 'No hay fuentes reproducibles disponibles.');
          setResolveAttempts(data.attempts || []);
        }
      })
      .catch(() => {
        setActiveItem(null);
        setIsResolving(false);
        setResolveError('Error de conexión al resolver la fuente.');
      })
      .finally(() => { resolvingRef.current = false; });
  }, []);

  // Click en card → abrir detalles primero
  const handleDetails = useCallback((item) => setDetailsItem(item), []);

  const playNext = useCallback(() => {
    if (!activeItem) return;
    const idx = filteredContent.findIndex(m => m.id === activeItem.id);
    if (idx >= 0 && idx < filteredContent.length - 1) handlePlay(filteredContent[idx + 1]);
  }, [activeItem, filteredContent, handlePlay]);

  const handleEpisodeChange = useCallback((updatedSource) => {
    setActiveItem(updatedSource);
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
      
      // Detener propagación de flechas para evitar conflicto con catálogo por debajo
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
        e.preventDefault();
        e.stopPropagation();
      }

      if (e.key === 'Escape' || e.key === 'Backspace') {
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

  if (isLoading) {
    return (
      <div className="catalog-loading">
        <div className="catalog-loading-icon"><Loader size={52} className="spin-anim" /></div>
        <p className="catalog-loading-text">Cargando contenido...</p>
      </div>
    );
  }

  if (allContent.length === 0) {
    return (
      <div className="catalog-empty">
        <div className="catalog-empty-icon"><Play size={80} /></div>
        <h2 className="catalog-empty-title">Sin contenido aún</h2>
        <p className="catalog-empty-sub">Ve a Películas y sincroniza el catálogo para ver contenido aquí.</p>
        <button className="btn btn-primary catalog-sync-btn" onClick={loadAll}>
          <RefreshCw size={16} /> Reintentar
        </button>
      </div>
    );
  }

  if (isTVMode) {
    let tvList = filteredContent.filter(item => item.type === 'tv');
    if (tvSearchQuery.trim().length > 0) {
      const q = tvSearchQuery.toLowerCase().trim();
      tvList = tvList.filter(item => 
        (item.title && item.title.toLowerCase().includes(q)) ||
        (item.description && item.description.toLowerCase().includes(q))
      );
    }

    const groupedTV = {};
    tvList.forEach(channel => {
      const cat = getNormalizedTVCategory(channel);
      if (!groupedTV[cat]) groupedTV[cat] = [];
      groupedTV[cat].push(channel);
    });

    const tvGroupKeys = Object.keys(groupedTV).sort();

    return (
      <div ref={pageRef} className="catalog-page catalog-page--no-hero">
        
        {/* TV SEARCH BAR */}
        <div className="catalog-filter-bar" style={{ display: 'flex', justifyContent: 'center', marginBottom: '32px' }}>
          <div className="catalog-search-wrapper" style={{ width: '100%', maxWidth: '600px' }}>
            <input
              type="text"
              className="catalog-search-input focusable"
              placeholder="Buscar canales en vivo por nombre..."
              value={tvSearchQuery}
              onChange={(e) => setTvSearchQuery(e.target.value)}
              tabIndex={0}
            />
            <Search className="catalog-search-icon" size={18} />
            {tvSearchQuery && (
              <button 
                className="catalog-search-clear focusable"
                onClick={() => setTvSearchQuery('')}
                tabIndex={0}
                title="Limpiar búsqueda"
              >
                <X size={16} />
              </button>
            )}
          </div>
        </div>

        {/* TV CHANNELS GRID SECTIONS */}
        <div className="catalog-grid-sections" style={{ display: 'flex', flexDirection: 'column', gap: '40px' }}>
          {tvGroupKeys.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '80px 20px', color: 'var(--text-secondary)' }}>
              <p style={{ fontSize: '1.2rem', marginBottom: '10px', fontWeight: 600 }}>No se encontraron canales.</p>
              <p style={{ fontSize: '0.9rem' }}>Intenta con otro término de búsqueda.</p>
            </div>
          ) : (
            tvGroupKeys.map(cat => (
              <div key={cat} className="tv-category-section">
                <div className="catalog-grid-header" style={{ marginBottom: '16px', borderBottom: '1px solid rgba(255,255,255,0.08)', paddingBottom: '8px' }}>
                  <h2 style={{ fontSize: '1.4rem', color: '#fff', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Tv size={20} style={{ color: 'var(--primary-light)' }} />
                    {cat}
                  </h2>
                  <span className="catalog-grid-count">
                    {groupedTV[cat].length} {groupedTV[cat].length === 1 ? 'canal' : 'canales'}
                  </span>
                </div>
                
                <div className="catalog-grid">
                  {groupedTV[cat].map((channel, idx) => (
                    <CatalogCard
                      key={`${channel.id}-${idx}`}
                      item={channel}
                      onPlay={handleDetails}
                      onFocus={() => {}}
                    />
                  ))}
                </div>
              </div>
            ))
          )}
        </div>

        {/* DETAILS MODAL */}
        {detailsItem && (
          <DetailsModal
            item={detailsItem}
            onClose={() => setDetailsItem(null)}
            onPlay={handlePlay}
          />
        )}

        {/* RESOLVER LOADING OVERLAY */}
        {(isResolving || (activeItem && activeItem.isResolving)) && (
          <div className="watch-overlay" style={{ justifyContent: 'center', alignItems: 'center', zIndex: 9999 }}>
            <div style={{ textAlign: 'center', padding: '36px', maxWidth: '420px' }} className="glass-panel form-card">
              <Loader className="spin-anim mx-auto mb-4" size={48} style={{ color: 'var(--primary-light)', marginBottom: '16px' }} />
              <h3 style={{ fontSize: '1.4rem', fontWeight: 600, color: '#fff', marginBottom: '6px' }}>
                {activeItem?.title || 'Cargando...'}
              </h3>
              <p style={{ fontSize: '0.85rem', color: 'var(--primary-light)', marginBottom: '4px', fontWeight: 500 }}>
                📡 Sintonizando señal en vivo...
              </p>
              <p className="text-secondary" style={{ fontSize: '0.85rem', marginBottom: '20px' }}>
                Analizando transmisiones y omitiendo anuncios de origen
              </p>
              <button className="btn btn-secondary focusable" tabIndex={0}
                onClick={() => { setActiveItem(null); setIsResolving(false); resolvingRef.current = false; }}>
                Cancelar
              </button>
            </div>
          </div>
        )}

        {/* RESOLVER ERROR OVERLAY */}
        {resolveError && (
          <div className="watch-overlay" style={{ justifyContent: 'center', alignItems: 'center', zIndex: 9999 }}>
            <div style={{ textAlign: 'center', maxWidth: '550px', padding: '24px' }} className="glass-panel form-card">
              <h3 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--accent)', marginBottom: '12px' }}>
                Error de Transmisión
              </h3>
              <p className="text-secondary" style={{ marginBottom: '20px', fontSize: '0.95rem' }}>{resolveError}</p>
              <button className="btn btn-secondary focusable" onClick={() => setResolveError(null)}>
                <X size={18} /> Cerrar
              </button>
            </div>
          </div>
        )}

        {/* VIDEO PLAYER */}
        {activeItem && !activeItem.isResolving && (
          <VideoPlayer
            source={activeItem}
            onClose={() => setActiveItem(null)}
            onNext={playNext}
            onNextEpisode={handleEpisodeChange}
            onPrevEpisode={handleEpisodeChange}
            channelList={tvList}
            onChannelChange={handlePlay}
          />
        )}
      </div>
    );
  }

  return (
    <div ref={pageRef} className="catalog-page catalog-page--no-hero">

      {/* ── GENRE ROWS ───────────────────────────────────────────────────── */}
      <div className="catalog-rows">
        {rowKeys.map((genre, rowIdx) => (
          <CatalogRow
            key={genre}
            id={`home-crow-${rowIdx}`}
            title={genre}
            items={genreGroups[genre] || []}
            isActive={activeRow === rowIdx}
            onPlay={handleDetails}
            onFocus={() => setActiveRow(rowIdx)}
          />
        ))}
      </div>

      {/* ── DETAILS MODAL ────────────────────────────────────────────────── */}
      {detailsItem && (
        <DetailsModal
          item={detailsItem}
          onClose={() => setDetailsItem(null)}
          onPlay={handlePlay}
        />
      )}

      {/* ── RESOLVER LOADING OVERLAY ──────────────────────────────────────── */}
      {(isResolving || (activeItem && activeItem.isResolving)) && (
        <div className="watch-overlay" style={{ justifyContent: 'center', alignItems: 'center', zIndex: 9999 }}>
          <div style={{ textAlign: 'center', padding: '36px', maxWidth: '420px' }} className="glass-panel form-card">
            <Loader className="spin-anim mx-auto mb-4" size={48} style={{ color: 'var(--primary-light)', marginBottom: '16px' }} />
            <h3 style={{ fontSize: '1.4rem', fontWeight: 600, color: '#fff', marginBottom: '6px' }}>
              {activeItem?.title || 'Cargando...'}
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--primary-light)', marginBottom: '4px', fontWeight: 500 }}>
              🔍 Buscando fuente en Español Latino...
            </p>
            <p className="text-secondary" style={{ fontSize: '0.85rem', marginBottom: '20px' }}>
              Analizando servidores y omitiendo anuncios de origen
            </p>
            <button className="btn btn-secondary focusable" tabIndex={0}
              onClick={() => { setActiveItem(null); setIsResolving(false); resolvingRef.current = false; }}>
              Cancelar
            </button>
          </div>
        </div>
      )}

      {/* ── RESOLVER ERROR OVERLAY ────────────────────────────────────────── */}
      {resolveError && (
        <div className="watch-overlay" style={{ justifyContent: 'center', alignItems: 'center', zIndex: 9999 }}>
          <div style={{ textAlign: 'center', maxWidth: '550px', padding: '24px' }} className="glass-panel form-card">
            <h3 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--accent)', marginBottom: '12px' }}>
              Error de Reproducción
            </h3>
            <p className="text-secondary" style={{ marginBottom: '20px', fontSize: '0.95rem' }}>{resolveError}</p>
            {resolveAttempts.length > 0 && (
              <div style={{ textAlign: 'left', maxHeight: '200px', overflowY: 'auto', marginBottom: '20px', background: 'rgba(0,0,0,0.3)', padding: '12px', borderRadius: '6px' }}>
                {resolveAttempts.map((att, i) => (
                  <div key={i} style={{ fontSize: '0.8rem', color: att.status === 'SUCCESS' ? '#4caf50' : '#f44336', marginBottom: '4px' }}>
                    • [{att.language}] {att.sourceName}: {att.status}
                  </div>
                ))}
              </div>
            )}
            <button className="btn btn-secondary focusable" onClick={() => setResolveError(null)}>
              <X size={18} /> Cerrar
            </button>
          </div>
        </div>
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
                  <button className="btn btn-secondary" onClick={() => setActiveSeries(null)}>Volver</button>
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
                      <span className="ep-card-num">{ep.episodeNum || (j + 1)}</span>
                      <span className="ep-card-title">{ep.title}</span>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="ep-selector-loading">
                  <p style={{ color: 'var(--text-secondary)' }}>No hay episodios disponibles.</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ── VIDEO PLAYER ─────────────────────────────────────────────────── */}
      {activeItem && !activeItem.isResolving && (
        <VideoPlayer
          source={activeItem}
          onClose={() => setActiveItem(null)}
          onNext={playNext}
          onNextEpisode={handleEpisodeChange}
          onPrevEpisode={handleEpisodeChange}
          channelList={activeItem.type === 'tv' ? tvChannels : undefined}
          onChannelChange={activeItem.type === 'tv' ? handlePlay : undefined}
        />
      )}
    </div>
  );
}
