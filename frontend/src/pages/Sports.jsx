import React, { useState, useEffect, useRef } from 'react';
import { PlayCircle, Search, RefreshCw, AlertCircle, Play, Globe, Settings, Clock, Activity, Loader, X, Trophy } from 'lucide-react';
import VideoPlayer from '../components/VideoPlayer';
import LoadingScreen from '../components/LoadingScreen';

const getSportStyle = (sport) => {
  const norm = (sport || '').toLowerCase().trim();
  if (norm.includes('baloncesto') || norm.includes('nba') || norm.includes('basket') || norm.includes('basquet')) {
    return {
      gradient: 'linear-gradient(135deg, rgba(249, 115, 22, 0.25) 0%, rgba(234, 88, 12, 0.05) 60%, rgba(13, 17, 34, 0.95) 100%)',
      emoji: '🏀',
      color: '#ea580c',
      shadow: '0 8px 32px rgba(234, 88, 12, 0.15)'
    };
  }
  if (norm.includes('formula') || norm.includes('f1') || norm.includes('moto') || norm.includes('carrera') || norm.includes('gp') || norm.includes('automovilismo')) {
    return {
      gradient: 'linear-gradient(135deg, rgba(239, 68, 68, 0.25) 0%, rgba(220, 38, 38, 0.05) 60%, rgba(13, 17, 34, 0.95) 100%)',
      emoji: '🏁',
      color: '#ef4444',
      shadow: '0 8px 32px rgba(239, 68, 68, 0.15)'
    };
  }
  if (norm.includes('tenis') || norm.includes('tennis')) {
    return {
      gradient: 'linear-gradient(135deg, rgba(132, 204, 22, 0.25) 0%, rgba(101, 163, 13, 0.05) 60%, rgba(13, 17, 34, 0.95) 100%)',
      emoji: '🎾',
      color: '#84cc16',
      shadow: '0 8px 32px rgba(132, 204, 22, 0.15)'
    };
  }
  if (norm.includes('combate') || norm.includes('ufc') || norm.includes('box') || norm.includes('lucha') || norm.includes('mma')) {
    return {
      gradient: 'linear-gradient(135deg, rgba(107, 114, 128, 0.25) 0%, rgba(75, 85, 99, 0.05) 60%, rgba(13, 17, 34, 0.95) 100%)',
      emoji: '🥊',
      color: '#6b7280',
      shadow: '0 8px 32px rgba(107, 114, 128, 0.15)'
    };
  }
  if (norm.includes('beisbol') || norm.includes('baseball') || norm.includes('mlb')) {
    return {
      gradient: 'linear-gradient(135deg, rgba(2, 132, 199, 0.25) 0%, rgba(3, 105, 161, 0.05) 60%, rgba(13, 17, 34, 0.95) 100%)',
      emoji: '⚾',
      color: '#0284c7',
      shadow: '0 8px 32px rgba(2, 132, 199, 0.15)'
    };
  }
  if (norm.includes('americano') || norm.includes('nfl')) {
    return {
      gradient: 'linear-gradient(135deg, rgba(139, 92, 246, 0.25) 0%, rgba(109, 40, 217, 0.05) 60%, rgba(13, 17, 34, 0.95) 100%)',
      emoji: '🏈',
      color: '#8b5cf6',
      shadow: '0 8px 32px rgba(139, 92, 246, 0.15)'
    };
  }
  if (norm.includes('futbol') || norm.includes('soccer')) {
    return {
      gradient: 'linear-gradient(135deg, rgba(16, 185, 129, 0.25) 0%, rgba(4, 120, 87, 0.05) 60%, rgba(13, 17, 34, 0.95) 100%)',
      emoji: '⚽',
      color: '#10b981',
      shadow: '0 8px 32px rgba(16, 185, 129, 0.15)'
    };
  }
  return {
    gradient: 'linear-gradient(135deg, rgba(99, 102, 241, 0.25) 0%, rgba(79, 70, 229, 0.05) 60%, rgba(13, 17, 34, 0.95) 100%)',
    emoji: '🏆',
    color: '#6366f1',
    shadow: '0 8px 32px rgba(99, 102, 241, 0.15)'
  };
};

const getTeamInitialsBg = (teamName) => {
  if (!teamName) return 'linear-gradient(135deg, #374151 0%, #111827 100%)';
  let hash = 0;
  for (let i = 0; i < teamName.length; i++) {
    hash = teamName.charCodeAt(i) + ((hash << 5) - hash);
  }
  const hue = Math.abs(hash % 360);
  return `linear-gradient(135deg, hsl(${hue}, 60%, 40%) 0%, hsl(${hue}, 70%, 18%) 100%)`;
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
  
  // Track last match for retry with different servers
  const [lastFailedMatch, setLastFailedMatch] = useState(null);
  const [excludedServers, setExcludedServers] = useState([]);

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

  // Open the video player and resolve the best stream for a match
  const handleSelectMatch = (match, excludeList = []) => {
    if (resolvingRef.current) return;
    resolvingRef.current = true;
    setIsResolving(true);
    setResolveError(null);
    setResolveAttempts([]);
    setLastFailedMatch(match);
    setExcludedServers(excludeList);

    setActiveWatchSource({ title: match.title, isResolving: true });

    fetch(`/api/live/resolve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ streams: match.streams, type: 'sports', excludeServer: excludeList.length > 0 ? excludeList : undefined })
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
            selectedStreamIndex: match.streams ? match.streams.findIndex(s => (s.name || `Servidor ${match.streams.indexOf(s) + 1}`) === data.selectedServer) : 0,
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
          setLastFailedMatch(null);
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

  // Retry with the next available server excluded
  const handleRetryWithNextServer = () => {
    if (!lastFailedMatch) return;
    // Find which server failed from the attempts
    const failedNames = resolveAttempts
      .filter(a => a.status === 'FAILED')
      .map(a => a.server)
      .filter(Boolean);
    const newExcludeList = [...new Set([...excludedServers, ...failedNames])];
    handleSelectMatch(lastFailedMatch, newExcludeList);
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
      <div className="sports-header-panel">
        <div className="sports-header-left">
          <div className="sports-title-container">
            <Trophy className="sports-header-icon" size={26} />
            <h2 className="sports-page-title">Deportes en Vivo</h2>
          </div>
          {activeMirror && (
            <div className="sports-mirror-badge">
              <span className="status-dot"></span>
              <span className="badge-text">
                Espejo Activo: <strong className="mirror-domain">{activeMirror.replace(/^https?:\/\/(www\.)?/, '')}</strong>
              </span>
            </div>
          )}
        </div>
        <div className="sports-header-right">
          {lastUpdated && (
            <span className="sports-update-time">
              <Clock size={14} /> Act. {getFormattedTime(lastUpdated)}
            </span>
          )}
          <button 
            className="btn-sports-action focusable" 
            tabIndex={0} 
            onClick={() => setSettingsOpen(!settingsOpen)} 
            title="Configurar dominios espejo"
          >
            <Settings size={16} />
            <span>Configurar</span>
          </button>
          <button 
            className="btn-sports-action primary focusable" 
            tabIndex={0}
            onClick={handleForceRefresh} 
            disabled={isRefreshing || loadingMatches}
          >
            <RefreshCw size={16} className={isRefreshing ? 'animate-spin' : ''} />
            <span>{isRefreshing ? 'Actualizando...' : 'Refrescar'}</span>
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
              <button type="submit" className="btn-sports-action primary focusable" tabIndex={0} disabled={savingSettings}>
                {savingSettings ? 'Guardando...' : 'Guardar y Recargar'}
              </button>
              <button type="button" className="btn-sports-action focusable" tabIndex={0} onClick={() => setSettingsOpen(false)}>
                Cancelar
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Search Input */}
      <div className="sports-search-container">
        <div className="sports-search-wrapper">
          <Search className="sports-search-icon" size={20} />
          <input
            type="text"
            placeholder="Busca eventos, equipos o deportes..."
            className="sports-search-input focusable"
            tabIndex={0}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <button 
              className="sports-search-clear focusable" 
              tabIndex={0}
              onClick={() => setSearchQuery('')}
              title="Limpiar búsqueda"
            >
              <X size={16} />
            </button>
          )}
        </div>
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
                    <span style={{ marginRight: '4px' }}>{styleInfo.emoji}</span>
                    {match.sport}
                  </span>
                  <span className="sports-card-time">
                    {match.time}
                  </span>
                  
                  {hasTeams ? (
                    <div className="sports-card-vs-logos">
                      <div className="sports-card-team-col">
                        <div className="sports-card-team-logo-wrapper">
                          {match.logo1 ? (
                            <img src={match.logo1} alt={match.team1} className="sports-card-team-logo" />
                          ) : (
                            <div 
                              className="sports-card-team-initials" 
                              style={{ 
                                background: getTeamInitialsBg(match.team1), 
                                width: '100%', 
                                height: '100%', 
                                display: 'flex', 
                                alignItems: 'center', 
                                justifyContent: 'center' 
                              }}
                            >
                              {match.team1 ? match.team1.slice(0, 3).toUpperCase() : '???'}
                            </div>
                          )}
                        </div>
                        <span className="sports-card-team-name">{match.team1}</span>
                      </div>
                      
                      <span className="sports-card-vs-text">VS</span>
                      
                      <div className="sports-card-team-col">
                        <div className="sports-card-team-logo-wrapper">
                          {match.logo2 ? (
                            <img src={match.logo2} alt={match.team2} className="sports-card-team-logo" />
                          ) : (
                            <div 
                              className="sports-card-team-initials" 
                              style={{ 
                                background: getTeamInitialsBg(match.team2), 
                                width: '100%', 
                                height: '100%', 
                                display: 'flex', 
                                alignItems: 'center', 
                                justifyContent: 'center' 
                              }}
                            >
                              {match.team2 ? match.team2.slice(0, 3).toUpperCase() : '???'}
                            </div>
                          )}
                        </div>
                        <span className="sports-card-team-name">{match.team2}</span>
                      </div>
                    </div>
                  ) : (
                    <div className="sports-card-icon-container">
                      <span style={{ fontSize: '2.2rem' }}>{styleInfo.emoji}</span>
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
        <LoadingScreen 
          type="resolver" 
          theme="sports"
          title={activeWatchSource?.title || 'Sintonizando transmisión...'} 
          onCancel={() => { setActiveWatchSource(null); setIsResolving(false); }} 
        />
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
            
            {/* Show tried servers */}
            {resolveAttempts.length > 0 && (
              <div style={{ textAlign: 'left', maxHeight: '160px', overflowY: 'auto', marginBottom: '16px', background: 'rgba(0,0,0,0.3)', padding: '12px', borderRadius: '6px' }}>
                <h4 style={{ fontSize: '0.85rem', color: '#aaa', marginBottom: '6px' }}>Servidores probados:</h4>
                {resolveAttempts.map((att, i) => (
                  <div key={i} style={{ fontSize: '0.78rem', color: att.status === 'SUCCESS' ? 'var(--primary)' : '#f44336', marginBottom: '3px' }}>
                    • {att.sourceName}: {att.status === 'SUCCESS' ? '✅' : '❌'} {att.reason}
                  </div>
                ))}
              </div>
            )}
            
            <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', flexWrap: 'wrap' }}>
              {lastFailedMatch && (
                <button className="btn btn-primary focusable" tabIndex={0} onClick={handleRetryWithNextServer}>
                  <RefreshCw size={16} /> Reintentar con otro servidor
                </button>
              )}
              <button ref={resolveErrorBtnRef} className="btn btn-secondary focusable" tabIndex={0} onClick={() => { setResolveError(null); setLastFailedMatch(null); }}>
                <X size={18} /> Cerrar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Internal Video Player Overlay */}
      {activeWatchSource && !activeWatchSource.isResolving && (
        <VideoPlayer
          source={activeWatchSource}
          onClose={() => setActiveWatchSource(null)}
          channelList={filteredMatches.map(m => ({
            id: m.id,
            title: m.title,
            group: m.sport || 'Deportes',
            category: m.sport || 'Deportes',
            streams: m.streams,
            type: 'sports',
            isSports: true
          }))}
          onChannelChange={(ch) => {
            const match = filteredMatches.find(m => m.id === ch.id);
            if (match) {
              handleSelectMatch(match);
            }
          }}
        />
      )}
    </div>
  );
}
