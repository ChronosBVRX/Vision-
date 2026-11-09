/**
 * The shell's stylesheet.
 *
 * Lobby and catalog live in the DOM rather than on the Phaser canvas. That is
 * not a cosmetic preference: the canvas is a fixed-size bitmap the browser
 * rescales to the screen, so everything drawn into it — text above all — arrives
 * softened. The same markup rendered by the browser is resolution-independent
 * and stays crisp at any display density.
 *
 * Colours come exclusively from the theme's custom properties, so a theme
 * switch is a variable update on the root element and never a re-render.
 */
const STYLE_ELEMENT_ID = "opl-shell-styles";

const css = `
.opl-rules-preview { padding: 12px 16px; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); line-height: 1.5; }
.opl-rules-preview summary { cursor: pointer; font-weight: 700; padding: 6px 0; }
.opl-rules-preview h3 { margin: 16px 0 6px; }
.opl-rules-preview ul { margin: 0; padding-left: 22px; }
.opl-shell {
  position: fixed;
  inset: 0;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  font-family: var(--font-body);
  color: var(--ink);
  background:
    radial-gradient(120% 90% at 12% -10%, color-mix(in srgb, var(--accent) 9%, transparent), transparent 60%),
    radial-gradient(100% 80% at 100% 110%, color-mix(in srgb, var(--amber) 12%, transparent), transparent 62%),
    var(--paper);
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
  overflow: hidden;
  z-index: 30;
}

.opl-shell[hidden] { display: none !important; }

.opl-shell *,
.opl-shell *::before,
.opl-shell *::after { box-sizing: border-box; }

/*
 * The button reset is wrapped in :where() so it carries no specificity of its
 * own. Written as a plain descendant selector it outranks every single-class
 * component rule below and silently strips their background, border and shadow
 * — which is exactly what it did until a screenshot showed the tiles had no
 * card edges at all.
 */
.opl-shell :where(button) {
  font: inherit;
  color: inherit;
  cursor: pointer;
  border: none;
  background: none;
  touch-action: manipulation;
}

.opl-shell :focus-visible {
  outline: 3px solid var(--accent-strong);
  outline-offset: 2px;
  box-shadow: none;
}

/* Embedded app mode: no browser dock and Vision+ dark palette, so the screen feels like the app. */
body[data-platform="android"] .opl-shell {
  --paper: #070B13;
  --paper-deep: #0B1424;
  --surface: #0F1626;
  --surface-raised: #131D33;
  --surface-muted: #1B2538;
  --line: #26334A;
  --line-strong: #334155;
  --ink: #F7FAFF;
  --ink-soft: #A9B4C6;
  --muted: #7B8794;
  --accent: #2D7DF6;
  --accent-strong: #6BA6FF;
  --accent-soft: #1E3A8A;
  --amber: #F5C518;
  --danger: #FF6B6B;
  --danger-soft: #3B1F24;
  --shadow-card: 0 18px 42px rgba(0, 0, 0, 0.42);
  --shadow-panel: 0 24px 60px rgba(0, 0, 0, 0.48);
  background:
    radial-gradient(120% 90% at 12% -10%, color-mix(in srgb, var(--accent) 12%, transparent), transparent 60%),
    radial-gradient(100% 80% at 100% 110%, color-mix(in srgb, var(--amber) 10%, transparent), transparent 62%),
    var(--paper);
}
body[data-platform="android"] .opl-dock { display: none; }
body[data-platform="android"] .opl-shell { grid-template-columns: minmax(0, 1fr) 316px; }
body[data-platform="android"] .opl-main { padding: 24px 28px 22px; }
body[data-platform="android"] .opl-title { margin-bottom: 14px; }
body[data-platform="android"] .opl-rail { width: 100%; padding: 18px 16px; }
body[data-platform="android"] .opl-open-top { align-items: center; }
body[data-platform="android"] .opl-open-card { border-radius: 20px; overflow: hidden; }
body[data-platform="android"] .opl-open-cover { width: 184px; height: 245px; border-radius: 16px; }
body[data-platform="android"] .opl-open-grid { gap: 22px; }
body[data-platform="android"] .opl-room-code { font-size: 48px; letter-spacing: 8px; }
body[data-platform="android"] .opl-qr { width: 164px; height: 164px; }
body[data-platform="android"] .opl-option, body[data-platform="android"] .opl-toggle,
body[data-platform="android"] .opl-back, body[data-platform="android"] .opl-start,
body[data-platform="android"] .opl-stepper button { min-height: 52px; font-size: 15px; touch-action: manipulation; }
body[data-platform="android"] .opl-howto { max-height: 440px; }

/* A game room belongs to one game. Hide the catalog affordances and let the
   selected lobby own the screen. */
.opl-shell.is-game-locked .opl-dock-button[data-dock="catalog"],
.opl-dock-button[hidden],
.opl-catalog[hidden] { display: none !important; }
.opl-shell.is-game-locked .opl-back { display: none !important; }
.opl-open-meta { display: flex; flex-wrap: wrap; gap: 7px; margin: 11px 0 0; }
.opl-open-meta span { padding: 5px 10px; border: 1px solid var(--line); border-radius: 999px; background: var(--surface-muted); color: var(--ink-soft); font-size: 11px; font-weight: 700; letter-spacing: .4px; }

/* ---------- left dock ---------- */

.opl-dock {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 76px;
  padding: 20px 0;
  background: var(--surface);
  border-right: 1px solid var(--line);
}

.opl-brand {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  background: var(--accent);
  color: var(--on-accent);
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.5px;
}

.opl-dock-rule {
  width: 26px;
  height: 1px;
  background: var(--line);
  margin: 4px 0;
}

.opl-dock-spacer { flex: 1; }

.opl-dock-button {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  color: var(--muted);
  transition: background 150ms ease, color 150ms ease, transform 150ms ease;
}

.opl-dock-button:hover {
  background: var(--surface-muted);
  color: var(--ink);
  transform: translateY(-1px);
}

.opl-dock-button[aria-pressed="true"] {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

/* ---------- centre column ---------- */

.opl-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 26px 30px 24px;
  overflow: hidden;
}

.opl-kicker {
  margin: 0;
  font-size: 11px;
  letter-spacing: 2.4px;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--muted) 76%, var(--amber));
}

.opl-title {
  margin: 2px 0 18px;
  font-family: var(--font-display);
  font-size: clamp(24px, 2.4vw, 38px);
  font-weight: 400;
  line-height: 1.15;
}

/* The scroll container. The settings card and the shelf are siblings inside it
   so neither can constrain the other's height. */
.opl-board {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  overflow-y: auto;
  padding: 2px 4px 8px 0;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}

.opl-board::-webkit-scrollbar { width: 8px; }
.opl-board::-webkit-scrollbar-thumb {
  background: var(--line-strong);
  border-radius: 99px;
}

.opl-catalog {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 14px;
  align-content: start;
}

/* ---------- game tile ---------- */

.opl-tile {
  padding: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  text-align: left;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: var(--shadow-card);
  transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease;
}

.opl-tile:hover:not(.is-open) {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--tile-accent, var(--accent)) 55%, var(--line));
  box-shadow: var(--shadow-panel);
}

.opl-tile-art {
  border-radius: 15px 15px 0 0;
  overflow: hidden;
  width: 100%;
  flex: none;
  display: grid;
  place-items: center;
  height: 128px;
  background: var(--tile-tint, var(--accent-soft));
  color: var(--tile-accent, var(--accent-strong));
  border-bottom: 1px solid color-mix(in srgb, var(--tile-accent, var(--accent)) 20%, transparent);
}

.opl-glyph-img { display: block; }
.opl-tile-cover { width: 100%; height: 100%; object-fit: cover; display: block; }

.opl-tile-body {
  display: block;
  padding: 10px 12px 12px;
}

.opl-tile-eyebrow {
  display: block;
  margin: 0;
  font-size: 10px;
  letter-spacing: 1.6px;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--tile-accent, var(--muted)) 45%, var(--ink));
}

.opl-tile-name {
  display: block;
  margin: 2px 0 0;
  font-size: 15px;
  font-weight: 500;
  line-height: 1.25;
  /* Two lines' worth, so a wrapping title does not make the row ragged. */
  min-height: 2.5em;
}

.opl-tile-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--muted);
}

.opl-tile-meta svg { flex: none; }

/* ---------- the setup card ---------- */

/*
 * The settings card for the selected game.
 *
 * It borrows the tile's look but not its structure: no art band — a full-width
 * strip of colour above a form is a lot of empty colour — so the glyph shrinks
 * to a badge beside the title and the space goes to the controls.
 */
.opl-open-card {
  flex: none;
  background: var(--surface);
  border: 1px solid var(--tile-accent, var(--accent));
  border-radius: 16px;
  box-shadow: var(--shadow-panel);
}

.opl-setup {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.opl-open-top {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.opl-open-cover {
  flex: none;
  width: 148px;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  border-radius: 14px;
  border: 1px solid var(--line);
  box-shadow: var(--shadow-card);
}

.opl-open-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
  gap: 18px;
  align-items: start;
}

.opl-setup-fields {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.opl-howto {
  min-width: 0;
  max-height: 390px;
  overflow: auto;
  padding: 15px 17px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: color-mix(in srgb, var(--surface-muted) 58%, transparent);
}

.opl-howto-title {
  margin: 0 0 10px;
  font-size: 11px;
  letter-spacing: 1.7px;
  text-transform: uppercase;
  color: var(--tile-accent, var(--accent-strong));
  font-weight: 800;
}

.opl-howto section + section { margin-top: 14px; }
.opl-howto h3 { margin: 0 0 5px; font-size: 14px; line-height: 1.25; }
.opl-howto ul { margin: 0; padding-left: 18px; }
.opl-howto li { margin: 4px 0; font-size: 12px; line-height: 1.45; color: var(--ink-soft); }
.opl-howto p { margin: 0; font-size: 12px; line-height: 1.5; color: var(--ink-soft); }

.opl-setup-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.opl-setup-badge {
  display: grid;
  place-items: center;
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: 15px;
  background: var(--tile-tint, var(--accent-soft));
  color: var(--tile-accent, var(--accent-strong));
}

.opl-setup-title { flex: 1; min-width: 0; }

.opl-setup-desc {
  margin: 6px 0 0;
  max-width: 62ch;
  font-size: 13px;
  line-height: 1.55;
  color: var(--ink-soft);
}

.opl-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
  padding: 7px 13px 7px 9px;
  font-size: 13px;
  color: var(--ink-soft);
  background: var(--surface-muted);
  border: 1px solid var(--line);
  border-radius: 99px;
  transition: background 140ms ease, color 140ms ease;
}

.opl-back:hover {
  background: var(--paper-deep);
  color: var(--ink);
}

/* justify-items keeps the stepper at its natural width instead of letting the
   grid stretch it across the whole card. */
.opl-field {
  display: grid;
  gap: 7px;
  justify-items: start;
}

.opl-field-label {
  margin: 0;
  font-size: 11px;
  letter-spacing: 1.4px;
  text-transform: uppercase;
  color: var(--muted);
}

.opl-field-hint {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.opl-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.opl-option {
  padding: 9px 15px;
  font-size: 13px;
  background: var(--surface-muted);
  border: 1px solid var(--line);
  border-radius: 11px;
  transition: background 140ms ease, border-color 140ms ease, color 140ms ease;
}

.opl-option:hover:not(:disabled) {
  border-color: var(--line-strong);
  background: var(--paper-deep);
}

.opl-option[aria-pressed="true"] {
  background: var(--tile-accent, var(--accent));
  border-color: var(--tile-accent, var(--accent));
  color: var(--on-accent);
}

.opl-option:disabled { opacity: 0.45; cursor: default; }

/* Verwandte Schalter stehen zusammen und nebeneinander. Zehn Hausregeln
   untereinander wären eine Wand; in zwei, drei Spalten sind sie eine Liste. */
.opl-group {
  display: grid;
  gap: 8px;
  padding: 11px 13px;
  border: 1px solid var(--line);
  border-radius: 13px;
  background: color-mix(in srgb, var(--surface-muted) 55%, transparent);
}

.opl-group-title {
  margin: 0;
  font-size: 11px;
  letter-spacing: 1.4px;
  text-transform: uppercase;
  color: var(--muted);
}

.opl-group-body {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 6px 12px;
}

.opl-toggle {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 10px;
  font-size: 13px;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  transition: background 140ms ease, border-color 140ms ease;
}

.opl-toggle:hover:not(:disabled) {
  border-color: var(--line);
  background: var(--paper-deep);
}

.opl-toggle-box {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  background: var(--surface);
  font-size: 12px;
  line-height: 1;
  color: transparent;
}

.opl-toggle[aria-pressed="true"] .opl-toggle-box {
  background: var(--tile-accent, var(--accent));
  border-color: var(--tile-accent, var(--accent));
  color: var(--on-accent);
}

.opl-toggle-text { min-width: 0; }
.opl-toggle-hint {
  display: block;
  font-size: 11px;
  color: var(--muted);
}

.opl-toggle:disabled { opacity: 0.45; cursor: default; }

.opl-stepper {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  background: var(--surface-muted);
  border: 1px solid var(--line);
  border-radius: 11px;
}

.opl-stepper button {
  width: 34px;
  height: 32px;
  font-size: 17px;
  line-height: 1;
  border-radius: 8px;
  color: var(--ink-soft);
  transition: background 140ms ease;
}

.opl-stepper button:hover:not(:disabled) { background: var(--paper-deep); }
.opl-stepper button:disabled { opacity: 0.4; cursor: default; }

.opl-stepper output {
  min-width: 52px;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 15px;
}

/* ---------- right rail ---------- */

.opl-rail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 268px;
  padding: 20px 18px;
  background: var(--surface);
  border-left: 1px solid var(--line);
}

.opl-room-card {
  padding: 14px 14px 16px;
  text-align: center;
  background: var(--surface-raised);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: var(--shadow-card);
  transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
}

/* D-pad focus needs its own marker: programmatic .focus() does not always
   trigger :focus-visible in a WebView. The global ring lives in
   dpadNavigation.ts; these rules add the TV-style lift for the lobby. */
.opl-room-card[data-dpad-focus] {
  border-color: var(--accent-strong);
  box-shadow:
    0 0 0 3px color-mix(in srgb, var(--accent-strong) 34%, transparent),
    var(--shadow-panel);
  transform: translateY(-1px);
}

.opl-room-label {
  margin: 0 0 6px;
  font-size: 10px;
  letter-spacing: 2.4px;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--muted) 76%, var(--amber));
}

.opl-room-code {
  margin: 0 0 12px;
  font-family: var(--font-mono);
  font-size: 40px;
  line-height: 1;
  letter-spacing: 7px;
  text-indent: 7px;
  color: var(--accent);
}

/* The code keeps its own light plate in both themes so it stays scannable. */
.opl-qr {
  display: block;
  width: 132px;
  height: 132px;
  margin: 0 auto;
  padding: 5px;
  background: #fffdf9;
  border-radius: 10px;
  image-rendering: pixelated;
}

.opl-room-url {
  margin: 10px 0 0;
  font-size: 11px;
  word-break: break-all;
  color: var(--muted);
}

.opl-rail-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 2px 0;
  font-size: 10px;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--muted) 76%, var(--amber));
}

.opl-roster {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--line-strong) transparent;
}

.opl-roster-empty {
  padding: 14px 10px;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  color: var(--muted);
  background: var(--surface-muted);
  border: 1px dashed var(--line);
  border-radius: 12px;
}

.opl-player {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 6px 7px;
  border-radius: 11px;
  transition: background 140ms ease;
}

.opl-player:hover { background: var(--surface-muted); }

.opl-player[data-dpad-focus] {
  background: var(--surface-muted);
  box-shadow: inset 0 0 0 2px var(--accent-strong);
}

.opl-player[data-dpad-focus] .opl-kick { opacity: 1; }

.opl-avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 500;
  color: var(--on-accent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--shadow-color) 18%, transparent);
}

.opl-player.is-offline .opl-avatar { opacity: 0.4; }

.opl-player-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.opl-player.is-offline .opl-player-name { color: var(--muted); }

.opl-badge {
  display: grid;
  place-items: center;
  flex: none;
  width: 22px;
  height: 22px;
}

.opl-badge-ready { color: var(--sage); }
.opl-badge-crown { color: var(--amber); }
.opl-badge-phone { color: var(--muted); }

.opl-kick {
  display: grid;
  place-items: center;
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: var(--danger);
  opacity: 0;
  transition: opacity 140ms ease, background 140ms ease;
}

.opl-player:hover .opl-kick,
.opl-kick:focus-visible,
.opl-kick[data-dpad-focus] { opacity: 1; }

.opl-kick:hover { background: var(--danger-soft); }

/* ---------- primary action ---------- */

.opl-start {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 13px;
  font-size: 15px;
  font-weight: 500;
  color: var(--on-accent);
  background: var(--accent);
  border-radius: 13px;
  box-shadow: var(--shadow-card);
  transition: background 150ms ease, transform 150ms ease, box-shadow 150ms ease;
}

.opl-start:hover:not(:disabled) {
  background: var(--accent-strong);
  transform: translateY(-2px);
  box-shadow: var(--shadow-panel);
}

.opl-start:active:not(:disabled) { transform: translateY(0); }

.opl-start:disabled,
.opl-start[aria-disabled="true"] {
  color: var(--muted);
  background: var(--surface-muted);
  border: 1px solid var(--line);
  box-shadow: none;
  cursor: default;
}

.opl-start[data-dpad-focus]:not([aria-disabled="true"]) {
  background: var(--accent-strong);
  transform: translateY(-2px);
  box-shadow: var(--shadow-panel);
}

.opl-note {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  color: var(--muted);
}

.opl-note.is-error { color: var(--danger); }

@media (pointer: coarse) {
  .opl-option, .opl-toggle, .opl-back, .opl-start, .opl-stepper button {
    min-height: 46px;
    touch-action: manipulation;
  }
  .opl-option, .opl-back, .opl-start { font-size: 15px; }
  .opl-qr { width: 154px; height: 154px; }
}

@media (max-width: 900px) {
  .opl-open-grid { grid-template-columns: 1fr; }
  .opl-howto { max-height: none; }
  .opl-open-cover { width: 104px; }
}

/* ---------- narrow screens ---------- */

@media (max-width: 900px) {
  .opl-shell { grid-template-columns: auto minmax(0, 1fr); }
  .opl-rail {
    position: absolute;
    inset: 0 0 auto auto;
    height: 100%;
    box-shadow: var(--shadow-modal);
  }
}

@media (max-width: 640px) {
  .opl-rail { width: 220px; }
  .opl-main { padding: 18px 16px; }
}

/* ---------- TV lobby (3 columns, zero scroll) ---------- */

.opl-shell.is-tv-lobby {
  display: block;
  padding: 1.4062vw 3.5vw;
  overflow: hidden;
}

.opl-shell.is-tv-lobby > .opl-dock,
.opl-shell.is-tv-lobby > .opl-main,
.opl-shell.is-tv-lobby > .opl-rail {
  display: none !important;
}

.opl-tv-lobby {
  display: grid;
  grid-template-columns: 29fr 43fr 28fr;
  gap: 1.4vw;
  width: 100%;
  height: calc(100% - 2.8125vw);
  max-height: calc(100% - 2.8125vw);
  min-height: 0;
  overflow: hidden;
}

.opl-tv-lobby[hidden] { display: none; }

.tv-panel {
  box-sizing: border-box;
  min-width: 0;
  min-height: 0;
  max-height: 100%;
  padding: 1.0125vw 1.4vw;
  overflow: hidden;
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 22px;
}

.tv-panel-left {
  display: flex;
  flex-direction: column;
  gap: 0.42vw;
}

.tv-game-head {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1vw;
  flex: none;
}

.tv-kicker,
.tv-label,
.tv-room-label {
  margin: 0;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #7dd3fc;
}

.tv-game-title.is-compact {
  font-size: clamp(30px, 2.9vw, 54px);
}

.tv-game-title {
  margin: 0.15vw 0 0;
  font-family: var(--font-display);
  font-size: clamp(38px, 3.8vw, 64px);
  line-height: 0.96;
  font-weight: 900;
  letter-spacing: -0.035em;
  transform: rotate(-2.5deg);
  transform-origin: left center;
  background: linear-gradient(180deg, #FFE259 0%, var(--logo-bottom, #FFA751) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
  filter:
    drop-shadow(0 4px 0 rgba(0, 0, 0, 0.32))
    drop-shadow(0 0 18px rgba(255, 167, 81, 0.26));
  width: fit-content;
}

.tv-eyebrow {
  margin: 0.225vw 0 0;
  font-size: 14px;
  color: #94a3b8;
}

.tv-exit-hint {
  flex: none;
  padding-top: 0.2812vw;
  font-size: 14px;
  color: #94a3b8;
  white-space: nowrap;
}

.tv-host-art {
  position: relative;
  flex: 1 1 0;
  min-height: 0;
  overflow: visible;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.tv-host-art img {
  position: absolute;
  left: 50%;
  bottom: -18%;
  width: 138%;
  height: 138%;
  max-width: none;
  max-height: none;
  object-fit: contain;
  object-position: center bottom;
  border: 0;
  border-radius: 0;
  transform: translateX(-50%) scale(1);
  transform-origin: center bottom;
  filter: drop-shadow(0 15px 25px rgba(0, 0, 0, 0.6));
  pointer-events: none;
  z-index: 0;
}

.tv-host-art.is-cover img {
  bottom: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
  border-radius: 0;
}

.tv-pack-block,
.tv-tune-block {
  position: relative;
  z-index: 2;
  flex: none;
  min-height: 0;
}

.tv-pack-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.25vw;
  margin-top: 0.35vw;
}

.tv-pack-grid.is-single {
  grid-template-columns: 1fr;
}

.tv-pack-btn {
  --pack-gradient: linear-gradient(135deg, #10b981, #06b6d4);
  --pack-border: #5eead4;
  --pack-glow: rgba(45, 212, 191, 0.5);
  --pack-ink: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.35vw;
  min-height: 2.6vw;
  padding: 0.35vw 0.45vw;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  color: #cbd5e1;
  font-size: 0.85rem;
  font-weight: 700;
  line-height: 1.1;
  text-align: center;
  white-space: normal;
  transition:
    background 140ms ease,
    border-color 140ms ease,
    box-shadow 140ms ease,
    transform 140ms ease;
}

.tv-pack-btn.is-mexico {
  --pack-gradient: linear-gradient(135deg, #10b981, #06b6d4);
  --pack-border: #5eead4;
  --pack-glow: rgba(45, 212, 191, 0.5);
}

.tv-pack-btn.is-cine {
  --pack-gradient: linear-gradient(135deg, #7c3aed, #db2777);
  --pack-border: #f0abfc;
  --pack-glow: rgba(219, 39, 119, 0.48);
}

.tv-pack-btn.is-afterdark {
  --pack-gradient: linear-gradient(135deg, #dc2626, #fb7185);
  --pack-border: #fda4af;
  --pack-glow: rgba(248, 113, 113, 0.48);
}

.tv-pack-btn.is-mixto {
  --pack-gradient: linear-gradient(135deg, #f59e0b, #facc15);
  --pack-border: #fde68a;
  --pack-glow: rgba(250, 204, 21, 0.45);
  --pack-ink: #1a1206;
}

.tv-pack-btn.is-active {
  background: var(--pack-gradient);
  border: 1.5px solid var(--pack-border);
  box-shadow: 0 0 14px var(--pack-glow);
  color: var(--pack-ink);
  transform: scale(1.03);
}

.tv-pack-btn[data-dpad-focus] {
  border: 2px solid #ffffff;
  box-shadow:
    0 0 0 2px rgba(255, 255, 255, 0.22),
    0 0 18px var(--pack-glow);
  transform: scale(1.04);
  z-index: 2;
}

.tv-pack-icon {
  display: grid;
  place-items: center;
  flex: none;
  font-family: "Noto Color Emoji", "Apple Color Emoji", "Segoe UI Emoji", sans-serif;
  font-size: 1.12em;
  line-height: 1;
}

.tv-pack-icon svg {
  width: 1.12em;
  height: 1.12em;
}

.tv-pack-label {
  min-width: 0;
  overflow-wrap: anywhere;
}

.tv-mode-summary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.35vw;
  min-height: 2.6vw;
  padding: 0.35vw 0.45vw;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  color: #cbd5e1;
  font-size: 0.85rem;
  font-weight: 700;
  line-height: 1.1;
  text-align: center;
}

.tv-settings-note {
  position: relative;
  z-index: 2;
  margin: auto 0 0;
  padding-top: 0.4vw;
  font-size: 14px;
  line-height: 1.35;
  color: #7b8794;
}

.tv-pack-desc {
  min-height: 3.7vw;
  margin: 0.35vw 0 0;
  font-size: 14px;
  line-height: 1.32;
  color: #a9b4c6;
}

.tv-tune-grid {
  display: grid;
  gap: 0.22vw;
  margin-top: 0.35vw;
}

.tv-setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8vw;
  min-height: 1.95vw;
}

.tv-setting-label {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.15;
  color: #cbd5e1;
  text-overflow: ellipsis;
}

.tv-stepper {
  display: flex;
  align-items: center;
  flex: none;
  gap: 0.35vw;
}

.tv-stepper button {
  display: grid;
  place-items: center;
  width: 1.9125vw;
  height: 1.9125vw;
  min-width: 28px;
  min-height: 28px;
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: 9px;
  background: rgba(30, 41, 59, 0.85);
  color: #e2e8f0;
  font-size: 18px;
  font-weight: 800;
}

.tv-stepper button:disabled {
  opacity: 0.35;
  cursor: default;
}

.tv-stepper output {
  min-width: 3.2vw;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 800;
  color: #f8fafc;
}

.tv-toggle {
  display: flex;
  align-items: center;
  gap: 0.6vw;
  min-height: 1.95vw;
  padding: 0.2812vw 0.7vw;
  border: 1px solid rgba(148, 163, 184, 0.26);
  border-radius: 10px;
  background: rgba(30, 41, 59, 0.62);
  color: #cbd5e1;
  font-size: 14px;
  font-weight: 700;
  text-align: left;
}

.tv-toggle.is-on {
  border-color: rgba(56, 189, 248, 0.55);
  color: #f8fafc;
}

.tv-toggle-check {
  display: grid;
  place-items: center;
  flex: none;
  width: 1.2375vw;
  height: 1.2375vw;
  min-width: 20px;
  min-height: 20px;
  border: 1px solid rgba(148, 163, 184, 0.5);
  border-radius: 6px;
  color: transparent;
  font-size: 12px;
}

.tv-toggle.is-on .tv-toggle-check {
  border-color: #38bdf8;
  background: #2d7df6;
  color: #fff;
}

.tv-toggle[data-dpad-focus],
.tv-stepper button[data-dpad-focus] {
  border-color: #38bdf8;
  background: rgba(45, 125, 246, 0.3);
}

/* centre column */

.tv-panel-center {
  display: flex;
  flex-direction: column;
  gap: 0.5625vw;
  /* The native "Salir" pill floats over the top centre of the WebView. */
  padding-top: 3vw;
}

.tv-center-head {
  flex: none;
}

.tv-center-head h2 {
  margin: 0;
  font-size: clamp(18px, 1.55vw, 26px);
  line-height: 1.1;
  letter-spacing: 0.03em;
  color: #f8fafc;
}

.tv-player-count {
  color: #7dd3fc;
  font-weight: 700;
}

.tv-slots-wrap {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.tv-slots {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 0.5062vw;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.tv-slot {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.2812vw;
  min-width: 0;
  min-height: 0;
  padding: 0.45vw 0.5vw;
  overflow: hidden;
  border-radius: 14px;
}

.tv-slot.is-empty {
  border: 1px dashed rgba(148, 163, 184, 0.24);
  background: rgba(15, 23, 42, 0.4);
  color: rgba(148, 163, 184, 0.5);
  font-family: var(--font-mono);
  font-size: 14px;
}

.tv-slot.is-filled {
  border: 1px solid rgba(56, 189, 248, 0.28);
  background: rgba(19, 29, 51, 0.86);
  animation: tv-slot-in 240ms ease-out both;
  animation-delay: var(--slot-delay, 0ms);
}

@keyframes tv-slot-in {
  from { opacity: 0; transform: scale(0.92); }
  to { opacity: 1; transform: scale(1); }
}

.tv-slot-avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 2.3625vw;
  height: 2.3625vw;
  min-width: 34px;
  min-height: 34px;
  overflow: hidden;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 800;
}

.tv-slot-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.tv-slot-name {
  max-width: 100%;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.15;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #f1f5f9;
}

.tv-slot-badges {
  position: absolute;
  top: 0.2812vw;
  right: 0.45vw;
  display: flex;
  gap: 0.2vw;
}

.tv-slot-badge { display: grid; place-items: center; width: 1.2375vw; height: 1.2375vw; min-width: 18px; min-height: 18px; }
.tv-slot-badge.is-crown { color: #facc15; }
.tv-slot-badge.is-ready { color: #86efac; }
.tv-slot-badge.is-phone { color: #94a3b8; }

.tv-kick {
  position: absolute;
  right: 0.5vw;
  bottom: 0.2812vw;
  display: grid;
  place-items: center;
  width: 1.575vw;
  height: 1.575vw;
  min-width: 24px;
  min-height: 24px;
  border: 1px solid rgba(248, 113, 113, 0.4);
  border-radius: 50%;
  background: rgba(59, 31, 36, 0.9);
  color: #fca5a5;
  opacity: 0;
  transition: opacity 120ms ease, transform 120ms ease;
}

.tv-slot[data-dpad-focus] .tv-kick,
.tv-kick[data-dpad-focus] { opacity: 1; }

.tv-slot[data-dpad-focus] {
  border-color: #38bdf8;
  background: rgba(30, 58, 138, 0.48);
}

.tv-empty {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.3375vw;
  padding: 1.125vw;
  pointer-events: none;
  text-align: center;
  background: rgba(15, 23, 42, 0.42);
  border-radius: 14px;
}

.tv-empty[hidden] { display: none; }

.tv-empty p {
  margin: 0;
  font-size: clamp(18px, 1.45vw, 24px);
  font-weight: 700;
  color: #f8fafc;
}

.tv-empty span {
  font-size: 14px;
  color: #a9b4c6;
}

.tv-action-footer {
  display: flex;
  flex-direction: column;
  gap: 0.3094vw;
  flex: none;
}

.tv-action-footer .opl-start {
  position: relative;
  z-index: 0;
  width: 100%;
  min-height: 4.4vw;
  border-radius: 9999px;
  border: 2px solid rgba(255, 247, 194, 0.55);
  background: linear-gradient(135deg, #facc15 0%, #fbbf24 48%, #fb923c 100%);
  color: #1a1206;
  box-shadow:
    0 0 0 2px rgba(250, 204, 21, 0.18),
    0 0 30px rgba(250, 204, 21, 0.38);
  font-size: clamp(18px, 1.6vw, 30px);
  font-weight: 900;
  letter-spacing: 0.055em;
  text-transform: uppercase;
}

.tv-action-footer .opl-start[aria-disabled="true"] {
  border-color: rgba(148, 163, 184, 0.22);
  background: rgba(30, 41, 59, 0.78);
  color: #64748b;
  box-shadow: none;
}

.tv-action-footer .opl-start[data-dpad-focus]:not([aria-disabled="true"]) {
  transform: scale(1.02);
  box-shadow:
    0 0 0 3px rgba(255, 247, 194, 0.6),
    0 0 38px rgba(250, 204, 21, 0.55);
}

.tv-action-footer .opl-start.is-ready {
  box-shadow:
    0 0 20px rgba(56, 189, 248, 0.5),
    0 0 36px rgba(250, 204, 21, 0.18);
}

.tv-action-footer .opl-start.is-ready::before {
  content: "";
  position: absolute;
  inset: -2px;
  z-index: -1;
  border-radius: inherit;
  box-shadow:
    0 0 24px rgba(56, 189, 248, 0.6),
    0 0 48px rgba(56, 189, 248, 0.26);
  opacity: 0.45;
  pointer-events: none;
  animation: tv-cta-glow 1.8s ease-in-out infinite;
}

@keyframes tv-cta-glow {
  0%, 100% { opacity: 0.3; transform: scale(0.995); }
  50% { opacity: 0.85; transform: scale(1.01); }
}

.tv-action-footer .opl-note {
  margin: 0;
  min-height: 1.35vw;
  font-size: 14px;
  line-height: 1.3;
  text-align: center;
}

.tv-rules-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.8vw;
  flex: none;
  padding-top: 0.15vw;
}

.tv-rules-button,
.tv-rules-close {
  min-height: 2.2vw;
  padding: 0.25vw 0.8vw;
  border: 2px solid rgba(56, 189, 248, 0.35);
  border-radius: 9999px;
  background: rgba(30, 41, 59, 0.82);
  color: #bae6fd;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.tv-rules-button[data-dpad-focus],
.tv-rules-close[data-dpad-focus] {
  border-color: #facc15;
  background: rgba(250, 204, 21, 0.18);
  transform: scale(1.05);
  box-shadow: 0 0 18px rgba(250, 204, 21, 0.3);
}

.tv-rules-tip {
  flex: none;
  margin: 0;
  font-size: 14px;
  line-height: 1.25;
  color: #64748b;
}

.tv-rules-modal {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: grid;
  place-items: center;
  padding: 2.8125vw 8vw;
  background: rgba(2, 6, 23, 0.8);
}

.tv-rules-modal[hidden] { display: none; }

.tv-rules-card {
  box-sizing: border-box;
  width: min(760px, 82vw);
  max-height: 43.875vw;
  overflow: auto;
  padding: 2vw 2.4vw;
  background: #0f172a;
  border: 2px solid rgba(56, 189, 248, 0.35);
  border-radius: 24px;
  box-shadow: 0 0 48px rgba(56, 189, 248, 0.16);
  scrollbar-width: thin;
}

.tv-rules-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 2vw;
}

.tv-rules-head h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(22px, 2vw, 34px);
  font-weight: 900;
  letter-spacing: 0.04em;
  color: #facc15;
}

.tv-rules-game {
  margin: 0.3vw 0 0;
  font-size: 14px;
  font-weight: 700;
  color: #7dd3fc;
}

.tv-rules-title {
  margin: 1.2vw 0 0.6vw;
  font-size: clamp(16px, 1.3vw, 22px);
  font-weight: 900;
  letter-spacing: 0.08em;
  color: #f8fafc;
}

.tv-rules-body ul {
  display: grid;
  gap: 0.9vw;
  margin: 0;
  padding-left: 1.35em;
  font-size: clamp(15px, 1.15vw, 20px);
  line-height: 1.45;
  color: #dbeafe;
}

.tv-rules-body p {
  margin: 0;
  font-size: clamp(15px, 1.15vw, 20px);
  line-height: 1.5;
  color: #dbeafe;
}

/* organic player bubbles */

.tv-slots-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 25.3125vw;
  background:
    radial-gradient(circle at 50% 46%, rgba(56, 189, 248, 0.2), transparent 62%),
    radial-gradient(circle at 50% 46%, rgba(250, 204, 21, 0.08), transparent 80%);
  border-radius: 24px;
}

.tv-bubbles {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  align-content: center;
  justify-content: center;
  gap: 1vw;
  width: 100%;
  height: auto;
  min-height: 25.3125vw;
  max-height: 100%;
  padding: 1vw;
}

.tv-bubble {
  --bubble-color: #38bdf8;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.45vw;
  flex: 0 1 clamp(86px, 6.8vw, 132px);
  min-width: 0;
  min-height: clamp(86px, 6.8vw, 132px);
  padding: 0.65vw 0.5vw;
  overflow: hidden;
  border: 2px solid color-mix(in srgb, var(--bubble-color) 55%, rgba(148, 163, 184, 0.25));
  border-radius: 34% 66% 58% 42% / 46% 38% 62% 54%;
  background:
    radial-gradient(circle at 35% 25%, color-mix(in srgb, var(--bubble-color) 42%, transparent), transparent 62%),
    linear-gradient(155deg, rgba(19, 29, 51, 0.92), rgba(15, 23, 42, 0.96));
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--bubble-color) 22%, transparent),
    0 12px 26px rgba(0, 0, 0, 0.28);
  animation: tv-bubble-pop 360ms cubic-bezier(0.18, 1.65, 0.4, 1) both;
  animation-delay: var(--bubble-delay, 0ms);
}

.tv-bubble.is-ready {
  border-color: #86efac;
}

.tv-bubble[data-dpad-focus] {
  border-color: #facc15;
  box-shadow:
    0 0 0 3px rgba(250, 204, 21, 0.35),
    0 0 28px color-mix(in srgb, var(--bubble-color) 55%, transparent);
  transform: scale(1.06);
}

@keyframes tv-bubble-pop {
  0% { opacity: 0; transform: scale(0.2) translateY(8px); }
  70% { opacity: 1; transform: scale(1.04) translateY(0); }
  100% { opacity: 1; transform: scale(1); }
}

.tv-bubble-avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: clamp(34px, 3vw, 58px);
  height: clamp(34px, 3vw, 58px);
  overflow: hidden;
  border-radius: 50%;
  font-size: clamp(12px, 1.15vw, 20px);
  font-weight: 900;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.22);
}

.tv-bubble-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.tv-bubble-name {
  max-width: 100%;
  overflow: hidden;
  font-size: clamp(12px, 1.05vw, 17px);
  line-height: 1.1;
  font-weight: 900;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #f8fafc;
}

.tv-bubble-badges {
  position: absolute;
  top: 0.4vw;
  right: 0.45vw;
  display: flex;
  gap: 0.15vw;
}

.tv-bubble-badge {
  display: grid;
  place-items: center;
  width: clamp(16px, 1.35vw, 24px);
  height: clamp(16px, 1.35vw, 24px);
}

.tv-bubble-badge.is-crown { color: #facc15; }
.tv-bubble-badge.is-ready { color: #86efac; }
.tv-bubble-badge.is-phone { color: #94a3b8; }

.tv-bubble .tv-kick {
  right: 0.3vw;
  bottom: 0.3vw;
  width: clamp(22px, 1.8vw, 32px);
  height: clamp(22px, 1.8vw, 32px);
}

.tv-bubble[data-dpad-focus] .tv-kick { opacity: 1; }

/* right column */

.tv-panel-right {
  display: flex;
  align-items: center;
  justify-content: center;
  border-color: rgba(56, 189, 248, 0.14);
  box-shadow: 0 0 42px rgba(56, 189, 248, 0.08);
}

.tv-room-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.6188vw;
  width: 100%;
  max-width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  text-align: center;
}

.tv-room-label {
  color: #7dd3fc;
  letter-spacing: 0.22em;
}

.tv-room-code {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(58px, 6.8vw, 118px);
  line-height: 0.92;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-indent: 0.08em;
  color: #38bdf8;
  text-shadow: 0 0 26px rgba(56, 189, 248, 0.38);
}

.tv-qr-box {
  box-sizing: border-box;
  display: grid;
  place-items: center;
  flex: none;
  width: clamp(140px, 12.375vw, 260px);
  height: clamp(140px, 12.375vw, 260px);
  max-width: 100%;
  max-height: 100%;
  padding: 0.675vw;
  background: #fffdf9;
  border-radius: 1rem;
}

.tv-qr {
  display: block;
  width: 100% !important;
  height: 100% !important;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
}

.tv-qr-placeholder {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.12);
}

.tv-join-main {
  margin: 0;
  font-size: clamp(14px, 1.1vw, 18px);
  font-weight: 700;
  color: #e2e8f0;
}

.tv-join-divider {
  display: flex;
  align-items: center;
  gap: 0.8vw;
  width: 80%;
  font-size: 13px;
  color: #64748b;
}

.tv-join-divider::before,
.tv-join-divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background: rgba(148, 163, 184, 0.22);
}

.tv-join-url {
  margin: 0;
  font-size: 14px;
  line-height: 1.35;
  color: #a9b4c6;
  overflow-wrap: anywhere;
}

.tv-join-url strong {
  color: #e2e8f0;
  font-weight: 700;
}

.tv-room-card[data-dpad-focus] {
  outline: none !important;
}

.tv-room-card[data-dpad-focus] .tv-qr-box {
  outline: 3px solid rgba(56, 189, 248, 0.72);
  outline-offset: 4px;
  box-shadow: 0 0 24px rgba(56, 189, 248, 0.25);
}

@media (max-aspect-ratio: 4/3) {
  .opl-tv-lobby { grid-template-columns: 30fr 40fr 30fr; gap: 1vw; }
  .tv-panel { padding: 0.9vw 1.1vw; }
}
`;

/** Injects the stylesheet once per document. */
export function installShellStyles(): void {
  if (document.getElementById(STYLE_ELEMENT_ID)) {
    return;
  }

  const style = document.createElement("style");
  style.id = STYLE_ELEMENT_ID;
  style.textContent = css;
  document.head.appendChild(style);
}
