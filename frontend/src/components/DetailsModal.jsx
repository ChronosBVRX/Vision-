import React, { useEffect, useRef, useState, useCallback } from 'react';
import { Play, Plus, X, Star, Clock, Calendar, Globe, Tv, Film, MonitorPlay, ChevronRight } from 'lucide-react';

// ─── Resolution Badge Color ────────────────────────────────────────────────────
function resolutionColor(res = '') {
  if (res.includes('4K')) return 'linear-gradient(135deg, #a855f7, #7c3aed)';
  if (res.includes('1080')) return 'linear-gradient(135deg, #3b82f6, #1d4ed8)';
  return 'linear-gradient(135deg, #10b981, #059669)';
}

// ─── Skeleton block ───────────────────────────────────────────────────────────
function Skeleton({ width = '100%', height = '1em', style = {} }) {
  return (
    <div style={{
      width, height,
      borderRadius: '6px',
      background: 'linear-gradient(90deg, rgba(255,255,255,0.06) 25%, rgba(255,255,255,0.12) 50%, rgba(255,255,255,0.06) 75%)',
      backgroundSize: '200% 100%',
      animation: 'shimmer 1.6s infinite',
      ...style
    }} />
  );
}

// ─── Main Modal ───────────────────────────────────────────────────────────────
export default function DetailsModal({ item, onClose, onPlay }) {
  const [meta, setMeta]           = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const playButtonRef             = useRef(null);

  // Fetch real metadata from backend
  const fetchMetadata = useCallback(async () => {
    if (!item) return;
    setIsLoading(true);
    setMeta(null);
    try {
      const params = new URLSearchParams({
        title: item.title || '',
        type: item.type || 'movie',
        year: item.year || '',
      });
      const r = await fetch(`/api/metadata/${encodeURIComponent(item.id)}?${params}`);
      const data = await r.json();
      if (data.success && data.metadata) {
        setMeta(data.metadata);
      } else {
        // Fallback to item data
        setMeta({
          ...item,
          languages: ['Español Latino', 'Inglés'],
          resolution: '1080p HD',
          duration: item.duration || null,
        });
      }
    } catch {
      setMeta({
        ...item,
        languages: ['Español Latino', 'Inglés'],
        resolution: '1080p HD',
        duration: item.duration || null,
      });
    } finally {
      setIsLoading(false);
      setTimeout(() => playButtonRef.current?.focus(), 100);
    }
  }, [item]);

  useEffect(() => { fetchMetadata(); }, [fetchMetadata]);

  // ESC / Backspace to close
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' || e.key === 'Backspace') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  if (!item) return null;

  const typeLabel = item.type === 'tv' ? 'TV en Vivo' : item.type === 'series' ? 'Serie' : 'Película';
  const TypeIcon  = item.type === 'tv' ? Tv : item.type === 'series' ? Tv : Film;
  const poster    = meta?.poster || item.poster || '';
  const bgStyle   = poster
    ? { backgroundImage: `url(${poster})` }
    : { background: 'linear-gradient(135deg, #0d1122 0%, #1a0d40 100%)' };

  const formatDuration = (min) => {
    if (!min) return null;
    const h = Math.floor(min / 60);
    const m = min % 60;
    return h > 0 ? `${h}h ${m}min` : `${m}min`;
  };

  return (
    <div className="details-modal-overlay" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      {/* shimmer keyframe */}
      <style>{`@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }`}</style>

      <div className="details-modal-card">

        {/* ── Poster / Background ────────────────────────────────────────── */}
        <div className="details-modal-bg" style={bgStyle}>
          <div className="details-modal-bg-overlay" />
        </div>

        {/* ── Close button ───────────────────────────────────────────────── */}
        <button className="details-close-btn focusable" onClick={onClose} aria-label="Cerrar">
          <X size={24} />
        </button>

        {/* ── Content ────────────────────────────────────────────────────── */}
        <div className="details-modal-body">

          {/* Poster thumbnail on left (desktop) */}
          <div className="details-poster-col">
            {poster
              ? <img src={poster} alt={item.title} className="details-poster-img" />
              : <div className="details-poster-placeholder"><TypeIcon size={48} /></div>
            }
          </div>

          {/* Info on right */}
          <div className="details-info-col">

            {/* Type badge */}
            <span className={`details-badge ${item.type}`} style={{ marginBottom: '10px', display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
              <TypeIcon size={13} />
              {typeLabel}
            </span>

            {/* Title */}
            <h1 className="details-title">{item.title}</h1>

            {/* Meta row */}
            <div className="details-meta-row">
              {isLoading ? (
                <>
                  <Skeleton width="60px" height="22px" />
                  <Skeleton width="50px" height="22px" />
                  <Skeleton width="70px" height="22px" />
                </>
              ) : (
                <>
                  {/* Resolution */}
                  <span className="details-meta-badge" style={{ background: resolutionColor(meta?.resolution), color: '#fff', fontWeight: 700, letterSpacing: '0.5px', fontSize: '0.72rem' }}>
                    <MonitorPlay size={12} /> {meta?.resolution || 'HD'}
                  </span>
                  {/* Rating */}
                  {(meta?.rating || item.rating) && (
                    <span className="details-meta-badge" style={{ background: 'rgba(250,204,21,0.15)', color: '#fbbf24', border: '1px solid rgba(250,204,21,0.3)' }}>
                      <Star size={12} fill="currentColor" />
                      {parseFloat(meta?.rating || item.rating).toFixed(1)}
                    </span>
                  )}
                  {/* Year */}
                  {(meta?.year || item.year) && (
                    <span className="details-meta-badge">
                      <Calendar size={12} />
                      {meta?.year || item.year}
                    </span>
                  )}
                  {/* Duration */}
                  {meta?.duration && (
                    <span className="details-meta-badge">
                      <Clock size={12} />
                      {formatDuration(meta.duration)}
                    </span>
                  )}
                </>
              )}
            </div>

            {/* Genres */}
            <div className="details-genres-row">
              {isLoading ? (
                <><Skeleton width="70px" height="26px" style={{ borderRadius: '20px' }} /><Skeleton width="60px" height="26px" style={{ borderRadius: '20px' }} /><Skeleton width="80px" height="26px" style={{ borderRadius: '20px' }} /></>
              ) : (
                (meta?.genres?.length > 0 ? meta.genres : item.genres || []).slice(0, 4).map(g => (
                  <span key={g} className="details-genre-chip">{g}</span>
                ))
              )}
            </div>

            {/* Synopsis */}
            <div className="details-synopsis-block">
              <h3 className="details-synopsis-label">Sinopsis</h3>
              {isLoading ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <Skeleton height="0.9em" />
                  <Skeleton height="0.9em" />
                  <Skeleton height="0.9em" width="75%" />
                </div>
              ) : (
                <p className="details-synopsis-text">
                  {meta?.description || item.description ||
                    'Prepárate para disfrutar de este increíble título. Sumérgete en una historia llena de emoción y aventura disponible ahora mismo en la mejor calidad.'}
                </p>
              )}
            </div>

            {/* Languages & Availability */}
            <div className="details-availability-block">
              {isLoading ? (
                <Skeleton width="180px" height="18px" />
              ) : (
                <div className="details-languages-row">
                  <Globe size={14} style={{ color: 'var(--primary-light)', flexShrink: 0 }} />
                  <span className="details-lang-text">
                    {(meta?.languages || ['Español Latino']).join(' • ')}
                  </span>
                </div>
              )}
            </div>

            {/* Action buttons */}
            <div className="details-actions">
              <button
                ref={playButtonRef}
                className="btn btn-primary btn-large focusable details-play-btn"
                onClick={() => { onClose(); onPlay(item); }}
                disabled={isLoading}
              >
                <Play fill="currentColor" size={20} />
                <span>Reproducir</span>
                <ChevronRight size={16} style={{ marginLeft: '4px', opacity: 0.7 }} />
              </button>

              <button className="btn btn-secondary btn-icon focusable" title="Agregar a Mi Lista">
                <Plus size={22} />
              </button>
            </div>

          </div>
        </div>
      </div>
    </div>
  );
}
