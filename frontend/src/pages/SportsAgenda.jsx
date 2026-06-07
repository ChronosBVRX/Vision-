import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { RefreshCw, Filter, Calendar, Zap, Tv, AlertCircle, Search, ChevronDown } from 'lucide-react';
import useCache from '../hooks/useCache';
import useDebounce from '../hooks/useDebounce';
import SportsEventCard from '../components/SportsEventCard';
import WatchOptionsModal from '../components/WatchOptionsModal';
import VideoPlayer from '../components/VideoPlayer';
import { getSportsEvents, refreshSportsEvents } from '../services/sportsApi';

const SPORTS = ['Todos', 'Soccer', 'Basketball', 'Baseball', 'Motorsport', 'Tennis', 'Boxing', 'MMA', 'American_Football'];
const SORT_OPTIONS = [
  { value: 'interest', label: 'Más relevantes' },
  { value: 'time', label: 'Próximos' },
  { value: 'sport', label: 'Por deporte' }
];

function groupByDate(events) {
  const groups = {};
  for (const ev of events) {
    let key = 'Sin fecha';
    if (ev.startTime) {
      try {
        const d = new Date(ev.startTime);
        if (!isNaN(d.getTime())) {
          const today = new Date();
          const tomorrow = new Date(today);
          tomorrow.setDate(tomorrow.getDate() + 1);
          if (d.toDateString() === today.toDateString()) key = 'Hoy';
          else if (d.toDateString() === tomorrow.toDateString()) key = 'Mañana';
          else key = d.toLocaleDateString('es-MX', { weekday: 'long', month: 'long', day: 'numeric' });
        }
      } catch {}
    }
    if (!groups[key]) groups[key] = [];
    groups[key].push(ev);
  }
  return groups;
}

export default function SportsAgenda() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [sportFilter, setSportFilter] = useState('Todos');
  const [sortBy, setSortBy] = useState('interest');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedEvent, setSelectedEvent] = useState(null);
  const [showWatchModal, setShowWatchModal] = useState(false);
  const [activePlayerEvent, setActivePlayerEvent] = useState(null);
  const [showFilters, setShowFilters] = useState(false);
  const debouncedSearch = useDebounce(searchQuery, 300);

  const fetchEvents = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const data = await getSportsEvents({ minScore: 1, limit: 200 });
      if (data.success) {
        setEvents(data.events || []);
      } else {
        setError(data.error || 'Error al cargar eventos');
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchEvents(); }, [fetchEvents]);

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await refreshSportsEvents();
      await fetchEvents();
    } catch (err) {
      setError(err.message);
    } finally {
      setRefreshing(false);
    }
  };

  const filteredEvents = useMemo(() => {
    let result = [...events];

    if (sportFilter !== 'Todos') {
      result = result.filter(e => e.sport === sportFilter);
    }

    if (debouncedSearch) {
      const q = debouncedSearch.toLowerCase();
      result = result.filter(e =>
        (e.title || '').toLowerCase().includes(q) ||
        (e.league || '').toLowerCase().includes(q) ||
        (e.homeTeam || '').toLowerCase().includes(q) ||
        (e.awayTeam || '').toLowerCase().includes(q)
      );
    }

    if (sortBy === 'interest') {
      result.sort((a, b) => (b.interestScore || 0) - (a.interestScore || 0));
    } else if (sortBy === 'time') {
      result.sort((a, b) => {
        const ta = a.startTime ? new Date(a.startTime).getTime() : 0;
        const tb = b.startTime ? new Date(b.startTime).getTime() : 0;
        return ta - tb;
      });
    } else if (sortBy === 'sport') {
      result.sort((a, b) => (a.sport || '').localeCompare(b.sport || ''));
    }

    return result;
  }, [events, sportFilter, debouncedSearch, sortBy]);

  const grouped = useMemo(() => groupByDate(filteredEvents), [filteredEvents]);
  const dateKeys = Object.keys(grouped);
  const todayFirst = ['Hoy', 'Mañana'];
  const sortedDateKeys = [...todayFirst.filter(k => dateKeys.includes(k)), ...dateKeys.filter(k => !todayFirst.includes(k))];

  const handleWatchOptions = (event) => {
    setSelectedEvent(event);
    setShowWatchModal(true);
  };

  const handlePlayChannel = (option) => {
    if (option.channelId && option.type === 'internal_channel') {
      const channel = {
        id: option.channelId,
        title: option.channelName || option.label,
        type: 'tv',
        streams: [{ name: 'Stream Directo', url: '', resolver: 'direct' }]
      };
      setActivePlayerEvent(channel);
      setShowWatchModal(false);
    }
  };

  return (
    <div className="sports-agenda">
      <div className="sports-header">
        <div className="sports-title-area">
          <h1><span className="sports-icon">🏆</span> Deportes</h1>
          <p className="sports-subtitle">Agenda inteligente de eventos globales y LATAM</p>
        </div>
        <div className="sports-header-actions">
          <button
            className="sports-filter-toggle focusable"
            onClick={() => setShowFilters(o => !o)}
            tabIndex={0}
          >
            <Filter size={16} /> Filtros <ChevronDown size={14} />
          </button>
          <button
            className="sports-refresh-btn focusable"
            onClick={handleRefresh}
            disabled={refreshing}
            tabIndex={0}
          >
            <RefreshCw size={16} className={refreshing ? 'spin' : ''} />
            {refreshing ? 'Actualizando...' : 'Actualizar'}
          </button>
        </div>
      </div>

      <div className={`sports-filters ${showFilters ? 'open' : ''}`}>
        <div className="sports-filter-row">
          <div className="sports-filter-group">
            <label>Deporte</label>
            <div className="sports-chips">
              {SPORTS.map(s => (
                <button
                  key={s}
                  className={`sports-chip focusable ${sportFilter === s ? 'active' : ''}`}
                  onClick={() => setSportFilter(s)}
                  tabIndex={0}
                >
                  {s === 'Todos' ? 'Todos' : s.replace('_', ' ')}
                </button>
              ))}
            </div>
          </div>

          <div className="sports-filter-group">
            <label>Ordenar por</label>
            <div className="sports-chips">
              {SORT_OPTIONS.map(opt => (
                <button
                  key={opt.value}
                  className={`sports-chip focusable ${sortBy === opt.value ? 'active' : ''}`}
                  onClick={() => setSortBy(opt.value)}
                  tabIndex={0}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="sports-search">
          <Search size={16} />
          <input
            type="text"
            placeholder="Buscar equipos, ligas, deportes..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="sports-search-input"
          />
        </div>
      </div>

      <div className="sports-stats">
        <span><Zap size={14} /> {events.filter(e => e.interestScore > 50).length} destacados</span>
        <span><Calendar size={14} /> {events.length} eventos encontrados</span>
        <span><Tv size={14} /> {events.filter(e => e.channels && e.channels.length > 0).length} con canales</span>
      </div>

      {error && (
        <div className="sports-error">
          <AlertCircle size={16} /> {error}
        </div>
      )}

      {loading && (
        <div className="sports-loading">
          <div className="spin-anim" style={{ width: 32, height: 32, borderWidth: 3 }} />
          <span>Cargando agenda deportiva...</span>
        </div>
      )}

      {!loading && !error && filteredEvents.length === 0 && (
        <div className="sports-empty">
          <Calendar size={48} />
          <h3>No hay eventos deportivos</h3>
          <p>Intenta actualizar la agenda o cambia los filtros.</p>
          <button className="sports-refresh-btn focusable" onClick={handleRefresh} tabIndex={0}>
            <RefreshCw size={16} /> Actualizar agenda
          </button>
        </div>
      )}

      {!loading && sortedDateKeys.map(dateKey => (
        <div key={dateKey} className="sports-day-group">
          <h2 className="sports-day-title">{dateKey}</h2>
          <div className="sports-events-grid">
            {grouped[dateKey].map(event => (
              <SportsEventCard
                key={event.id}
                event={event}
                onWatchOptions={handleWatchOptions}
              />
            ))}
          </div>
        </div>
      ))}

      {showWatchModal && selectedEvent && (
        <WatchOptionsModal
          event={selectedEvent}
          onClose={() => { setShowWatchModal(false); setSelectedEvent(null); }}
          onPlayChannel={handlePlayChannel}
        />
      )}

      {activePlayerEvent && (
        <div className="sports-player-overlay">
          <button
            className="sports-player-close focusable"
            onClick={() => setActivePlayerEvent(null)}
            tabIndex={0}
          >
            ✕ Cerrar reproductor
          </button>
          <VideoPlayer
            stream={activePlayerEvent.streams?.[0] || {}}
            title={activePlayerEvent.title}
            type="tv"
            onClose={() => setActivePlayerEvent(null)}
          />
        </div>
      )}
    </div>
  );
}
