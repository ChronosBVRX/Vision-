import { useEffect, useRef } from 'react';

const NAV_KEYS = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', 'Escape', 'Backspace', 'BrowserBack', 'GoBack']);
const FOCUSABLE_SEL = '.focusable';
const LOCAL_NAV_OVERLAY_SEL = '.ep-selector-overlay';
const MODAL_NAV_SEL = '.details-modal-overlay, .video-overlay, .wo-modal-overlay';

function isSmartTV() {
  return typeof window !== 'undefined' && window.isSmartTV === true;
}

/**
 * Spatial navigation hook for Smart TV remote controls.
 *
 * Performance optimizations for Smart TV:
 *  - VideoPlayer check BEFORE throttle (prevents conflicting double-processing)
 *  - Throttle: max 1 navigation cycle per 100ms (TV) / 40ms (browser)
 *  - MutationObserver-based cache invalidation (only on real DOM changes)
 *  - Batch getBoundingClientRect: ALL rects calculated before the scoring loop
 *  - No smooth scroll on TV (instant scroll)
 *  - Passive listeners for mouse events
 */
export default function useSpatialNavigation(isActive = true) {
  const focusableCache = useRef([]);
  const sidebarCache   = useRef([]);
  const cacheValid     = useRef(false);
  const lastNavTime    = useRef(0);
  const throttleMs     = isSmartTV() ? 100 : 40;
  const mutationObserver = useRef(null);

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

    // ── MutationObserver: invalidate cache only on real DOM changes ──
    // This is much more efficient than invalidating on every keydown.
    const invalidateCache = () => {
      cacheValid.current = false;
      focusableCache.current = [];
      sidebarCache.current = [];
    };

    mutationObserver.current = new MutationObserver(() => {
      invalidateCache();
    });
    mutationObserver.current.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class', 'style', 'hidden', 'disabled'],
    });

    const getCachedFocusables = (restrictTo) => {
      if (!cacheValid.current || !focusableCache.current.length) {
        focusableCache.current = queryFocusables(null);
        cacheValid.current = true;
      }
      if (!restrictTo) return focusableCache.current;
      // filter — avoid full DOM re-query
      return focusableCache.current.filter(el => restrictTo.contains(el));
    };

    const getCachedSidebar = () => {
      if (!cacheValid.current || !sidebarCache.current.length) {
        sidebarCache.current = queryFocusables(document.querySelector('.sidebar'));
      }
      return sidebarCache.current;
    };

    const handleKeyDown = (e) => {
      if (!NAV_KEYS.has(e.key)) return;

      // ── CRITICAL FIX: Check if VideoPlayer is open BEFORE throttle ──
      // If VideoPlayer is active, it handles ALL keys in capture phase.
      // We must bail out immediately, BEFORE applying our throttle,
      // so both systems don't compete and the throttle doesn't eat the event.
      const wOverlay = document.querySelector('.watch-overlay');
      if (wOverlay) {
        return; // VideoPlayer takes full control
      }

      // Episode selector owns its grid math locally (season tabs + real columns).
      // The global navigator must stay out so a single D-pad press never moves
      // both the overlay and the catalog behind it.
      if (document.querySelector(LOCAL_NAV_OVERLAY_SEL)) {
        return;
      }

      // ── Throttle: skip if too soon since last navigation ──────────
      const now = performance.now();
      if (now - lastNavTime.current < throttleMs) {
        e.preventDefault();
        e.stopPropagation();
        return;
      }
      lastNavTime.current = now;

      const activeEl = document.activeElement;

      const activeModal = document.querySelector(MODAL_NAV_SEL);
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

      // ── Backspace / Escape / BrowserBack ─────────────────────────────────────────
      if (e.key === 'Backspace' || e.key === 'Escape' || e.key === 'BrowserBack' || e.key === 'GoBack') {
        if (activeModal) {
          const closeBtn = activeModal.querySelector('.watch-close, .wo-close, .btn-secondary') || activeModal.querySelector('button');
          if (closeBtn) {
            e.preventDefault();
            e.stopPropagation();
            closeBtn.click();
          }
        } else {
          e.preventDefault();
          e.stopPropagation();
          const activeSidebarItem = document.querySelector('.sidebar .active.focusable') || document.querySelector('.sidebar .focusable');
          if (activeSidebarItem) {
            activeSidebarItem.focus();
            activeSidebarItem.scrollIntoView({ behavior: isSmartTV() ? 'auto' : 'smooth', block: 'center' });
            window.dispatchEvent(new CustomEvent('sidebar-open'));
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
            sidebarItems[nextIdx].scrollIntoView({ behavior: isSmartTV() ? 'auto' : 'smooth', block: 'center' });
          } else {
            navigateSpatially(e.key, activeEl, sidebarItems);
          }
        } else {
          e.preventDefault();
          e.stopPropagation();
          const mainContent = document.querySelector('.main-content');
          const pool = mainContent ? getCachedFocusables(mainContent) : focusableCache.current;
          if (pool && pool.length > 0) {
            if (!activeEl || !pool.includes(activeEl)) {
              pool[0].focus();
              pool[0].scrollIntoView({ behavior: 'auto', block: 'nearest' });
            } else {
              navigateSpatially(e.key, activeEl, pool);
            }
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
        if (e.key === 'ArrowLeft') {
          pool = getCachedFocusables(null); // allow jumping to sidebar
        } else {
          const mainContent = document.querySelector('.main-content');
          pool = mainContent ? getCachedFocusables(mainContent) : getCachedFocusables(null);
        }
      }

      if (!activeEl || !pool.includes(activeEl)) {
        if (pool.length > 0) {
          const target = pool[0];
          target.focus();
          if (isSmartTV()) {
            target.scrollIntoView({ behavior: 'auto', block: 'nearest' });
          }
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
    }, isSmartTV() ? 800 : 500);

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      clearTimeout(timer);
      if (mutationObserver.current) {
        mutationObserver.current.disconnect();
        mutationObserver.current = null;
      }
    };
  }, [isActive]);
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function isVisible(el) {
  const style = window.getComputedStyle(el);
  return el.offsetWidth > 0
    && el.offsetHeight > 0
    && !el.disabled
    && style.display !== 'none'
    && style.visibility !== 'hidden'
    && !el.closest('[hidden], [inert], [aria-hidden="true"]');
}

function queryFocusables(restrictTo) {
  const base = restrictTo || document;
  return Array.from(base.querySelectorAll(FOCUSABLE_SEL)).filter(el => {
    return isVisible(el);
  });
}

/**
 * navigateSpatially — PERFORMANCE OPTIMIZED VERSION
 *
 * Key optimization: All getBoundingClientRect() calls are batched BEFORE
 * the scoring loop. Reading layout properties inside a loop causes the
 * browser to reflow the entire page on each read (layout thrashing).
 * By pre-reading all rects into an array first, we trigger ONE reflow
 * and then do pure math in the loop — much faster on low-end TV hardware.
 */
function navigateSpatially(key, activeEl, focusables) {
  if (!activeEl || !focusables.includes(activeEl)) {
    if (focusables.length > 0) focusables[0].focus();
    return;
  }

  // ── PRE-BATCH all rects in ONE layout read (avoids layout thrashing) ──
  const rects = focusables.map(el => el.getBoundingClientRect());
  const aIdx = focusables.indexOf(activeEl);
  const aRect = rects[aIdx];
  const aCX = aRect.left + aRect.width / 2;
  const aCY = aRect.top + aRect.height / 2;

  let best = null;
  let bestScore = Infinity;

  for (let i = 0; i < focusables.length; i++) {
    if (i === aIdx) continue;

    const cRect = rects[i]; // Read from pre-batched array — no reflow!
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
      best = focusables[i];
    }
  }

  if (best) {
    best.focus();
    best.scrollIntoView({ behavior: isSmartTV() ? 'auto' : 'smooth', block: 'nearest', inline: 'nearest' });
  }
}
