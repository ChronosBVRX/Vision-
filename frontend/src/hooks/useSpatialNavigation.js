import { useEffect, useRef } from 'react';

const NAV_KEYS = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', 'Escape', 'Backspace']);
const FOCUSABLE_SEL = '.focusable';

const isTV = typeof window !== 'undefined' && window.isSmartTV === true;

/**
 * Spatial navigation hook for Smart TV remote controls.
 *
 * Performance optimizations for Smart TV:
 *  - Throttle: max 1 navigation cycle per 100ms
 *  - Cached focusables: only re-query DOM when layout could have changed
 *  - Batch getBoundingClientRect: single layout thrash per cycle
 *  - No smooth scroll on TV (instant scroll)
 *  - Passive listeners for mouse events
 */
export default function useSpatialNavigation(isActive = true) {
  const focusableCache = useRef([]);
  const sidebarCache   = useRef([]);
  const cacheVersion   = useRef(0);
  const lastNavTime    = useRef(0);
  const throttleMs     = isTV ? 100 : 40;

  // ── Keyboard / Mouse mode detection ───────────────────────────────
  useEffect(() => {
    if (!isActive) return;

    let isKeyboardMode = false;
    let lastMouseX = null;
    let lastMouseY = null;
    const threshold = 15;

    const enterKeyboardMode = () => {
      if (isKeyboardMode) return;
      isKeyboardMode = true;
      document.body.classList.add('keyboard-mode');
      document.body.classList.remove('mouse-mode');
    };

    const enterMouseMode = () => {
      if (!isKeyboardMode) return;
      isKeyboardMode = false;
      document.body.classList.remove('keyboard-mode');
      document.body.classList.add('mouse-mode');
      lastMouseX = null;
      lastMouseY = null;
    };

    const handleKeyDownGlobal = (e) => {
      if (NAV_KEYS.has(e.key)) {
        enterKeyboardMode();
      }
    };

    const handleMouseMoveGlobal = (e) => {
      if (!isKeyboardMode) return;
      const x = e.screenX || e.clientX;
      const y = e.screenY || e.clientY;
      if (lastMouseX === null || lastMouseY === null) {
        lastMouseX = x;
        lastMouseY = y;
        return;
      }
      const dist = Math.sqrt((x - lastMouseX) ** 2 + (y - lastMouseY) ** 2);
      if (dist > threshold) {
        enterMouseMode();
      }
    };

    const handleTouchStartGlobal = () => { enterMouseMode(); };

    window.addEventListener('keydown', handleKeyDownGlobal, true);
    window.addEventListener('mousemove', handleMouseMoveGlobal, { capture: true, passive: true });
    window.addEventListener('touchstart', handleTouchStartGlobal, { capture: true, passive: true });

    document.body.classList.add('mouse-mode');

    return () => {
      window.removeEventListener('keydown', handleKeyDownGlobal, true);
      window.removeEventListener('mousemove', handleMouseMoveGlobal, true);
      window.removeEventListener('touchstart', handleTouchStartGlobal, true);
      document.body.classList.remove('keyboard-mode', 'mouse-mode');
    };
  }, [isActive]);

  // ── Spatial navigation handler ────────────────────────────────────
  useEffect(() => {
    if (!isActive) return;

    const invalidateCache = () => {
      cacheVersion.current += 1;
    };

    const getCachedFocusables = (restrictTo) => {
      if (cacheVersion.current === 0 || !focusableCache.current.length) {
        focusableCache.current = queryFocusables(null);
      }
      if (!restrictTo) return focusableCache.current;
      // filter — avoid full DOM re-query
      return focusableCache.current.filter(el => restrictTo.contains(el));
    };

    const getCachedSidebar = () => {
      if (cacheVersion.current === 0 || !sidebarCache.current.length) {
        sidebarCache.current = queryFocusables(document.querySelector('.sidebar'));
      }
      return sidebarCache.current;
    };

    const handleKeyDown = (e) => {
      if (!NAV_KEYS.has(e.key)) return;

      // ── Throttle: skip if too soon since last navigation ──────────
      const now = performance.now();
      if (now - lastNavTime.current < throttleMs) {
        e.preventDefault();
        e.stopPropagation();
        return;
      }
      lastNavTime.current = now;

      // Invalidate cache — DOM may have changed (modal, scroll, etc.)
      invalidateCache();

      // ── VideoPlayer open → it handles ALL keys in capture phase ────
      const wOverlay = document.querySelector('.watch-overlay');
      if (wOverlay && (wOverlay.querySelector('video') || wOverlay.querySelector('.watch-header-overlay'))) return;

      const activeEl = document.activeElement;

      const isVideoOverlay = wOverlay && (wOverlay.querySelector('video') || wOverlay.querySelector('.watch-header-overlay'));
      const overlayContainer = isVideoOverlay ? null : wOverlay;
      const activeModal  = overlayContainer || document.querySelector('.details-modal-overlay, .video-overlay');
      const activeInSidebar = activeEl ? activeEl.closest('.sidebar') : false;

      // ── Enter: click focused element ───────────────────────────────
      if (e.key === 'Enter') {
        const focusables = getCachedFocusables(activeModal);
        if (activeEl && focusables.includes(activeEl)) {
          e.preventDefault();
          e.stopPropagation();
          activeEl.click();
        }
        return;
      }

      // ── Backspace / Escape ─────────────────────────────────────────
      if (e.key === 'Backspace' || e.key === 'Escape') {
        if (activeModal) {
          const closeBtn = activeModal.querySelector('.watch-close, .btn-secondary') || activeModal.querySelector('button');
          if (closeBtn) {
            e.preventDefault();
            e.stopPropagation();
            closeBtn.click();
          }
        } else {
          e.preventDefault();
          e.stopPropagation();
          const firstSidebarItem = document.querySelector('.sidebar .focusable');
          if (firstSidebarItem) {
            firstSidebarItem.focus();
            firstSidebarItem.scrollIntoView({ behavior: isTV ? 'auto' : 'smooth', block: 'center' });
          }
        }
        return;
      }

      // ── ArrowUp / ArrowDown ────────────────────────────────────────
      if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
        if (activeModal) {
          e.preventDefault();
          e.stopPropagation();
          navigateSpatially(e.key, activeEl, getCachedFocusables(activeModal));
        } else if (activeInSidebar) {
          e.preventDefault();
          e.stopPropagation();
          const sidebarItems = getCachedSidebar();
          const idx = sidebarItems.indexOf(activeEl);
          if (idx >= 0) {
            const nextIdx = e.key === 'ArrowUp'
              ? Math.max(0, idx - 1)
              : Math.min(sidebarItems.length - 1, idx + 1);
            sidebarItems[nextIdx].focus();
            sidebarItems[nextIdx].scrollIntoView({ behavior: isTV ? 'auto' : 'smooth', block: 'center' });
          } else {
            navigateSpatially(e.key, activeEl, sidebarItems);
          }
        } else if (document.querySelector('.catalog-grid, .sports-grid')) {
          e.preventDefault();
          e.stopPropagation();
          // Inline lightweight spatial for grid rows — skip full DOM scan
          const grid = document.querySelector('.catalog-grid, .sports-grid');
          if (grid) {
            const items = getCachedFocusables(grid);
            navigateSpatially(e.key, activeEl, items);
          }
        }
        return;
      }

      // ── ArrowLeft / ArrowRight ─────────────────────────────────────
      e.preventDefault();
      e.stopPropagation();

      let pool;
      if (activeModal) {
        pool = getCachedFocusables(activeModal);
      } else if (activeInSidebar) {
        if (e.key === 'ArrowRight') {
          const mainContent = document.querySelector('.main-content');
          pool = mainContent ? getCachedFocusables(mainContent) : focusableCache.current;
        } else {
          pool = getCachedSidebar();
        }
      } else {
        const mainContent = document.querySelector('.main-content');
        pool = mainContent ? getCachedFocusables(mainContent) : focusableCache.current;
      }

      if (!activeEl || !pool.includes(activeEl)) {
        if (pool.length > 0) {
          const target = pool[0];
          // Scroll container into view before focusing
          if (isTV) {
            const container = target.closest('.catalog-row-track, .catalog-grid, .main-content');
            if (container) {
              const tRect = target.getBoundingClientRect();
              const cRect = container.getBoundingClientRect();
              if (tRect.top < cRect.top || tRect.bottom > cRect.bottom) {
                target.scrollIntoView({ behavior: 'auto', block: 'nearest' });
              }
            }
          }
          target.focus();
        }
        return;
      }
      navigateSpatially(e.key, activeEl, pool);
    };

    window.addEventListener('keydown', handleKeyDown);

    // Auto-focus first sidebar item on load
    const timer = setTimeout(() => {
      const activeEl = document.activeElement;
      if (!activeEl || activeEl === document.body) {
        const first = document.querySelector('.sidebar .focusable');
        if (first) first.focus();
      }
    }, isTV ? 800 : 500);

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      clearTimeout(timer);
    };
  }, [isActive]);
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function isVisible(el) {
  const rect = el.getBoundingClientRect();
  return rect.width > 0 && rect.height > 0 && !el.disabled;
}

function queryFocusables(restrictTo) {
  const base = restrictTo || document;
  return Array.from(base.querySelectorAll(FOCUSABLE_SEL)).filter(el => {
    return isVisible(el);
  });
}

function navigateSpatially(key, activeEl, focusables) {
  if (!activeEl || !focusables.includes(activeEl)) {
    if (focusables.length > 0) focusables[0].focus();
    return;
  }

  const aRect = activeEl.getBoundingClientRect();
  const aCX = aRect.left + aRect.width / 2;
  const aCY = aRect.top + aRect.height / 2;

  let best = null;
  let bestScore = Infinity;

  for (let i = 0; i < focusables.length; i++) {
    const c = focusables[i];
    if (c === activeEl) continue;

    const cRect = c.getBoundingClientRect();
    const cCX = cRect.left + cRect.width / 2;
    const cCY = cRect.top + cRect.height / 2;
    const dx = cCX - aCX;
    const dy = cCY - aCY;

    let score = Infinity;

    switch (key) {
      case 'ArrowLeft':
        if (cRect.right <= aRect.left + 10 || cCX < aCX) {
          const yOverlap = Math.max(0, Math.min(aRect.bottom, cRect.bottom) - Math.max(aRect.top, cRect.top));
          score = Math.abs(dx) + Math.abs(dy) * 5 - (yOverlap > 0 ? 1000 : 0);
        }
        break;
      case 'ArrowRight':
        if (cRect.left >= aRect.right - 10 || cCX > aCX) {
          const yOverlap = Math.max(0, Math.min(aRect.bottom, cRect.bottom) - Math.max(aRect.top, cRect.top));
          score = Math.abs(dx) + Math.abs(dy) * 5 - (yOverlap > 0 ? 1000 : 0);
        }
        break;
      case 'ArrowUp':
        if (cRect.bottom <= aRect.top + 10 || cCY < aCY) {
          const xOverlap = Math.max(0, Math.min(aRect.right, cRect.right) - Math.max(aRect.left, cRect.left));
          score = Math.abs(dy) + Math.abs(dx) * 5 - (xOverlap > 0 ? 1000 : 0);
        }
        break;
      case 'ArrowDown':
        if (cRect.top >= aRect.bottom - 10 || cCY > aCY) {
          const xOverlap = Math.max(0, Math.min(aRect.right, cRect.right) - Math.max(aRect.left, cRect.left));
          score = Math.abs(dy) + Math.abs(dx) * 5 - (xOverlap > 0 ? 1000 : 0);
        }
        break;
    }

    if (score < bestScore) {
      bestScore = score;
      best = c;
    }
  }

  if (best) {
    best.focus();
    best.scrollIntoView({ behavior: isTV ? 'auto' : 'smooth', block: 'nearest', inline: 'nearest' });
  }
}
