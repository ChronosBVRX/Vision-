import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { Play, Loader, Tv, Film, Search } from 'lucide-react';
import VideoPlayer from '../components/VideoPlayer';
import DetailsModal from '../components/DetailsModal';
import { HeroBanner, CatalogRow } from '../components/CatalogComponents';

export default function PlutoTV({ initialTab }) {
  const [catalog, setCatalog] = useState([]);
  const [categories, setCategories] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState(initialTab || 'Todas');
  
  const [activeItem, setActiveItem] = useState(null);
  const [detailsItem, setDetailsItem] = useState(null);
  const [featIdx, setFeatIdx] = useState(0);
  const [activeRow, setActiveRow] = useState(0);
  const [focusedItem, setFocusedItem] = useState(null);
  const [isResolving, setIsResolving] = useState(false);
  const [resolveError, setResolveError] = useState(null);

  const pageRef = useRef(null);
  const resolvingRef = useRef(false);

  useEffect(() => {
    setIsLoading(true);
    fetch('/api/plutotv')
      .then(r => r.json())
      .then(data => {
        setCatalog(data.items || []);
        
        const cats = new Set();
        (data.items || []).forEach(item => {
          let catName = item.category || 'General';
          catName = catName.replace(/^Pluto TV - /i, '');
          cats.add(catName);
        });
        setCategories(['Todas', 'Canales en Vivo', 'Películas', ...Array.from(cats).sort().filter(c => c !== 'Live')]);
      })
      .catch(err => console.error('Error fetching pluto tv:', err))
      .finally(() => setIsLoading(false));
  }, []);

  useEffect(() => {
    if (initialTab) setActiveTab(initialTab);
  }, [initialTab]);

  const playChannel = useCallback((channelItem) => {
    if (!channelItem || resolvingRef.current) return;
    resolvingRef.current = true;
    setIsResolving(true);
    setResolveError(null);
    setActiveItem({ ...channelItem, isResolving: true });

    fetch('/api/live/resolve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ streams: channelItem.streams, type: 'tv' })
    })
      .then(r => r.json())
      .then(data => {
        if (data.success) {
          setActiveItem({
            ...channelItem,
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
  }, []);

  const playMovie = useCallback((item) => {
    if (!item || resolvingRef.current) return;
    resolvingRef.current = true;
    setIsResolving(true);
    setResolveError(null);
    setActiveItem({ ...item, isResolving: true });

    fetch(`/api/movies/${item.id}/resolve?lang=auto`)
      .then(r => r.json())
      .then(data => {
        if (data.success) {
          setActiveItem({
            id: item.id, title: item.title, type: item.type, poster: item.poster,
            provider: "internal-resolver",
            streams: [{ name: data.selectedServer, url: data.stream.url, type: data.stream.type, headers: data.stream.headers || {}, quality: data.stream.quality || "auto", resolver: "direct" }]
          });
        } else {
          setActiveItem(null);
          setResolveError(data.error || "No hay fuentes reproducibles disponibles.");
        }
      })
      .catch(() => { setActiveItem(null); setResolveError("Error de conexión al resolver la fuente."); })
      .finally(() => { setIsResolving(false); resolvingRef.current = false; });
  }, []);

  const handlePlay = useCallback((item) => {
    setDetailsItem(null);
    if (!item) return;
    if (item.type === 'tv') {
      playChannel(item);
    } else {
      playMovie(item);
    }
  }, [playChannel, playMovie]);

  const tvChannels = useMemo(() => catalog.filter(i => i.type === 'tv'), [catalog]);

  const handleDetails = useCallback((item) => {
    setDetailsItem(item);
  }, []);

  const filteredCatalog = useMemo(() => {
    let filtered = catalog;
    
    // Filtro por pestaña
    if (activeTab === 'Canales en Vivo') {
      filtered = filtered.filter(i => i.type === 'tv');
    } else if (activeTab === 'Películas') {
      filtered = filtered.filter(i => i.type === 'movie');
    } else if (activeTab !== 'Todas') {
      filtered = filtered.filter(i => {
        const catName = (i.category || '').replace(/^Pluto TV - /i, '');
        return catName === activeTab;
      });
    }

    // Filtro por búsqueda
    if (searchQuery.trim() !== '') {
      const q = searchQuery.toLowerCase();
      filtered = filtered.filter(i => (i.title || '').toLowerCase().includes(q));
    }
    
    return filtered;
  }, [catalog, activeTab, searchQuery]);

  const featuredItems = useMemo(() => {
    const pool = catalog.filter(m => m.poster);
    const shuffled = [...pool].sort(() => 0.5 - Math.random());
    return shuffled.slice(0, 10);
  }, [catalog]);

  useEffect(() => {
    if (featuredItems.length <= 1) return;
    const t = setInterval(() => setFeatIdx(p => (p + 1) % featuredItems.length), 7000);
    return () => clearInterval(t);
  }, [featuredItems]);

  const genreGroups = useMemo(() => {
    const g = {};
    if (searchQuery.trim() !== '') {
      g['Resultados de Búsqueda'] = filteredCatalog;
      return g;
    }
    
    if (activeTab === 'Todas') {
      const tvItems = filteredCatalog.filter(i => i.type === 'tv');
      const movieItems = filteredCatalog.filter(i => i.type === 'movie');
      if (tvItems.length > 0) g['Canales Destacados'] = tvItems.slice(0, 24);
      if (movieItems.length > 0) g['Películas Destacadas'] = movieItems.slice(0, 24);
      
      // Agrupar algunas categorías populares
      const action = filteredCatalog.filter(i => i.category?.includes('Acción'));
      if (action.length > 0) g['Acción'] = action.slice(0, 24);
    } else {
      g[activeTab] = filteredCatalog;
    }
    
    return g;
  }, [filteredCatalog, activeTab, searchQuery]);

  const rowKeys = Object.keys(genreGroups);

  // Modal de error simple
  if (resolveError) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'white' }}>
        <h2 style={{ marginBottom: '1rem' }}>Error</h2>
        <p>{resolveError}</p>
        <button className="btn btn-primary" style={{ marginTop: '1rem' }} onClick={() => setResolveError(null)}>Regresar</button>
      </div>
    );
  }

  // Reproductor Activo
  if (activeItem && !activeItem.isResolving) {
    return (
      <div className="player-overlay">
        <VideoPlayer 
          source={activeItem} 
          onClose={() => setActiveItem(null)} 
          onNextEpisode={() => {}} 
          onPrevEpisode={() => {}} 
          channelList={activeItem.type === 'tv' ? tvChannels : undefined}
          onChannelChange={activeItem.type === 'tv' ? playChannel : undefined}
        />
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="catalog-loading">
        <div className="catalog-loading-icon">
          <Loader size={52} className="spin-anim" />
        </div>
        <p className="catalog-loading-text">Cargando Pluto TV...</p>
      </div>
    );
  }

  const currentFeatured = featuredItems[featIdx] || catalog[0];
  const heroItem = focusedItem || currentFeatured;

  return (
    <div ref={pageRef} className="catalog-page pluto-page" style={{ position: 'relative' }}>
      
      {/* Search and Tabs Header */}
      <div style={{ position: 'absolute', top: 20, right: 40, zIndex: 100, display: 'flex', gap: '15px', alignItems: 'center' }}>
        <div className="search-bar" style={{ display: 'flex', alignItems: 'center', background: 'rgba(0,0,0,0.5)', padding: '5px 15px', borderRadius: '20px', border: '1px solid rgba(255,204,0,0.3)' }}>
          <Search size={18} color="#ffcc00" />
          <input 
            type="text" 
            placeholder="Buscar en Pluto TV..." 
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ background: 'transparent', border: 'none', color: 'white', outline: 'none', marginLeft: '10px' }}
          />
        </div>
      </div>

      <div style={{ position: 'absolute', top: 70, right: 40, zIndex: 100, display: 'flex', gap: '10px', maxWidth: '60vw', overflowX: 'auto', paddingBottom: '10px' }}>
        {categories.map(cat => (
          <button 
            key={cat}
            className={`tab-btn ${activeTab === cat ? 'active' : ''}`}
            style={{
              padding: '6px 16px',
              borderRadius: '20px',
              border: `1px solid ${activeTab === cat ? '#ffcc00' : 'rgba(255,255,255,0.2)'}`,
              background: activeTab === cat ? 'rgba(255,204,0,0.2)' : 'rgba(0,0,0,0.5)',
              color: activeTab === cat ? '#ffcc00' : 'white',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              fontSize: '0.9rem'
            }}
            onClick={() => setActiveTab(cat)}
          >
            {cat}
          </button>
        ))}
      </div>

      {heroItem && (
        <HeroBanner
          item={heroItem}
          totalItems={featuredItems.length}
          currentIndex={featIdx}
          onIndexChange={setFeatIdx}
          onPlay={handleDetails}
          isMovie={heroItem.type === 'movie'}
        />
      )}

      {/* Resolving Overlay */}
      {isResolving && (
        <div style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.8)', zIndex: 999, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: 'white' }}>
          <Loader size={48} className="spin-anim" style={{ color: '#ffcc00', marginBottom: '20px' }} />
          <h3>Sintonizando {activeItem?.title}...</h3>
          <p style={{ color: '#aaa', marginTop: '10px' }}>Obteniendo token de Pluto TV</p>
        </div>
      )}

      <div className="catalog-rows" style={{ marginTop: '-80px', position: 'relative', zIndex: 10 }}>
        {rowKeys.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '50px', color: '#aaa' }}>No se encontraron resultados.</div>
        ) : (
          rowKeys.map((genre, rowIdx) => (
            <CatalogRow
              key={genre}
              id={`crow-${rowIdx}`}
              title={genre}
              items={genreGroups[genre] || []}
              isActive={activeRow === rowIdx}
              onPlay={handleDetails}
              onFocus={() => setActiveRow(rowIdx)}
              onItemFocus={setFocusedItem}
            />
          ))
        )}
      </div>

      {detailsItem && (
        <DetailsModal
          item={detailsItem}
          onClose={() => setDetailsItem(null)}
          onPlay={() => handlePlay(detailsItem)}
          isResolving={isResolving}
        />
      )}
    </div>
  );
}
