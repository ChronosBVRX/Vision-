import React, { useState, useEffect, useRef } from 'react';
import { PlayCircle, Search, RefreshCw, AlertCircle, Play, Globe, Settings, Clock, Activity, Loader, X, Trophy } from 'lucide-react';
import VideoPlayer from '../components/VideoPlayer';

const getSportStyle = (sport) => {
  const norm = (sport || '').toLowerCase().trim();
  if (norm.includes('baloncesto') || norm.includes('nba') || norm.includes('basket')) {
    return {
      gradient: 'linear-gradient(135deg, #f97316 0%, #ea580c 50%, #9a3412 100%)',
      emoji: '🏀',
      color: '#ea580c'
    };
  }
  if (norm.includes('formula') || norm.includes('f1') || norm.includes('moto') || norm.includes('carrera') || norm.includes('gp')) {
    return {
      gradient: 'linear-gradient(135deg, #ef4444 0%, #dc2626 50%, #7f1d1d 100%)',
      emoji: '🏎️',
      color: '#dc2626'
    };
  }
  if (norm.includes('tenis') || norm.includes('tennis')) {
    return {
      gradient: 'linear-gradient(135deg, #a3e635 0%, #84cc16 50%, #16a34a 100%)',
      emoji: '🎾',
      color: '#84cc16'
    };
  }
  if (norm.includes('combate') || norm.includes('ufc') || norm.includes('box') || norm.includes('lucha')) {
    return {
      gradient: 'linear-gradient(135deg, #6b7280 0%, #4b5563 50%, #1f2937 100%)',
      emoji: '🥊',
      color: '#4b5563'
    };
  }
  if (norm.includes('futbol') || norm.includes('soccer')) {
    return {
      gradient: 'linear-gradient(135deg, #34d399 0%, #10b981 50%, #047857 100%)',
      emoji: '⚽',
      color: '#10b981'
    };
  }
  return {
    gradient: 'linear-gradient(135deg, #818cf8 0%, #6366f1 50%, #3730a3 100%)',
    emoji: '🏆',
    color: '#6366f1'
  };
};

export default function Sports() {
  const [matches, setMatches] = useState([]);
  const [activeMirror, setActiveMirror] = useState('');
  const [lastUpdated, setLastUpdated] = useState(null);
  const [loadingMatches, setLoadingMatches] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  
  // Settings for scraper
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [rojadirectaUrl, setRojadirectaUrl] = useState('');
  const [savingSettings, setSavingSettings] = useState(false);

  // Active match streams (read instantly from grouped match)
  const [selectedMatch, setSelectedMatch] = useState(null);
  const [streams, setStreams] = useState([]);
  const [resolvingStreamUrl, setResolvingStreamUrl] = useState(null); // URL of stream being resolved
  
  // Watch active stream
  const [activeWatchSource, setActiveWatchSource] = useState(null);
  
  const [isResolving, setIsResolving] = useState(false);
  const [resolveAttempts, setResolveAttempts] = useState([]);
  const [resolveError, setResolveError] = useState(null);
  const resolvingRef = useRef(false);
  const resolveErrorBtnRef = useRef(null);

  // Auto-focus close button when error overlay appears
  useEffect(() => {
    if (resolveError) setTimeout(() => resolveErrorBtnRef.current?.focus(), 100);
  }, [resolveError]);

  // Polling reference for background updates
  const pollingIntervalRef = useRef(null);

  useEffect(() => {
    fetchSettings();
    // Load instant cache on mount
    fetchMatches(true);

    return () => {
      stopPolling();
    };
  }, []);

  const fetchSettings = () => {
    fetch('/api/settings')
      .then(res => res.json())
      .then(data => {
        setRojadirectaUrl(data.rojadirectaUrl || 'https://www.rojadirectatv.me');
      })
      .catch(err => console.error("Error loading settings:", err));
  };

  const handleSaveSettings = (e) => {
    e.preventDefault();
    setSavingSettings(true);
    fetch('/api/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rojadirectaUrl })
    })
      .then(res => res.json())
      .then(data => {
        setRojadirectaUrl(data.rojadirectaUrl);
        setSettingsOpen(false);
        handleForceRefresh();
      })
      .catch(err => {
        console.error("Error saving settings:", err);
        alert("No se pudo guardar la configuración.");
      })
      .finally(() => setSavingSettings(false));
  };

  const fetchMatches = (isInitial = false) => {
    if (isInitial && matches.length === 0) {
      setLoadingMatches(true);
    }
    
    fetch('/api/sports/matches')
      .then(res => {
        if (!res.ok) throw new Error("Error en la respuesta del servidor");
        return res.json();
      })
      .then(data => {
        if (data.success) {
          setMatches(data.matches || []);
          setActiveMirror(data.activeUrl || '');
          setLastUpdated(data.lastUpdated ? new Date(data.lastUpdated) : null);
          setIsRefreshing(data.loading);
          setError(data.error);

          // Update active selected match if it has updated streams in cache
          if (selectedMatch) {
            const updated = (data.matches || []).find(m => m.title === selectedMatch.title && m.time === selectedMatch.time);
            if (updated) {
              setStreams(updated.streams || []);
            }
          }

          if (data.loading) {
            startPolling();
          } else {
            stopPolling();
          }
        } else {
          setError(data.error || "No se pudo cargar la agenda.");
          stopPolling();
        }
      })
      .catch(err => {
        console.error("Error loading matches:", err);
        setError("Error de conexión. El servidor de Vision+ podría estar desconectado.");
        stopPolling();
      })
      .finally(() => {
        setLoadingMatches(false);
      });
  };

  const startPolling = () => {
    if (pollingIntervalRef.current) return;
    pollingIntervalRef.current = setInterval(() => {
      fetchMatches(false);
    }, 3000);
  };

  const stopPolling = () => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
  };

  const handleForceRefresh = () => {
    setIsRefreshing(true);
    setError(null);
    fetch('/api/sports/refresh', { method: 'POST' })
      .then(res => res.json())
      .then(() => {
        startPolling();
      })
      .catch(err => {
        console.error("Error triggering refresh:", err);
        setIsRefreshing(false);
      });
  };

  // Open the video player and let the player auto-resolve the stream list
  const handleSelectMatch = (match) => {
    if (resolvingRef.current) return;
    resolvingRef.current = true;
    setIsResolving(true);
    setResolveError(null);
    setResolveAttempts([]);

    setActiveWatchSource({ title: match.title, isResolving: true });

    fetch(`/api/live/resolve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ streams: match.streams, type: 'sports' })
    })
      .then(r => r.json())
      .then(data => {
        if (data.success) {
          const playableSource = {
            id: match.id || Date.now().toString(),
            title: match.title,
            type: 'sports',
            isSports: true,
            provider: "live-resolver",
            selectedLanguage: data.selectedLanguage,
            selectedServer: data.selectedServer,
            originalStreams: match.streams,
            streams: [
              {
                name: data.selectedServer,
                url: data.stream.url,
                type: data.stream.type,
                headers: data.stream.headers || {},
                quality: data.stream.quality || "auto",
                resolver: data.stream.resolver || "direct"
              }
            ],
            attempts: data.attempts || []
          };
          setActiveWatchSource(playableSource);
        } else {
          setActiveWatchSource(null);
          setResolveError(data.error || "No hay fuentes disponibles en este momento.");
          setResolveAttempts(data.attempts || []);
        }
      })
      .catch(err => {
        console.error("Resolution error:", err);
        setActiveWatchSource(null);
        setResolveError("Error de conexión al resolver el partido.");
      })
      .finally(() => {
        setIsResolving(false);
        resolvingRef.current = false;
      });
  };

  const filteredMatches = matches.filter(match => 
    match.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    match.sport.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const getFormattedTime = (date) => {
    if (!date) return 'Nunca';
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  return (
    <div>
      {/* Header and status bar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 className="section-title" style={{ margin: 0, display: 'inline-block' }}>Deportes en Vivo</h2>
          {activeMirror && (
            <span 
              className="card-badge tv" 
              style={{ 
                marginLeft: '12px', 
                position: 'static', 
                display: 'inline-flex', 
                alignItems: 'center', 
                gap: '4px',
                fontSize: '0.75rem',
                border: '1px solid rgba(6, 182, 212, 0.3)',
                background: 'rgba(6, 182, 212, 0.05)'
              }}
            >
              <Activity size={10} /> Espejo Activo: {activeMirror.replace('https://', '')}
            </span>
          )}
        </div>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          {lastUpdated && (
            <span className="text-muted" style={{ fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Clock size={12} /> Act. {getFormattedTime(lastUpdated)}
            </span>
          )}
          <button className="btn btn-secondary focusable" tabIndex={0} onClick={() => setSettingsOpen(!settingsOpen)} title="Configurar espejos">
            <Settings size={18} /> Configurar
          </button>
          <button 
            className="btn btn-primary focusable" 
            tabIndex={0}
            onClick={handleForceRefresh} 
            disabled={isRefreshing || loadingMatches}
            style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
          >
            <RefreshCw size={18} className={isRefreshing ? 'animate-spin' : ''} />
            {isRefreshing ? 'Actualizando...' : 'Refrescar'}
          </button>
        </div>
      </div>

      {/* Settings Form Panel */}
      {settingsOpen && (
        <div className="glass-panel form-card" style={{ marginBottom: '24px' }}>
          <h4 style={{ fontWeight: 700, marginBottom: '12px' }}>Configuración del Espejo de Deportes</h4>
          <form onSubmit={handleSaveSettings} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div className="form-group">
              <label className="form-label">Dominio Preferido de Rojadirecta</label>
              <input
                type="url"
                className="form-input focusable"
                tabIndex={0}
                value={rojadirectaUrl}
                onChange={(e) => setRojadirectaUrl(e.target.value)}
                placeholder="https://www.rojadirectatv.me"
                required
              />
              <p className="text-muted" style={{ fontSize: '0.75rem', marginTop: '4px' }}>
                El servidor busca automáticamente espejos activos en cada inicio, pero puedes forzar un dominio de inicio preferido aquí.
              </p>
            </div>
            <div style={{ display: 'flex', gap: '10px', marginTop: '6px' }}>
              <button type="submit" className="btn btn-primary focusable" tabIndex={0} disabled={savingSettings}>
                {savingSettings ? 'Guardando...' : 'Guardar y Recargar'}
              </button>
              <button type="button" className="btn btn-secondary focusable" tabIndex={0} onClick={() => setSettingsOpen(false)}>
                Cancelar
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Search Input */}
      <div className="search-container">
        <Search className="search-icon" size={20} />
        <input
          type="text"
          placeholder="Busca eventos, equipos o deportes..."
          className="search-input focusable"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
      </div>

      {/* Matches Schedule Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', marginTop: '10px' }}>
        <h3 className="section-title" style={{ margin: 0 }}>Agenda del Día</h3>
        {isRefreshing && (
          <span className="text-muted animate-pulse" style={{ fontSize: '0.85rem', color: 'var(--secondary)', fontWeight: 600 }}>
            Buscando transmisiones en segundo plano...
          </span>
        )}
      </div>

      {loadingMatches ? (
        <div className="text-center p-12 glass-panel" style={{ borderRadius: 'var(--radius-lg)' }}>
          <RefreshCw className="animate-spin text-muted mx-auto mb-4" size={36} />
          <p className="text-secondary" style={{ fontSize: '1rem' }}>Cargando agenda de partidos...</p>
        </div>
      ) : error && matches.length === 0 ? (
        <div className="text-center p-12 glass-panel" style={{ borderRadius: 'var(--radius-lg)', color: 'var(--accent)' }}>
          <AlertCircle className="mx-auto mb-4" size={48} />
          <p style={{ fontWeight: 600, fontSize: '1.1rem', marginBottom: '16px' }}>{error}</p>
          <button className="btn btn-secondary focusable" tabIndex={0} onClick={handleForceRefresh}>
            Volver a intentar
          </button>
        </div>
      ) : filteredMatches.length === 0 ? (
        <div className="text-center p-12 glass-panel" style={{ borderRadius: 'var(--radius-lg)' }}>
          <Trophy className="text-muted mx-auto mb-4" size={48} />
          <p className="text-secondary" style={{ marginBottom: '20px', fontSize: '1rem' }}>
            No hay partidos programados que coincidan con la búsqueda.
          </p>
          <button className="btn btn-secondary focusable" tabIndex={0} onClick={() => setSearchQuery('')}>
            Limpiar búsqueda
          </button>
        </div>
      ) : (
        <div className="sports-grid">
          {filteredMatches.map((match) => {
            const styleInfo = getSportStyle(match.sport);
            const hasTeams = match.team1 || match.team2;
            return (
              <div 
                key={match.id} 
                className="sports-card focusable"
                tabIndex={0}
                onClick={() => handleSelectMatch(match)}
                onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleSelectMatch(match); } }}
              >
                <div 
                  className="sports-card-banner"
                  style={{ background: styleInfo.gradient }}
                >
                  <span className="sports-card-badge card-badge tv" style={{ background: styleInfo.color }}>
                    {match.sport}
                  </span>
                  <span className="sports-card-time">
                    {match.time}
                  </span>
                  
                  {hasTeams ? (
                    <div className="sports-card-vs-logos">
                      <div className="sports-card-team-logo-wrapper">
                        {match.logo1 ? (
                          <img src={match.logo1} alt={match.team1} className="sports-card-team-logo" />
                        ) : (
                          <div className="sports-card-team-initials">
                            {match.team1 ? match.team1.slice(0, 3).toUpperCase() : '???'}
                          </div>
                        )}
                      </div>
                      <span className="sports-card-vs-text">VS</span>
                      <div className="sports-card-team-logo-wrapper">
                        {match.logo2 ? (
                          <img src={match.logo2} alt={match.team2} className="sports-card-team-logo" />
                        ) : (
                          <div className="sports-card-team-initials">
                            {match.team2 ? match.team2.slice(0, 3).toUpperCase() : '???'}
                          </div>
                        )}
                      </div>
                    </div>
                  ) : (
                    <div className="sports-card-icon-container">
                      <span style={{ fontSize: '1.8rem' }}>{styleInfo.emoji}</span>
                    </div>
                  )}
                </div>
                <div className="sports-card-content">
                  <h3 className="sports-card-title">{match.title}</h3>
                  <div className="sports-card-footer">
                    <span className="sports-card-streams-count">
                      {match.streams ? `${match.streams.length} servidores` : 'Sin señal'}
                    </span>
                    <span className="sports-card-action">
                      <Play size={12} fill="currentColor" /> Ver Transmisión
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* RESOLVER LOADING OVERLAY */}
      {(isResolving || (activeWatchSource && activeWatchSource.isResolving)) && (
        <div className="watch-overlay" style={{ justifyContent: 'center', alignItems: 'center', zIndex: 9999 }}>
          <div style={{ textAlign: 'center', padding: '36px', maxWidth: '420px' }} className="glass-panel form-card">
            <Loader className="spin-anim mx-auto mb-4" size={48} style={{ color: 'var(--primary-light)', marginBottom: '16px' }} />
            <h3 style={{ fontSize: '1.4rem', fontWeight: 600, color: '#fff', marginBottom: '6px' }}>
              {activeWatchSource?.title || 'Sintonizando transmisión...'}
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--primary-light)', marginBottom: '4px', fontWeight: 500 }}>
              🔍 Buscando servidor más estable...
            </p>
            <p className="text-secondary" style={{ fontSize: '0.85rem', marginBottom: '20px' }}>
              Evadiendo protección contra bots y resolviendo video directo
            </p>
            <button
              className="btn btn-secondary focusable"
              tabIndex={0}
              onClick={() => { setActiveWatchSource(null); setIsResolving(false); }}
            >
              Cancelar
            </button>
          </div>
        </div>
      )}

      {/* RESOLVER ERROR / DEBUG OVERLAY */}
      {resolveError && (
        <div className="watch-overlay" style={{ justifyContent: 'center', alignItems: 'center', zIndex: 9999 }}>
          <div style={{ textAlign: 'center', maxWidth: '550px', padding: '24px' }} className="glass-panel form-card">
            <h3 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--accent)', marginBottom: '12px' }}>
              Error de Conexión
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

      {/* Internal Video Player Overlay */}
      {activeWatchSource && !activeWatchSource.isResolving && (
        <VideoPlayer
          source={activeWatchSource}
          onClose={() => setActiveWatchSource(null)}
        />
      )}
    </div>
  );
}
