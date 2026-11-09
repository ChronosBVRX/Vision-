const FOCUSABLE = [
  "button:not([disabled])",
  "a[href]",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "[role='button']:not([aria-disabled='true'])",
  "[tabindex]:not([tabindex='-1'])"
].join(",");

const FOCUS_STYLE_ID = "opl-dpad-focus-style";

let installed = false;
let current: HTMLElement | null = null;
let currentKey: string | null = null;

function visible(element: Element): element is HTMLElement {
  if (!(element instanceof HTMLElement)) return false;
  if (element.hidden || element.closest("[hidden]")) return false;
  if (element.getAttribute("aria-hidden") === "true") return false;
  if (element.closest("[inert]")) return false;
  const style = getComputedStyle(element);
  if (style.display === "none" || style.visibility === "hidden") return false;
  const rect = element.getBoundingClientRect();
  return rect.width > 1 && rect.height > 1;
}

function keyOf(element: HTMLElement | null): string | null {
  const key = element?.dataset.dpadKey?.trim();
  return key ? key : null;
}

function focusables(scope: ParentNode = document): HTMLElement[] {
  return Array.from(scope.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(visible);
}

function mark(element: HTMLElement | null): void {
  if (current && current !== element) current.removeAttribute("data-dpad-focus");
  current = element;
  currentKey = keyOf(element) ?? currentKey;
  if (element) element.setAttribute("data-dpad-focus", "");
}

function scrollWithinRoot(element: HTMLElement): void {
  const root = element.closest<HTMLElement>("[data-dpad-scroll-root]");
  if (!root) {
    element.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "auto" });
    return;
  }

  const target = element.getBoundingClientRect();
  const viewport = root.getBoundingClientRect();
  const pad = 18;

  if (target.top < viewport.top + pad) {
    root.scrollTop = Math.max(0, root.scrollTop - (viewport.top + pad - target.top));
  } else if (target.bottom > viewport.bottom - pad) {
    root.scrollTop = Math.min(
      root.scrollHeight - root.clientHeight,
      root.scrollTop + (target.bottom - (viewport.bottom - pad))
    );
  }
}

function resetMainScrollForRail(element: HTMLElement): void {
  const main = document.querySelector<HTMLElement>('[data-dpad-scroll-root="main"]');
  if (main && !main.contains(element) && main.scrollTop !== 0) {
    // Returning from the rail (QR, roster, start) should show the whole setup
    // again, not only the last focused button.
    main.scrollTop = 0;
  }
}

function focusElement(element: HTMLElement | null): void {
  if (!element) return;
  resetMainScrollForRail(element);
  element.focus({ preventScroll: true });
  mark(element);
  if (!element.closest(".opl-tv-lobby")) {
    scrollWithinRoot(element);
  }

  // The first focusable inside a scroll panel is the "top" of that panel.
  // Returning to it must also bring back the heading and description above
  // it, not only enough pixels to show the button.
  const root = element.closest<HTMLElement>("[data-dpad-scroll-root]");
  if (root && focusables(root)[0] === element) {
    root.scrollTop = 0;
  }
}

function center(element: HTMLElement): { x: number; y: number } {
  const rect = element.getBoundingClientRect();
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
}

function zoneOf(element: HTMLElement): string | null {
  return element.closest<HTMLElement>("[data-dpad-zone]")?.dataset.dpadZone ?? null;
}

function move(direction: "up" | "down" | "left" | "right"): void {
  const list = focusables();
  if (!list.length) return;

  const active = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  if (!active || active === document.body || !visible(active)) {
    const preferred = list.find((element) => element.hasAttribute("data-dpad-autofocus"));
    focusElement(preferred ?? list[0] ?? null);
    return;
  }

  const from = center(active);
  const fromRect = active.getBoundingClientRect();
  const vertical = direction === "up" || direction === "down";
  const zone = zoneOf(active);
  const targetZone =
    direction === "left"
      ? zone === "right"
        ? "center"
        : zone === "center"
          ? "left"
          : null
      : direction === "right"
        ? zone === "left"
          ? "center"
          : zone === "center"
            ? "right"
            : null
        : null;
  const crossZone = targetZone
    ? list.filter((element) => zoneOf(element) === targetZone && !element.hasAttribute("data-dpad-cross-skip"))
    : [];
  const sameZone = zone ? list.filter((element) => zoneOf(element) === zone) : list;

  const scorePool = (pool: HTMLElement[], crossing: boolean) =>
    pool
      .filter((element) => element !== active)
      .map((element) => {
        const point = center(element);
        const rect = element.getBoundingClientRect();
        const dx = point.x - from.x;
        const dy = point.y - from.y;
        const primary = direction === "left" ? -dx : direction === "right" ? dx : direction === "up" ? -dy : dy;
        const secondary = vertical ? Math.abs(dx) : Math.abs(dy);
        const overlap = vertical
          ? Math.max(0, Math.min(fromRect.right, rect.right) - Math.max(fromRect.left, rect.left))
          : Math.max(0, Math.min(fromRect.bottom, rect.bottom) - Math.max(fromRect.top, rect.top));
        // Aligned neighbours win: a vertical rail should not jump sideways just
        // because a diagonal target is a few pixels closer.
        // When crossing between the three TV columns, horizontal distance is a
        // given; vertical alignment decides which control in the next column is
        // the natural landing point.
        const score = crossing
          ? Math.abs(dy) + Math.abs(dx) * 0.06
          : primary + secondary * (overlap > 0 ? 0.7 : 2.2);
        return { element, primary, score, overlap };
      })
      .filter(({ primary }) => primary > 8);

  // Stay inside the current column while it has a neighbour in that direction.
  // Only at the edge do we hand the D-pad to the next column. Horizontal moves
  // require vertical overlap so "right" from the start button does not jump to
  // a kick button floating above it.
  let candidates = scorePool(sameZone, false);
  if (!vertical) candidates = candidates.filter((candidate) => candidate.overlap > 0);
  if (!candidates.length && crossZone.length) {
    candidates = scorePool(crossZone, true);
  }
  if (!candidates.length) {
    candidates = scorePool(list, false);
    if (!vertical) candidates = candidates.filter((candidate) => candidate.overlap > 0);
  }

  candidates.sort((a, b) => a.score - b.score);
  if (!candidates.length) {
    // At the edge of a scrollable panel, use the key to reveal the rest of
    // the text instead of doing nothing.
    const root = active.closest<HTMLElement>("[data-dpad-scroll-root]");
    if (root) {
      if (direction === "up") root.scrollTop = 0;
      else if (direction === "down") root.scrollTop = root.scrollHeight;
    }
    return;
  }
  focusElement(candidates[0]?.element ?? null);
}

function activate(): void {
  const active = document.activeElement;
  if (active instanceof HTMLElement && (active.tagName === "BUTTON" || active.tagName === "A" || active.getAttribute("role") === "button")) {
    active.click();
  }
}

function onKeyDown(event: KeyboardEvent): void {
  const active = document.activeElement;
  const editing = active instanceof HTMLInputElement || active instanceof HTMLTextAreaElement || active instanceof HTMLSelectElement;
  if (editing) return;

  if (event.key === "ArrowUp" || event.key === "ArrowDown" || event.key === "ArrowLeft" || event.key === "ArrowRight") {
    event.preventDefault();
    event.stopPropagation();
    move(event.key === "ArrowUp" ? "up" : event.key === "ArrowDown" ? "down" : event.key === "ArrowLeft" ? "left" : "right");
    return;
  }

  if (event.key === "Enter" || event.code === "NumpadEnter") {
    if (active instanceof HTMLElement && visible(active) && active !== document.body) {
      event.preventDefault();
      activate();
    }
  }
}

function installFocusStyle(): void {
  if (document.getElementById(FOCUS_STYLE_ID)) return;
  const style = document.createElement("style");
  style.id = FOCUS_STYLE_ID;
  style.textContent = `
    :where([data-dpad-focus]) {
      outline: 3px solid var(--accent-strong, #6BA6FF) !important;
      outline-offset: 3px !important;
      scroll-margin: 24px;
    }
  `;
  document.head.appendChild(style);
}

/** Focus a specific D-pad target. Returns false when it is not visible. */
export function focusDpad(selector: string): boolean {
  const element = Array.from(document.querySelectorAll<HTMLElement>(selector)).find(
    (candidate) => visible(candidate) && candidate.matches(FOCUSABLE)
  );
  if (!element) return false;
  focusElement(element);
  return true;
}

/**
 * After a DOM patch, the focused node may have been replaced. Remember the
 * stable `data-dpad-key` and put focus back where the player left it.
 */
export function restoreDpadFocus(scope: ParentNode = document): boolean {
  const active = document.activeElement;
  if (active instanceof HTMLElement && active !== document.body && visible(active)) return false;

  if (currentKey) {
    const target = Array.from(
      scope.querySelectorAll<HTMLElement>(`[data-dpad-key="${CSS.escape(currentKey)}"]`)
    ).find((candidate) => visible(candidate) && candidate.matches(FOCUSABLE));
    if (target) {
      focusElement(target);
      return true;
    }
    currentKey = null;
  }

  const fallback = Array.from(scope.querySelectorAll<HTMLElement>("[data-dpad-autofocus]")).find(
    (candidate) => visible(candidate) && candidate.matches(FOCUSABLE)
  );
  if (fallback) {
    focusElement(fallback);
    return true;
  }
  return false;
}

export function installDpadNavigation(): void {
  if (installed) return;
  installed = true;
  installFocusStyle();
  document.addEventListener("keydown", onKeyDown, true);
  document.addEventListener("focusin", (event) => {
    if (event.target instanceof HTMLElement) {
      if (current && current !== event.target) current.removeAttribute("data-dpad-focus");
      current = event.target;
      currentKey = keyOf(event.target);
      event.target.setAttribute("data-dpad-focus", "");
    }
  });
}
