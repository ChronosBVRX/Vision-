import QRCode from "qrcode";
import { resolveGameMinPlayers } from "@open-party-lab/game-core";
import {
  canManagePlayerRoster,
  hasActiveRound,
  type PlayerSnapshot,
  type SupportedLanguage
} from "@open-party-lab/protocol";
import {
  applyThemeVariables,
  normalizeThemeName,
  themes,
  type ThemeName
} from "@open-party-lab/ui-kit";
import type { HostAppState, HostSocketClient } from "../app/hostSocketClient.js";
import { focusDpad, restoreDpadFocus } from "../app/dpadNavigation.js";
import { resolveHostSurface } from "../app/hostSurface.js";
import { requiresReadyAutoStart } from "../app/roundStartPolicy.js";
import { getHostText } from "../i18n/hostText.js";
import { installShellStyles } from "./shellStyles.js";
import { renderCatalog, renderOpenGame } from "./shellCatalog.js";
import { renderRoomCard, renderRoster, type RailOptions } from "./shellRail.js";
import { renderTvEmpty, renderTvLeft, renderTvRoom, renderTvRules, renderTvSlots } from "./tvLobby.js";
import { renderUiIcon } from "./gameGlyphs.js";
import { escapeHtml } from "./escapeHtml.js";

/** Signal other chrome reads to avoid duplicating what the shell already shows. */
export const SHELL_ACTIVE_ATTRIBUTE = "data-opl-shell";

/** Event the dock fires so the settings panel can open without a direct import. */
export const OPEN_HOST_CONTROLS_EVENT = "opl:open-host-controls";

/** Same idea for full screen, which the floating overlay owns. */
export const TOGGLE_FULLSCREEN_EVENT = "opl:toggle-fullscreen";

interface SectionSignatures {
  head: string;
  catalog: string;
  room: string;
  roster: string;
  action: string;
}

const emptySignatures: SectionSignatures = {
  head: "",
  catalog: "",
  room: "",
  roster: "",
  action: ""
};

function describePlayer(player: PlayerSnapshot): string {
  return [
    player.id,
    player.name,
    player.avatar ?? "",
    player.color,
    player.isReady ? "1" : "0",
    player.connected ? "1" : "0",
    player.selectedCharacterName ?? ""
  ].join(":");
}

function toRailOptions(state: HostAppState, qrDataUrl: string | null): RailOptions {
  const room = state.room;

  return {
    roomCode: room?.code ?? "----",
    joinUrl: room?.joinUrl ?? "",
    qrDataUrl,
    players: room?.players ?? [],
    language: room?.language ?? state.preferredLanguage,
    canKick: canManagePlayerRoster(room),
    hostControlHolderId: room?.hostControl.holderPlayerId ?? null,
    hostControlRequestId: room?.hostControl.pendingRequest?.playerId ?? null
  };
}

/**
 * Whether the round can be started from here right now.
 *
 * Games that start themselves once everyone is ready get a disabled button with
 * the ready count as its label, so the screen still says what it is waiting for
 * without a paragraph of explanation.
 */
function resolveAction(
  state: HostAppState,
  language: SupportedLanguage
): { label: string; icon: string; enabled: boolean; note: string | null } {
  const text = getHostText(language);
  const room = state.room;
  const players = room?.players ?? [];
  const selected = room?.availableGames.find((game) => game.id === room.selectedGameId);

  if (hasActiveRound(room)) {
    return { label: text.shellRoundRunning, icon: "play", enabled: false, note: null };
  }

  if (!selected) {
    return {
      label: text.shellStartRound,
      icon: "play",
      enabled: false,
      note: text.noActiveGameSelectLine
    };
  }

  const minimumPlayers = resolveGameMinPlayers(selected, room?.selectedGameSettings);
  const missing = minimumPlayers - players.length;

  if (missing > 0) {
    return {
      label: text.shellStartRound,
      icon: "play",
      enabled: false,
      note: text.shellNeedsPlayers(missing)
    };
  }

  if (requiresReadyAutoStart(selected)) {
    const ready = players.filter((player) => player.isReady).length;

    return {
      label: text.shellReadyCount(ready, players.length),
      icon: "check",
      enabled: false,
      note: text.autoReadyLine
    };
  }

  return { label: text.shellStartRound, icon: "play", enabled: true, note: null };
}

export function mountHostShell(client: HostSocketClient): () => void {
  installShellStyles();
  const requestedGame = new URLSearchParams(window.location.search).get("game");
  const gameLocked = Boolean(requestedGame);
  let requestedGameApplied = false;

  const root = document.createElement("div");
  root.className = `opl-shell${gameLocked ? " is-game-locked" : ""}`;
  root.hidden = true;
  root.innerHTML = `
    <nav class="opl-dock" aria-label="Host">
      <span class="opl-brand">V+</span>
      <span class="opl-dock-rule"></span>
      <button type="button" class="opl-dock-button" data-action="show-catalog" aria-pressed="true"
              data-dock="catalog">${renderUiIcon("catalog", 21)}</button>
      <span class="opl-dock-spacer"></span>
      <button type="button" class="opl-dock-button" data-action="toggle-fullscreen"
              data-dock="fullscreen">${renderUiIcon("fullscreen", 21)}</button>
      <button type="button" class="opl-dock-button" data-action="toggle-theme"
              data-dock="theme">${renderUiIcon("theme", 21)}</button>
      <button type="button" class="opl-dock-button" data-action="open-settings"
              data-dock="settings">${renderUiIcon("settings", 21)}</button>
    </nav>
    <main class="opl-main">
      <p class="opl-kicker" data-section="kicker"></p>
      <h1 class="opl-title" data-section="title"></h1>
      <div class="opl-board" data-dpad-scroll-root="main">
        <div data-section="open"></div>
        <div class="opl-catalog" data-section="catalog"></div>
      </div>
    </main>
    <aside class="opl-rail">
      <div data-section="room"></div>
      <p class="opl-rail-label" data-section="roster-label"></p>
      <div class="opl-roster" data-section="roster"></div>
      <button type="button" class="opl-start" data-action="start-round" data-section="start"
              data-dpad-key="start"></button>
      <p class="opl-note" data-section="note"></p>
    </aside>
    <div class="opl-tv-lobby" data-section="tv-lobby" hidden>
      <aside class="tv-panel tv-panel-left" data-section="tv-left" data-dpad-zone="left"></aside>
      <main class="tv-panel tv-panel-center" data-dpad-zone="center">
        <header class="tv-center-head">
          <h2>JUGADORES <span class="tv-player-count" data-section="tv-count"></span></h2>
        </header>
        <div class="tv-slots-wrap">
          <div class="tv-bubbles" data-section="tv-slots"></div>
          <div class="tv-empty" data-section="tv-empty"></div>
        </div>
        <footer class="tv-action-footer" data-section="tv-footer"></footer>
        <div class="tv-rules-row">
          <button type="button" class="tv-rules-button" data-action="open-tv-rules"
                  data-dpad-key="rules-button">? Cómo se juega</button>
          <span class="tv-rules-tip">Cada voto vale 100 puntos.</span>
        </div>
      </main>
      <aside class="tv-panel tv-panel-right" data-section="tv-right" data-dpad-zone="right"></aside>
    </div>
    <div class="tv-rules-modal" data-section="tv-rules" hidden>
      <div class="tv-rules-card" data-dpad-zone="rules" role="dialog" aria-modal="true"
           aria-labelledby="tv-rules-title">
        <header class="tv-rules-head">
          <h2 id="tv-rules-title">CÓMO SE JUEGA</h2>
          <button type="button" class="tv-rules-close" data-action="close-tv-rules"
                  data-dpad-key="rules-close" data-dpad-autofocus>Cerrar</button>
        </header>
        <div class="tv-rules-body" data-section="tv-rules-body"></div>
      </div>
    </div>
  `;
  document.body.appendChild(root);

  const query = <T extends HTMLElement>(section: string): T =>
    root.querySelector<T>(`[data-section="${section}"]`) as T;

  const nodes = {
    kicker: query("kicker"),
    title: query("title"),
    open: query("open"),
    catalog: query("catalog"),
    room: query("room"),
    rosterLabel: query("roster-label"),
    roster: query("roster"),
    start: query<HTMLButtonElement>("start"),
    note: query("note"),
    tvLobby: query<HTMLDivElement>("tv-lobby"),
    tvLeft: query("tv-left"),
    tvRight: query("tv-right"),
    tvSlots: query("tv-slots"),
    tvEmpty: query("tv-empty"),
    tvFooter: query("tv-footer"),
    tvCount: query("tv-count"),
    tvRules: query<HTMLDivElement>("tv-rules"),
    tvRulesBody: query("tv-rules-body")
  };
  const rail = root.querySelector<HTMLElement>(".opl-rail") as HTMLElement;
  const dockButtons = {
    catalog: root.querySelector<HTMLButtonElement>('[data-dock="catalog"]') as HTMLButtonElement,
    fullscreen: root.querySelector<HTMLButtonElement>('[data-dock="fullscreen"]') as HTMLButtonElement,
    theme: root.querySelector<HTMLButtonElement>('[data-dock="theme"]') as HTMLButtonElement,
    settings: root.querySelector<HTMLButtonElement>('[data-dock="settings"]') as HTMLButtonElement
  };

  function syncDockLabels(text: ReturnType<typeof getHostText>, theme: ThemeName): void {
    const labels = {
      catalog: text.shellBackToCatalog,
      fullscreen: document.fullscreenElement ? text.exitFullscreen : text.fullscreen,
      theme: `${text.themeLabel}: ${theme === "dark" ? text.themeLight : text.themeDark}`,
      settings: text.shellSetupKicker
    };

    for (const [key, label] of Object.entries(labels) as Array<[keyof typeof dockButtons, string]>) {
      dockButtons[key].setAttribute("aria-label", label);
      dockButtons[key].title = label;
    }
    dockButtons.catalog.hidden = gameLocked;
  }

  let signatures = { ...emptySignatures };
  let appliedTheme: string | null = null;
  let qrDataUrl: string | null = null;
  let qrSourceUrl: string | null = null;
  let visible = false;
  let focusPending = false;
  let destroyed = false;
  let tvMode = false;
  let tvLeftSignature = "";
  let tvSlotsSignature = "";
  let tvRoomSignature = "";
  let tvEmptySignature = "";
  let tvRulesSignature = "";
  let tvRulesOpen = false;

  function setTvRulesOpen(open: boolean, focus = true): void {
    if (open === tvRulesOpen) return;
    tvRulesOpen = open;
    nodes.tvRules.hidden = !open;
    nodes.tvLobby.querySelectorAll<HTMLElement>(".tv-panel").forEach((panel) => {
      if (open) panel.setAttribute("inert", "");
      else panel.removeAttribute("inert");
    });
    if (!focus) return;
    requestAnimationFrame(() => {
      if (open) {
        focusDpad('[data-action="close-tv-rules"]');
      } else if (tvMode) {
        focusDpad('[data-action="open-tv-rules"]');
      }
    });
  }

  function setTvMode(enabled: boolean): void {
    if (enabled === tvMode) return;
    tvMode = enabled;
    root.classList.toggle("is-tv-lobby", enabled);
    nodes.tvLobby.hidden = !enabled;
    if (!enabled && tvRulesOpen) setTvRulesOpen(false, false);
    tvLeftSignature = "";
    tvSlotsSignature = "";
    tvRoomSignature = "";
    tvEmptySignature = "";
    tvRulesSignature = "";
    if (enabled) {
      nodes.tvFooter.append(nodes.start, nodes.note);
    } else {
      nodes.start.classList.remove("is-ready");
      rail.append(nodes.start, nodes.note);
    }
  }

  /**
   * The QR bitmap is regenerated only when the join URL changes.
   *
   * A state update arrives several times a second; re-encoding a QR code that
   * often would be pure waste.
   *
   * The code deliberately does not follow the theme. An inverted QR — light
   * modules on a dark field — is rejected by a good share of phone scanners,
   * and a code nobody can scan defeats the point of putting it on the wall. It
   * keeps the warm paper palette in both themes and sits on its own light plate.
   */
  function ensureQrCode(joinUrl: string): void {
    if (!joinUrl || joinUrl === qrSourceUrl) {
      return;
    }

    qrSourceUrl = joinUrl;
    void QRCode.toDataURL(joinUrl, {
      margin: 1,
      width: 264,
      errorCorrectionLevel: "M",
      color: {
        dark: themes.light.color.text,
        light: themes.light.color.surfaceRaised
      }
    })
      .then((dataUrl) => {
        if (destroyed) {
          return;
        }

        qrDataUrl = dataUrl;
        signatures.room = "";
        render(client.getState());
      })
      .catch(() => {
        qrSourceUrl = null;
      });
  }

  function render(state: HostAppState): void {
    const room = state.room;

    if (!room) {
      return;
    }

    if (!requestedGameApplied && requestedGame) {
      const exists = room.availableGames.some((game) => game.id === requestedGame);
      if (exists) {
        requestedGameApplied = true;
        client.selectGame(requestedGame);
      } else if (room.availableGames.length > 0) {
        requestedGameApplied = true;
      }
    }

    const language = room.language ?? state.preferredLanguage;
    const text = getHostText(language);
    const theme = normalizeThemeName(room.theme);

    syncDockLabels(text, theme);

    if (theme !== appliedTheme) {
      applyThemeVariables(root, theme);
      appliedTheme = theme;
    }

    const selected = room.availableGames.find((game) => game.id === room.selectedGameId);
    const rail = toRailOptions(state, qrDataUrl);
    const action = resolveAction(state, language);
    const locked = hasActiveRound(room);
    const useTvLobby = Boolean(selected);

    setTvMode(useTvLobby);
    if (!useTvLobby) ensureQrCode(room.joinUrl);

    const headSignature = `${language}:${selected?.displayName ?? ""}`;

    if (headSignature !== signatures.head) {
      signatures.head = headSignature;
      nodes.kicker.textContent = selected
        ? (gameLocked ? "SALA DE JUEGO" : text.shellSetupKicker)
        : text.shellKicker;
      nodes.title.textContent = selected ? selected.displayName : text.shellTitle;
    }

    const catalogSignature = [
      language,
      room.selectedGameId ?? "",
      locked ? "1" : "0",
      room.availableGames.map((game) => game.id).join(","),
      JSON.stringify(room.selectedGameSettings ?? {})
    ].join("|");

    if (catalogSignature !== signatures.catalog) {
      signatures.catalog = catalogSignature;
      nodes.open.innerHTML = renderOpenGame(
        selected,
        room.selectedGameSettings ?? {},
        language,
        locked,
        !gameLocked
      );
      // One thing at a time: choosing, or setting up what was chosen. The shelf
      // behind an open setup card was only a way to lose your place, and the
      // dock's catalog button is the way back.
      nodes.catalog.hidden = Boolean(selected) || gameLocked;
      nodes.catalog.innerHTML = selected || gameLocked
        ? ""
        : renderCatalog({
            games: room.availableGames,
            selectedGameId: room.selectedGameId,
            settings: room.selectedGameSettings ?? {},
            language,
            locked
          });
    }

    const roomSignature = `${room.code}|${qrDataUrl ? "1" : "0"}|${language}`;

    if (roomSignature !== signatures.room) {
      signatures.room = roomSignature;
      nodes.room.innerHTML = renderRoomCard(rail);
    }

    const rosterSignature = [
      language,
      rail.canKick ? "1" : "0",
      rail.hostControlHolderId ?? "",
      rail.hostControlRequestId ?? "",
      room.players.map(describePlayer).join(";")
    ].join("|");

    if (rosterSignature !== signatures.roster) {
      signatures.roster = rosterSignature;
      nodes.rosterLabel.innerHTML = `<span>${escapeHtml(text.players)}</span><span>${
        room.players.length
      }</span>`;
      nodes.roster.innerHTML = renderRoster(rail);
    }

    const actionSignature = `${action.label}|${action.enabled ? "1" : "0"}|${
      action.note ?? ""
    }|${state.error ?? ""}`;

    if (actionSignature !== signatures.action) {
      signatures.action = actionSignature;
      nodes.start.innerHTML = `${renderUiIcon(action.icon as "play", 18)}<span>${escapeHtml(
        action.label
      )}</span>`;
      // Keep the action focusable even when it cannot start yet. On a TV the
      // player needs to reach it with the D-pad and read the note; a disabled
      // button would silently disappear from the focus order.
      nodes.start.disabled = false;
      nodes.start.tabIndex = 0;
      nodes.start.setAttribute("aria-disabled", action.enabled ? "false" : "true");
      nodes.start.classList.toggle("is-disabled", !action.enabled);

      const note = state.error ?? action.note;
      nodes.note.textContent = note ?? "";
      nodes.note.classList.toggle("is-error", Boolean(state.error));
      nodes.note.style.display = note ? "block" : "none";
    }

    if (useTvLobby && selected) {
      nodes.start.innerHTML = `${renderUiIcon("play", 18)}<span>Empezar partida</span>`;
      const readyMatch = /^(\d+)\s+de\s+(\d+)\s+listos$/i.exec(action.label);
      if (readyMatch && !state.error) {
        nodes.note.textContent = `${readyMatch[1]} de ${readyMatch[2]} listos`;
        nodes.note.style.display = "block";
      }
      const settings = room.selectedGameSettings ?? {};
      const minPlayers = resolveGameMinPlayers(selected, settings);
      nodes.start.classList.toggle(
        "is-ready",
        action.enabled || room.players.length >= minPlayers
      );
      const tvOptions = {
        game: selected,
        settings,
        language,
        room,
        qrDataUrl,
        action,
        canKick: rail.canKick,
        hostControlHolderId: rail.hostControlHolderId,
        hostControlRequestId: rail.hostControlRequestId,
        locked
      };

      const leftSignature = [selected.id, language, locked ? "1" : "0", JSON.stringify(settings)].join("|");
      if (leftSignature !== tvLeftSignature) {
        tvLeftSignature = leftSignature;
        nodes.tvLeft.innerHTML = renderTvLeft(tvOptions);
      }

      nodes.tvCount.textContent = `(${room.players.length} / ${Math.min(
        12,
        Math.max(1, selected.maxPlayers)
      )})`;

      const emptySignature = language;
      if (emptySignature !== tvEmptySignature) {
        tvEmptySignature = emptySignature;
        nodes.tvEmpty.innerHTML = renderTvEmpty(language);
      }
      nodes.tvEmpty.hidden = room.players.length > 0;

      const slotsSignature = [
        language,
        rail.canKick ? "1" : "0",
        rail.hostControlHolderId ?? "",
        rail.hostControlRequestId ?? "",
        room.players.map(describePlayer).join(";")
      ].join("|");
      if (slotsSignature !== tvSlotsSignature) {
        tvSlotsSignature = slotsSignature;
        nodes.tvSlots.innerHTML = renderTvSlots(tvOptions);
      }

      const tvRoomKey = [room.code, qrDataUrl ? "1" : "0", language, room.joinUrl].join("|");
      if (tvRoomKey !== tvRoomSignature) {
        tvRoomSignature = tvRoomKey;
        nodes.tvRight.innerHTML = renderTvRoom(tvOptions);
        const qrCanvas = nodes.tvRight.querySelector<HTMLCanvasElement>("canvas[data-qr-canvas]");
        if (qrCanvas) {
          void QRCode.toCanvas(qrCanvas, room.joinUrl, {
            margin: 1,
            width: 264,
            errorCorrectionLevel: "M",
            color: {
              dark: themes.light.color.text,
              light: themes.light.color.surfaceRaised
            }
          }).catch(() => undefined);
        }
      }

      const tvRulesKey = `${selected.id}|${language}`;
      if (tvRulesKey !== tvRulesSignature) {
        tvRulesSignature = tvRulesKey;
        nodes.tvRulesBody.innerHTML = renderTvRules(selected, language);
      }
    }

    // Re-renders replace innerHTML for the rail and roster several times a
    // second. Restore the D-pad cursor by stable key so first paint and live
    // updates never leave the player on <body>.
    requestAnimationFrame(() => {
      if (!visible) return;
      if (focusPending) {
        focusPending = false;
        if (nodes.start.getAttribute("aria-disabled") !== "true" && focusDpad('[data-dpad-key="start"]')) {
          return;
        }
        if (focusDpad('[data-dpad-key="room-card"]')) return;
      }
      restoreDpadFocus(root);
    });
  }

  function setVisible(next: boolean): void {
    if (next === visible) {
      return;
    }

    const entering = next && !visible;
    visible = next;
    root.hidden = !next;
    document.documentElement.setAttribute(SHELL_ACTIVE_ATTRIBUTE, next ? "on" : "off");
    if (entering) focusPending = true;

    if (!next) {
      if (tvRulesOpen) setTvRulesOpen(false, false);
      // Force a full repaint on the way back in: the room may have changed
      // language, theme or catalog while a game held the screen.
      signatures = { ...emptySignatures };
      appliedTheme = null;
    }
  }

  nodes.tvRules.addEventListener("click", (event) => {
    if (event.target === nodes.tvRules) setTvRulesOpen(false);
  });

  root.addEventListener("click", (event) => {
    const target = event.target as HTMLElement | null;
    const trigger = target?.closest<HTMLElement>("[data-action]");

    if (!trigger) {
      return;
    }

    const state = client.getState();
    const gameId = state.room?.selectedGameId ?? null;

    switch (trigger.dataset.action) {
      case "select-game": {
        if (gameLocked) break;
        const id = trigger.dataset.gameId;
        if (id) {
          client.selectGame(id);
        }
        break;
      }
      case "back":
        if (!gameLocked) client.returnToGameSelection();
        break;
      case "start-round":
        if (trigger.getAttribute("aria-disabled") === "true") break;
        client.startRound();
        break;
      case "kick": {
        const playerId = trigger.dataset.playerId;
        if (playerId) {
          client.kickPlayer(playerId);
        }
        break;
      }
      case "setup-select":
      case "setup-number": {
        const actionKey = trigger.dataset.actionKey;
        const raw = trigger.dataset.value;
        if (gameId && actionKey && raw !== undefined) {
          const value = trigger.dataset.action === "setup-number" ? Number(raw) : raw;
          client.sendGameHostAction(gameId, { type: "configure-lobby", [actionKey]: value });
        }
        break;
      }
      case "setup-confirm": {
        const actionType = trigger.dataset.actionType;
        if (gameId && actionType) {
          client.sendGameHostAction(gameId, { type: actionType });
        }
        break;
      }
      case "show-catalog":
        if (!gameLocked) client.returnToGameSelection();
        break;
      case "toggle-theme":
        client.setTheme(normalizeThemeName(state.room?.theme) === "dark" ? "light" : "dark");
        break;
      case "open-settings":
        window.dispatchEvent(new CustomEvent(OPEN_HOST_CONTROLS_EVENT));
        break;
      case "open-tv-rules":
        setTvRulesOpen(true);
        break;
      case "close-tv-rules":
        setTvRulesOpen(false);
        break;
      case "toggle-fullscreen":
        window.dispatchEvent(new CustomEvent(TOGGLE_FULLSCREEN_EVENT));
        break;
      default:
        break;
    }
  });

  /**
   * Keyboard shortcuts, previously bound to the Phaser scenes.
   *
   * They are ignored while the shell is hidden so a game keeps its own keys,
   * and while a text field has focus so typing never starts a round.
   */
  const handleKeyDown = (event: KeyboardEvent): void => {
    if (!visible || event.metaKey || event.ctrlKey || event.altKey) {
      return;
    }

    const active = document.activeElement;

    if (active instanceof HTMLInputElement || active instanceof HTMLTextAreaElement) {
      return;
    }

    if (event.code === "Space") {
      event.preventDefault();
      if (nodes.start.getAttribute("aria-disabled") !== "true") {
        client.startRound();
      }
      return;
    }

    const digit = /^Digit([1-9])$/.exec(event.code);

    if (digit && !gameLocked) {
      const game = client.getState().room?.availableGames[Number(digit[1]) - 1];

      if (game) {
        client.selectGame(game.id);
      }
    }
  };

  window.addEventListener("keydown", handleKeyDown);
  const handleFullscreenChange = (): void => {
    if (visible) {
      render(client.getState());
    }
  };
  document.addEventListener("fullscreenchange", handleFullscreenChange);

  const unsubscribe = client.subscribe((state) => {
    if (destroyed) {
      return;
    }

    setVisible(shouldShowShell(state));

    if (visible) {
      render(state);
    }
  });

  return () => {
    destroyed = true;
    unsubscribe();
    window.removeEventListener("keydown", handleKeyDown);
    document.removeEventListener("fullscreenchange", handleFullscreenChange);
    document.documentElement.removeAttribute(SHELL_ACTIVE_ATTRIBUTE);
    root.remove();
  };
}

/**
 * The shell owns the screen whenever no game does.
 *
 * This deliberately defers to the same resolver the router uses instead of
 * asking "is a round running": a finished round still belongs to the game,
 * which is showing its own result screen.
 */
export function shouldShowShell(state: HostAppState): boolean {
  // `game:state` puede llegar un instante antes que el snapshot de la sala
  // con `currentRound`; si ya hay estado de juego, el lobby ya no debe tapar
  // la escena del juego.
  return resolveHostSurface(state).kind === "shell" && !state.game;
}
