import {
  isLobbyFieldVisible,
  type AvailableGameDto,
  type PlayerSnapshot,
  type RoomSnapshot,
  type SupportedLanguage
} from "@open-party-lab/protocol";
import { getHostText } from "../i18n/hostText.js";
import { escapeHtml } from "./escapeHtml.js";
import { renderGameGlyph, renderUiIcon } from "./gameGlyphs.js";
import { partyRulesFor } from "./partyRules.js";

type LobbyField = NonNullable<AvailableGameDto["lobbySetup"]>["fields"][number];
type SettingsMap = Record<string, string | number | boolean>;

export interface TvLobbyOptions {
  game: AvailableGameDto;
  settings: SettingsMap;
  language: SupportedLanguage;
  room: RoomSnapshot;
  qrDataUrl: string | null;
  action: { label: string; enabled: boolean; note: string | null };
  canKick: boolean;
  hostControlHolderId: string | null;
  hostControlRequestId: string | null;
  locked: boolean;
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

function avatarInk(color: string): string {
  const hex = color.replace("#", "");
  const full =
    hex.length === 3
      ? hex
          .split("")
          .map((character) => character + character)
          .join("")
      : hex;
  const value = Number.parseInt(full, 16);
  if (!Number.isFinite(value) || full.length !== 6) return "#ffffff";
  const channels = [(value >> 16) & 255, (value >> 8) & 255, value & 255].map((channel) => {
    const ratio = channel / 255;
    return ratio <= 0.03928 ? ratio / 12.92 : ((ratio + 0.055) / 1.055) ** 2.4;
  });
  const luminance = 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
  return luminance > 0.45 ? "#1d1d1b" : "#ffffff";
}

function fieldValue(field: LobbyField, settings: SettingsMap): unknown {
  return settings[field.settingKey ?? field.id] ?? field.defaultValue;
}

const CUTOUT_HOSTS = new Set([
  "banterbolt",
  "pantomime",
  "merkissioner",
  "schattenjagd"
]);

const GAME_TONES: Record<string, string> = {
  pantomime: "#f97316",
  imposter: "#38bdf8",
  buzzwort: "#fb7185",
  merkissioner: "#f59e0b",
  schattenjagd: "#2dd4bf",
  "magic-arena": "#a855f7",
  "zeichnen-und-erraten": "#ec4899",
  "dungeon-party": "#fb923c",
  "you-got-it": "#06b6d4",
  "card-table": "#22c55e",
  flatterfluff: "#f472b6",
  schaetzorama: "#fb923c"
};

const PACK_META: Record<string, { icon: string; tone: string }> = {
  mexico: { icon: "🌶️", tone: "mexico" },
  cine: { icon: "🎬", tone: "cine" },
  afterdark: { icon: "🔥", tone: "afterdark" },
  mixto: { icon: "🎲", tone: "mixto" }
};

function renderPackField(
  field: LobbyField & { kind: "select" },
  settings: SettingsMap,
  locked: boolean,
  index: number,
  gameAccent: string,
  single: boolean,
  genericIcon: string
): string {
  const current = String(fieldValue(field, settings));
  const selected = field.options.find((option) => option.id === current);
  const buttons = field.options
    .map((option) => {
      const meta = PACK_META[option.id] ?? { icon: genericIcon, tone: "game" };
      const accentStyle =
        meta.tone === "game"
          ? ` style="--pack-gradient:linear-gradient(135deg, color-mix(in srgb, ${escapeHtml(
              gameAccent
            )} 72%, #0b1220) 0%, ${escapeHtml(gameAccent)} 100%);--pack-border:color-mix(in srgb, ${escapeHtml(
              gameAccent
            )} 65%, #fff);--pack-glow:color-mix(in srgb, ${escapeHtml(
              gameAccent
            )} 55%, transparent);"`
          : "";
      return `
        <button type="button" class="tv-pack-btn is-${meta.tone}${option.id === current ? " is-active" : ""}"
                aria-pressed="${option.id === current ? "true" : "false"}"
                ${locked ? "disabled" : ""}${accentStyle}
                data-action="setup-select"
                data-action-key="${escapeHtml(field.actionKey ?? field.id)}"
                data-value="${escapeHtml(option.id)}"
                data-dpad-key="pack:${index}:${escapeHtml(option.id)}">
          <span class="tv-pack-icon" aria-hidden="true">${meta.icon}</span>
          <span class="tv-pack-label">${escapeHtml(option.label)}</span>
        </button>`;
    })
    .join("");

  return `
    <section class="tv-pack-block" aria-label="${escapeHtml(field.label)}">
      <h3 class="tv-label">${escapeHtml(field.label)}</h3>
      <div class="tv-pack-grid${single ? " is-single" : ""}">${buttons}</div>
      ${selected?.description ? `<p class="tv-pack-desc">${escapeHtml(selected.description)}</p>` : ""}
    </section>
  `;
}

function renderNumberField(
  field: LobbyField & { kind: "number" },
  settings: SettingsMap,
  locked: boolean
): string {
  const raw = Number(fieldValue(field, settings));
  const current = Number.isFinite(raw) ? raw : field.defaultValue;
  const key = escapeHtml(field.actionKey ?? field.id);
  const minusDisabled = locked || current - field.step < field.min;
  const plusDisabled = locked || current + field.step > field.max;

  return `
    <div class="tv-setting-row">
      <span class="tv-setting-label">${escapeHtml(field.label)}</span>
      <div class="tv-stepper" role="group" aria-label="${escapeHtml(field.label)}">
        <button type="button" ${minusDisabled ? "disabled" : ""}
                data-action="setup-number" data-action-key="${key}"
                data-value="${current - field.step}"
                data-dpad-key="setting:${key}:minus"
                aria-label="${escapeHtml(field.label)} menos">−</button>
        <output>${current}</output>
        <button type="button" ${plusDisabled ? "disabled" : ""}
                data-action="setup-number" data-action-key="${key}"
                data-value="${current + field.step}"
                data-dpad-key="setting:${key}:plus"
                aria-label="${escapeHtml(field.label)} más">+</button>
      </div>
    </div>
  `;
}

function renderToggleField(
  field: LobbyField & { kind: "toggle" },
  settings: SettingsMap,
  locked: boolean
): string {
  const on = String(fieldValue(field, settings)) === field.onValue;
  const nextValue = on ? field.offValue : field.onValue;

  return `
    <button type="button" class="tv-toggle${on ? " is-on" : ""}"
            aria-pressed="${on ? "true" : "false"}"
            ${locked ? "disabled" : ""}
            data-action="setup-select"
            data-action-key="${escapeHtml(field.actionKey ?? field.id)}"
            data-value="${escapeHtml(nextValue)}"
            data-dpad-key="setting:${escapeHtml(field.actionKey ?? field.id)}">
      <span class="tv-toggle-check" aria-hidden="true">✓</span>
      <span>${escapeHtml(field.label)}</span>
    </button>
  `;
}

function renderModeSummary(
  field: LobbyField & { kind: "select" },
  settings: SettingsMap,
  genericIcon: string
): string {
  const current = String(fieldValue(field, settings));
  const selected = field.options.find((option) => option.id === current);
  return `
    <section class="tv-pack-block" aria-label="${escapeHtml(field.label)}">
      <h3 class="tv-label">${escapeHtml(field.label)}</h3>
      <div class="tv-mode-summary">
        <span class="tv-pack-icon" aria-hidden="true">${genericIcon}</span>
        <span class="tv-pack-label">${escapeHtml(selected?.label ?? current)}</span>
      </div>
      ${selected?.description ? `<p class="tv-pack-desc">${escapeHtml(selected.description)}</p>` : ""}
    </section>
  `;
}

export function renderTvLeft(options: TvLobbyOptions): string {
  const { game, settings, locked } = options;
  const setup = game.lobbySetup;
  const fields = (setup?.fields ?? []).filter((field) =>
    isLobbyFieldVisible(field, setup?.fields ?? [], settings)
  );
  const selects = fields.filter(
    (field): field is LobbyField & { kind: "select" } => field.kind === "select"
  );
  const primary = selects[0];
  const hasCutout = CUTOUT_HOSTS.has(game.id);
  const gameAccent = GAME_TONES[game.id] ?? game.visual?.accent ?? "#38bdf8";
  const hostArt = hasCutout
    ? `/party-covers/${encodeURIComponent(game.id)}-host.webp`
    : `/party-covers/${encodeURIComponent(game.id)}.webp`;
  const logoStyle =
    game.id === "banterbolt"
      ? ""
      : `style="--logo-bottom:${escapeHtml(gameAccent)}"`;
  const genericIcon =
    renderGameGlyph(game.visual?.icon, game.visual?.iconPath, 18) || "🎮";
  const pack =
    primary == null
      ? ""
      : primary.options.length <= 4
        ? renderPackField(
            primary,
            settings,
            locked,
            0,
            gameAccent,
            game.id !== "banterbolt",
            genericIcon
          )
        : renderModeSummary(primary, settings, genericIcon);

  return `
    <header class="tv-game-head">
      <div>
        <h1 class="tv-game-title${game.id === "banterbolt" ? "" : " is-compact"}" ${logoStyle}>${escapeHtml(
          game.displayName
        )}</h1>
      </div>
    </header>

    <div class="tv-host-art ${hasCutout ? "is-cutout" : "is-cover"}">
      <img src="${hostArt}" alt="" loading="eager" />
    </div>

    ${pack}

    <p class="tv-settings-note">Ajustes finos desde el celular del anfitrión.</p>
  `;
}

function renderPlayerBubble(
  player: PlayerSnapshot,
  options: TvLobbyOptions,
  index: number
): string {
  const { language } = options;
  const text = getHostText(language);
  const badges: string[] = [];
  if (player.id === options.hostControlHolderId) {
    badges.push(
      `<span class="tv-bubble-badge is-crown" title="${escapeHtml(
        text.hostControlDelegatedTitle
      )}">${renderUiIcon("crown", 15)}</span>`
    );
  }
  if (player.id === options.hostControlRequestId) {
    badges.push(
      `<span class="tv-bubble-badge is-phone" title="${escapeHtml(
        text.hostControlRequestTitle
      )}">${renderUiIcon("phone", 15)}</span>`
    );
  }
  if (player.isReady) {
    badges.push(
      `<span class="tv-bubble-badge is-ready" title="${escapeHtml(
        text.ready
      )}">${renderUiIcon("check", 15, 2.1)}</span>`
    );
  }

  const kick = options.canKick
    ? `<button type="button" class="tv-kick" data-action="kick"
               data-dpad-cross-skip
               data-player-id="${escapeHtml(player.id)}"
               data-dpad-key="kick:${escapeHtml(player.id)}"
               aria-label="${escapeHtml(text.kickPlayer(player.name))}">${renderUiIcon("kick", 16)}</button>`
    : "";

  const avatar = player.avatar
    ? `<img src="${escapeHtml(player.avatar)}" alt="" />`
    : escapeHtml(initials(player.name));
  const stateLabel = player.isReady ? `, ${text.ready}` : "";

  return `
    <div class="tv-bubble${player.isReady ? " is-ready" : ""}" tabindex="0" role="group"
         data-dpad-key="player:${escapeHtml(player.id)}"
         aria-label="${escapeHtml(`${player.name}${stateLabel}`)}"
         style="--bubble-color:${escapeHtml(player.color)};--bubble-delay:${
           Math.min(index, 10) * 35
         }ms">
      <span class="tv-bubble-avatar" style="background:${escapeHtml(
        player.color
      )};color:${avatarInk(player.color)}">${avatar}</span>
      <span class="tv-bubble-name">${escapeHtml(player.name)}</span>
      ${badges.length ? `<span class="tv-bubble-badges">${badges.join("")}</span>` : ""}
      ${kick}
    </div>
  `;
}

export function renderTvSlots(options: TvLobbyOptions): string {
  return options.room.players
    .map((player, index) => renderPlayerBubble(player, options, index))
    .join("");
}

export function renderTvRoom(options: TvLobbyOptions): string {
  const { room } = options;
  let host = room.joinUrl;
  try {
    host = new URL(room.joinUrl).host;
  } catch {
    // Keep the raw URL when it is not absolute.
  }

  return `
    <div class="tv-room-card" tabindex="0" role="group"
         data-dpad-key="room-card" data-dpad-autofocus
         aria-label="Sala ${escapeHtml(room.code)}. Escanea el código QR para entrar.">
      <span class="tv-room-label">CÓDIGO DE SALA</span>
      <div class="tv-room-code">${escapeHtml(room.code)}</div>
      <div class="tv-qr-box">
        <canvas class="tv-qr" data-qr-canvas role="img"
                aria-label="Código QR para unirse a la sala ${escapeHtml(room.code)}"></canvas>
      </div>
      <p class="tv-join-main">Escanea con tu celular</p>
      <div class="tv-join-divider">o</div>
      <p class="tv-join-url">Entra en <strong>${escapeHtml(host)}</strong></p>
    </div>
  `;
}

export function renderTvEmpty(language: SupportedLanguage): string {
  if (language === "en") {
    return `<p>Nobody here yet.</p><span>Scan the code to join!</span>`;
  }
  if (language === "de") {
    return `<p>Noch niemand da.</p><span>Scanne den Code und mach mit!</span>`;
  }
  return `<p>Todavía no hay nadie.</p><span>¡Escanea para empezar!</span>`;
}


export function renderTvRules(game: AvailableGameDto, _language: SupportedLanguage): string {
  const lines = partyRulesFor(game.id);
  const body = lines.length
    ? `<ul>${lines.map((line) => `<li>${escapeHtml(line)}</li>`).join("")}</ul>`
    : `<p>${escapeHtml(game.description)}</p>`;
  return `
    <p class="tv-rules-game">${escapeHtml(game.displayName)}</p>
    ${body}
  `;
}
