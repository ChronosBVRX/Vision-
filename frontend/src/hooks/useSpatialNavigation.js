import { useEffect } from 'react';

/**
 * Spatial navigation hook for Smart TV remote controls.
 *
 * Design rules:
 * 1. ArrowUp/Down: handled EXCLUSIVELY by each page's own row-navigation effect
 *    (the catalog-page components). This hook SKIPS ArrowUp/Down entirely when
 *    the focused element is inside `.catalog-page` (main content zone).
 * 2. ArrowLeft from main content → sidebar (first item).
 * 3. ArrowRight/Up/Down never enter the sidebar.
 * 4. Backspace / Escape → return focus to sidebar if no modal is open.
 * 5. Modal open → focus trapped inside modal only.
 */
export default function useSpatialNavigation(isActive = true) {
  useEffect(() => {
    if (!isActive) return;

    const handleKeyDown = (e) => {
      const keys = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', 'Escape', 'Backspace'];
      if (!keys.includes(e.key)) return;

      // ── VideoPlayer open → it handles ALL keys in capture phase ────
      // Only skip if a real VideoPlayer is active (has <video> or watch-header-overlay),
      // not for resolver error/loading overlays that need keyboard navigation.
      const wOverlay = document.querySelector('.watch-overlay');
      if (wOverlay && (wOverlay.querySelector('video') || wOverlay.querySelector('.watch-header-overlay'))) return;

      const activeEl = document.activeElement;

      // ── Detect context ─────────────────────────────────────────────────
      // Treat any .watch-overlay without a video player as an active overlay
      const isVideoOverlay = wOverlay && (wOverlay.querySelector('video') || wOverlay.querySelector('.watch-header-overlay'));
      const overlayContainer = isVideoOverlay ? null : wOverlay;
      const activeModal  = overlayContainer || document.querySelector('.details-modal-overlay, .video-overlay');
      const activeInMain = activeEl ? !!activeEl.closest('.catalog-page, .main-content') : false;
      const activeInSidebar = activeEl ? !!activeEl.closest('.sidebar') : false;

      // ── Enter: click focused element ───────────────────────────────────
      if (e.key === 'Enter') {
        const focusables = getFocusables(activeModal);
        if (activeEl && focusables.includes(activeEl)) {
          e.preventDefault();
          activeEl.click();
        }
        return;
      }

      // ── Backspace / Escape: close overlay/modal or return to sidebar ──
      if (e.key === 'Backspace' || e.key === 'Escape') {
        if (activeModal) {
          // Try to close the overlay by clicking its close button
          const closeBtn = activeModal.querySelector('.watch-close, .btn-secondary') || activeModal.querySelector('button');
          if (closeBtn) {
            e.preventDefault();
            closeBtn.click();
          }
        } else {
          e.preventDefault();
          const firstSidebarItem = document.querySelector('.sidebar .focusable');
          if (firstSidebarItem) {
            firstSidebarItem.focus();
            firstSidebarItem.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }
        return;
      }

      // ── ArrowUp / ArrowDown ────────────────────────────────────────────
      // When inside main catalog content (not modal, not sidebar) these keys
      // are handled by each page's own row-navigation useEffect.
      // We only handle them here if we are in the sidebar, a modal, or if there is a grid active.
      if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
        if (activeModal) {
          e.preventDefault();
          navigateSpatially(e.key, activeEl, getFocusables(activeModal));
        } else if (activeInSidebar) {
          e.preventDefault();
          const sidebarItems = getSidebarFocusables();
          const idx = sidebarItems.indexOf(activeEl);
          if (idx >= 0) {
            const nextIdx = e.key === 'ArrowUp' ? Math.max(0, idx - 1) : Math.min(sidebarItems.length - 1, idx + 1);
            sidebarItems[nextIdx].focus();
            sidebarItems[nextIdx].scrollIntoView({ behavior: 'smooth', block: 'center' });
          } else {
            navigateSpatially(e.key, activeEl, sidebarItems);
          }
        } else if (document.querySelector('.catalog-grid, .sports-grid')) {
          // If we are in grid view (search / category filter active), handle ArrowUp/Down spatially!
          e.preventDefault();
          navigateSpatially(e.key, activeEl, getFocusables(null));
        }
        // If in main content: do nothing — let the page handle it
        return;
      }

      // ── ArrowLeft / ArrowRight ─────────────────────────────────────────
      e.preventDefault();

      // ArrowRight / ArrowLeft inside sidebar or modal → spatial
      let pool;
      if (activeModal) {
        pool = getFocusables(activeModal);
      } else if (activeInSidebar) {
        if (e.key === 'ArrowRight') {
          // Jump to main content
          const mainContent = document.querySelector('.main-content');
          pool = mainContent ? getFocusables(mainContent) : getFocusables(null);
        } else {
          pool = getSidebarFocusables();
        }
      } else {
        // Restrict navigation to main content focusables when focus is in the main content.
        // This prevents spatial navigation (like ArrowLeft) from jumping to the sidebar.
        const mainContent = document.querySelector('.main-content');
        pool = mainContent ? getFocusables(mainContent) : getFocusables(null);
      }

      if (!activeEl || !pool.includes(activeEl)) {
        if (pool.length > 0) pool[0].focus();
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
    }, 500);

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

function getFocusables(restrictTo) {
  return Array.from(document.querySelectorAll('.focusable')).filter(el => {
    if (restrictTo && !restrictTo.contains(el)) return false;
    return isVisible(el);
  });
}

function getSidebarFocusables() {
  return Array.from(document.querySelectorAll('.sidebar .focusable')).filter(isVisible);
}

function navigateSpatially(key, activeEl, focusables) {
  if (!activeEl || !focusables.includes(activeEl)) {
    if (focusables.length > 0) focusables[0].focus();
    return;
  }

  const aRect = activeEl.getBoundingClientRect();
  const aX = aRect.left + aRect.width / 2;
  const aY = aRect.top + aRect.height / 2;

  let best = null;
  let bestScore = Infinity;
  const PENALTY = 5.0;

  focusables.forEach(c => {
    if (c === activeEl) return;
    const cRect = c.getBoundingClientRect();
    const cX = cRect.left + cRect.width / 2;
    const cY = cRect.top + cRect.height / 2;
    const dx = cX - aX;
    const dy = cY - aY;

    const xOverlap = Math.max(0, Math.min(aRect.right, cRect.right) - Math.max(aRect.left, cRect.left));
    const yOverlap = Math.max(0, Math.min(aRect.bottom, cRect.bottom) - Math.max(aRect.top, cRect.top));

    let score = Infinity;
    switch (key) {
      case 'ArrowLeft':
        if (cRect.right <= aRect.left + 10 || cX < aX) {
          score = Math.abs(dx) + Math.abs(dy) * PENALTY - (yOverlap > 0 ? 1000 : 0);
        }
        break;
      case 'ArrowRight':
        if (cRect.left >= aRect.right - 10 || cX > aX) {
          score = Math.abs(dx) + Math.abs(dy) * PENALTY - (yOverlap > 0 ? 1000 : 0);
        }
        break;
      case 'ArrowUp':
        if (cRect.bottom <= aRect.top + 10 || cY < aY) {
          score = Math.abs(dy) + Math.abs(dx) * PENALTY - (xOverlap > 0 ? 1000 : 0);
        }
        break;
      case 'ArrowDown':
        if (cRect.top >= aRect.bottom - 10 || cY > aY) {
          score = Math.abs(dy) + Math.abs(dx) * PENALTY - (xOverlap > 0 ? 1000 : 0);
        }
        break;
    }

    if (score < bestScore) {
      bestScore = score;
      best = c;
    }
  });

  if (best) {
    best.focus();
    best.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
  }
}
