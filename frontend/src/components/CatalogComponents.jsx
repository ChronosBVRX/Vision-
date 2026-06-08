import React, { useState, useEffect, useRef, useCallback, memo } from 'react';
import { Play, Star } from 'lucide-react';
import TrailerPlayer from './TrailerPlayer';


// ─── Fallback SVG poster when image fails ─────────────────────────────────────
export function getFallbackPoster(type) {
  const bg = type === 'series' ? '%230d2040' : '%231a1040';
  const emoji = type === 'series' ? '📺' : '🎬';
  return `data:image/svg+xml;charset=utf-8,<svg xmlns='http://www.w3.org/2000/svg' width='300' height='450'><rect width='300' height='450' fill='${bg}'/><text x='150' y='200' font-size='80' text-anchor='middle' dominant-baseline='middle' fill='rgba(255,255,255,0.15)'>${emoji}</text><text x='150' y='290' font-family='system-ui,sans-serif' font-size='13' text-anchor='middle' fill='rgba(255,255,255,0.25)'>Sin portada</text></svg>`;
}

// ─── Hero Banner Component ───────────────────────────────────────────────────
export function HeroBanner({ item, totalItems, currentIndex, onIndexChange, onPlay, isMovie }) {
  const [imgLoaded, setImgLoaded] = useState(false);
  const [showTrailer, setShowTrailer] = useState(false);
  const trailerTimerRef = useRef(null);

  useEffect(() => { 
    setImgLoaded(false); 
    setShowTrailer(false);
    if (trailerTimerRef.current) clearTimeout(trailerTimerRef.current);
    trailerTimerRef.current = setTimeout(() => {
      setShowTrailer(true);
    }, 1500);
    return () => clearTimeout(trailerTimerRef.current);
  }, [item?.id]);

  return (
    <div className="catalog-hero" aria-label="Película destacada">
      {/* Background image */}
      {!showTrailer && (
        <div
          className={`catalog-hero-bg ${imgLoaded ? 'loaded' : ''}`}
          style={{ backgroundImage: item.poster ? `url(${item.poster})` : 'none' }}
        />
      )}
      <TrailerPlayer item={item} isPlaying={showTrailer} />
      {/* Preload trigger */}
      <img src={item.poster} alt="" onLoad={() => setImgLoaded(true)} onError={() => setImgLoaded(true)} style={{ display: 'none' }} />

      {/* Gradients */}
      <div className="catalog-hero-grad-left" />
      <div className="catalog-hero-grad-bottom" />

      {/* Content */}
      <div className="catalog-hero-content">
        {/* Tags row */}
        <div className="catalog-hero-tags">
          <span className="catalog-tag catalog-tag--type">
            {item.type === 'tv' ? '📡 En Vivo' : item.type === 'series' ? '📺 Serie' : '🎬 Película'}
          </span>
          {item.year && <span className="catalog-tag">{item.year}</span>}
          {item.rating && (
            <span className="catalog-tag catalog-tag--rating">
              <Star size={11} fill="gold" color="gold" />
              {typeof item.rating === 'number' ? item.rating.toFixed(1) : item.rating}
            </span>
          )}
          {item.siteName && <span className="catalog-tag">{item.siteName}</span>}
        </div>

        {/* Title */}
        <h1 className="catalog-hero-title">{item.title}</h1>

        {/* Genres */}
        {item.genres?.length > 0 && (
          <div className="catalog-hero-genres">
            {item.genres.filter(g => !g.startsWith('⭐') && !g.startsWith('🔥')).slice(0, 4).map(g => (
              <span key={g} className="catalog-genre-chip">{g}</span>
            ))}
          </div>
        )}

        {/* Description */}
        {item.description && item.description.length > 10 && (
          <p className="catalog-hero-desc">
            {item.description.slice(0, 200)}{item.description.length > 200 ? '...' : ''}
          </p>
        )}

        {/* Actions */}
        <div className="catalog-hero-actions">
          <button className="catalog-btn-play focusable" tabIndex={0} onClick={() => onPlay(item)}>
            <Play size={20} fill="currentColor" />
            Reproducir
          </button>
        </div>
      </div>

      {/* Dot indicators for hero rotation */}
      {totalItems > 1 && (
        <div className="catalog-hero-dots">
          {Array.from({ length: Math.min(totalItems, 10) }).map((_, i) => (
            <button
              key={i}
              className={`catalog-hero-dot ${i === currentIndex ? 'active' : ''}`}
              onClick={() => onIndexChange(i)}
              aria-label={`Elemento ${i + 1}`}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Horizontal Genre Row ─────────────────────────────────────────────────────
export const CatalogRow = memo(function CatalogRow({ id, title, items, isActive, onPlay, onFocus, onItemFocus }) {
  const trackRef   = useRef(null);
  const touchDragRef = useRef({ active: false, moved: false, startX: 0, scrollLeft: 0 });
  const suppressClickRef = useRef(false);
  const [focusIdx, setFocusIdx] = useState(0);
  const [canLeft, setCanLeft]   = useState(false);
  const [canRight, setCanRight] = useState(true);

  const updateArrows = useCallback(() => {
    const el = trackRef.current;
    if (!el) return;
    setCanLeft(el.scrollLeft > 10);
    setCanRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 10);
  }, []);

  // Navigate card-by-card with ArrowLeft / ArrowRight when row is active
  useEffect(() => {
    if (!isActive) return;
    const isTV = typeof window !== 'undefined' && window.isSmartTV;
    const scrollOpt = { behavior: isTV ? 'auto' : 'smooth', block: 'nearest', inline: 'center' };
    const handler = (e) => {
      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
      if (!trackRef.current?.contains(document.activeElement)) return;

      const cards = Array.from(trackRef.current?.querySelectorAll('.catalog-card') || []);
      if (cards.length === 0) return;

      const currentIdx = cards.indexOf(document.activeElement);
      if (e.key === 'ArrowLeft' && currentIdx === 0 && trackRef.current.scrollLeft <= 10) {
        return; // Let global useSpatialNavigation handle jumping to sidebar
      }

      e.preventDefault();
      e.stopPropagation(); // Prevent useSpatialNavigation (bubble) from double-handling
      setFocusIdx(() => {
        const next = e.key === 'ArrowLeft'
          ? Math.max(0, currentIdx - 1)
          : Math.min(cards.length - 1, currentIdx + 1);
        cards[next]?.focus();
        cards[next]?.scrollIntoView(scrollOpt);
        return next;
      });
    };
    window.addEventListener('keydown', handler, true); // capture → fires BEFORE any bubble handler
    return () => window.removeEventListener('keydown', handler, true);
  }, [isActive]);

  // Reset focus index and focus first card when row becomes active
  useEffect(() => {
    if (!isActive) return;
    const isTV = typeof window !== 'undefined' && window.isSmartTV;
    const isTouchDevice = typeof window !== 'undefined' && (
      window.matchMedia?.('(pointer: coarse)')?.matches ||
      navigator.maxTouchPoints > 0
    );
    setFocusIdx(0);
    if (isTouchDevice && !isTV) return;

    const cards = Array.from(trackRef.current?.querySelectorAll('.catalog-card') || []);
    if (cards.length > 0) {
      cards[0].focus();
      cards[0].scrollIntoView({ behavior: isTV ? 'auto' : 'smooth', block: 'nearest', inline: 'start' });
    }
  }, [isActive]);

  const clearSuppressedClick = useCallback(() => {
    window.setTimeout(() => {
      suppressClickRef.current = false;
    }, 80);
  }, []);

  const handlePointerDown = useCallback((e) => {
    if (e.pointerType && e.pointerType !== 'touch' && e.pointerType !== 'pen') return;
    const el = trackRef.current;
    if (!el) return;

    touchDragRef.current = {
      active: true,
      moved: false,
      startX: e.clientX,
      scrollLeft: el.scrollLeft
    };
    e.currentTarget.setPointerCapture?.(e.pointerId);
  }, []);

  const handlePointerMove = useCallback((e) => {
    const drag = touchDragRef.current;
    const el = trackRef.current;
    if (!drag.active || !el) return;

    const dx = e.clientX - drag.startX;
    if (Math.abs(dx) > 6) {
      drag.moved = true;
      suppressClickRef.current = true;
      el.scrollLeft = drag.scrollLeft - dx;
      updateArrows();
      e.preventDefault();
    }
  }, [updateArrows]);

  const handlePointerEnd = useCallback(() => {
    const drag = touchDragRef.current;
    touchDragRef.current = { active: false, moved: false, startX: 0, scrollLeft: 0 };
    if (drag.moved) {
      suppressClickRef.current = true;
      clearSuppressedClick();
    }
  }, [clearSuppressedClick]);

  if (!items || items.length === 0) return null;

  return (
    <section
      id={id}
      className={`catalog-row ${isActive ? 'catalog-row--active' : ''}`}
      onFocus={onFocus}
      onMouseEnter={() => {
        if (document.body.classList.contains('keyboard-mode')) return;
        onFocus?.();
      }}
    >
      <h2 className="catalog-row-title">{title}</h2>
      <div className="catalog-row-wrapper">
        <div
          ref={trackRef}
          className="catalog-row-track"
          onScroll={updateArrows}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerEnd}
          onPointerCancel={handlePointerEnd}
          style={{ touchAction: 'pan-y', WebkitOverflowScrolling: 'touch', overscrollBehaviorX: 'contain' }}
        >
          {items.map((item, idx) => (
            <CatalogCard
              key={`${item.id}-${idx}`}
              item={item}
              onPlay={onPlay}
              onFocus={() => onItemFocus?.(item)}
              shouldSuppressClick={() => suppressClickRef.current}
            />
          ))}
        </div>
      </div>
    </section>
  );
});

// ─── Individual Movie / Series Card ──────────────────────────────────────────
export const CatalogCard = memo(function CatalogCard({ item, onPlay, onFocus, shouldSuppressClick }) {
  const [imgErr, setImgErr] = useState(false);

  return (
    <div
      className="catalog-card focusable"
      tabIndex={0}
      role="button"
      aria-label={`Reproducir ${item.title}`}
      onClick={(e) => {
        if (shouldSuppressClick?.()) {
          e.preventDefault();
          e.stopPropagation();
          return;
        }
        onPlay(item);
      }}
      onFocus={onFocus}
      onMouseEnter={() => {
        if (document.body.classList.contains('keyboard-mode')) return;
        onFocus?.();
      }}
      onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onPlay(item); } }}
    >
      <div className="catalog-card-poster">
        <img
          src={imgErr || !item.poster ? getFallbackPoster(item.type) : item.poster}
          alt={item.title}
          loading="lazy"
          onError={() => setImgErr(true)}
        />
        {/* Hover play overlay */}
        <div className="catalog-card-overlay">
          <div className="catalog-card-play-circle">
            <Play size={24} fill="white" color="white" />
          </div>
        </div>
        {/* Badges */}
        {item.type === 'tv' && <span className="catalog-badge catalog-badge--type" style={{background: 'var(--secondary)'}}>EN VIVO</span>}
        {item.year && <span className="catalog-badge catalog-badge--year">{item.year}</span>}
        {item.rating && (
          <span className="catalog-badge catalog-badge--rating">
            <Star size={9} fill="gold" color="gold" />
            {typeof item.rating === 'number' ? item.rating.toFixed(1) : item.rating}
          </span>
        )}
        {item.siteName && (
          <span className="catalog-badge catalog-badge--site">{item.siteName}</span>
        )}
      </div>
      <div className="catalog-card-meta">
        <h3 className="catalog-card-title" title={item.title}>{item.title}</h3>
        {item.genres?.[0] && !/^[⭐🔥]/.test(item.genres[0]) && (
          <span className="catalog-card-genre">{item.genres[0]}</span>
        )}
        {!item.genres?.[0] && item.category && (
          <span className="catalog-card-genre">{item.category}</span>
        )}
      </div>
    </div>
  );
});
