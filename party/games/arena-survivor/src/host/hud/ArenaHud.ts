import type { ArenaSurvivorPlayerState, ArenaSurvivorState } from "../../protocol.js";
import {
  resolveArenaSurvivorCharacterPortraitPath,
  resolveArenaSurvivorShopIconPath
} from "../../visualThemes.js";

interface RoomSnapshot {
  language?: "de" | "en";
  players?: Array<{
    id: string;
    name: string;
    isReady?: boolean;
  }>;
}

/**
 * The HUD's own ink, deliberately not the platform theme.
 *
 * This overlay is painted on the arena itself — dark in every one of the game's
 * visual themes, and the backgrounds below are fixed artwork rather than
 * surfaces that follow the room. A pass that swapped literals for platform
 * tokens caught these too, and in the light theme that turned the wave report's
 * stat labels, the material badge and the HP readout into dark text on a
 * near-black card: present, but invisible. The card decides the ink, not the
 * room, so these stay fixed.
 */
const hudInk = {
  /** Base colour for everything the overlay's children inherit. */
  text: "#f8fafc",
  /** Secondary labels on the dark cards. */
  textSoft: "#cbd5e1",
  /** Hints and empty states. */
  muted: "#94a3b8",
  /** Unfilled part of the XP and HP tracks. */
  track: "#1e293b"
} as const;

const hostTheme = {
  bodyFont: "Inter, system-ui, sans-serif",
  text: hudInk.text
};

function formatRoundedHp(value: number): string {
  return `${Math.max(0, Math.round(value))}`;
}

function formatPercent(value: number): string {
  const safeValue = Math.max(0, value);
  return `${safeValue >= 10 ? Math.round(safeValue) : Math.round(safeValue * 10) / 10}%`;
}

function formatPlayerStats(player: ArenaSurvivorPlayerState): string {
  const stats = player.stats;

  return [
    `Atk ${Math.round(stats.attackSpeedMultiplier * 100)}%`,
    `HP ${formatRoundedHp(stats.maxHp)}`,
    `Dmg ${Math.round(stats.projectileDamageMultiplier * 100)}%`,
    `LS ${formatPercent(stats.lifeStealPct)}`,
    `Regen ${Math.round(stats.hpRegen * 10) / 10}`,
    `Armor ${Math.round(stats.armor * 10) / 10}`
  ].join("  |  ");
}

const cornerAnchors = [
  { horizontal: "left", vertical: "top" },
  { horizontal: "right", vertical: "top" },
  { horizontal: "left", vertical: "bottom" },
  { horizontal: "right", vertical: "bottom" }
] as const;

const topInset = "18px";
const sideInset = "18px";
const bottomInset = "18px";

function applyPanelChrome(element: HTMLElement): void {
  element.style.borderRadius = "18px";
  element.style.background = "rgba(15, 23, 42, 0.84)";
  element.style.border = "1px solid rgba(148, 163, 184, 0.22)";
  element.style.boxShadow = "0 14px 28px rgba(2, 6, 23, 0.28)";
  element.style.backdropFilter = "blur(10px)";
  element.style.pointerEvents = "none";
}

export interface ArenaHud {
  update(state: ArenaSurvivorState | null, room?: RoomSnapshot | null): void;
  destroy(): void;
}

export interface ArenaHudActions {
  onRestartRun(): void;
  onReturnToSetup(): void;
}

export function createArenaHud(actions: ArenaHudActions): ArenaHud {
  const overlay = document.createElement("div");
  overlay.style.position = "fixed";
  overlay.style.inset = "0";
  overlay.style.zIndex = "46";
  overlay.style.pointerEvents = "none";
  overlay.style.fontFamily = `"${hostTheme.bodyFont}", sans-serif`;
  overlay.style.color = hostTheme.text;

  const metaBar = document.createElement("div");
  metaBar.style.position = "absolute";
  metaBar.style.left = "50%";
  metaBar.style.top = "14px";
  metaBar.style.transform = "translateX(-50%)";
  metaBar.style.padding = "6px 10px";
  metaBar.style.width = "min(300px, 36vw)";
  metaBar.style.display = "grid";
  metaBar.style.gap = "5px";
  metaBar.style.fontSize = "13px";
  metaBar.style.fontWeight = "800";
  metaBar.style.letterSpacing = "0.01em";
  metaBar.style.whiteSpace = "nowrap";
  metaBar.style.textAlign = "center";
  applyPanelChrome(metaBar);

  const metaText = document.createElement("div");

  const roundProgressTrack = document.createElement("div");
  roundProgressTrack.style.height = "4px";
  roundProgressTrack.style.borderRadius = "999px";
  roundProgressTrack.style.background = "rgba(51, 65, 85, 0.9)";
  roundProgressTrack.style.overflow = "hidden";

  const roundProgressFill = document.createElement("div");
  roundProgressFill.style.height = "100%";
  roundProgressFill.style.width = "0%";
  roundProgressFill.style.borderRadius = "999px";
  roundProgressFill.style.background = "linear-gradient(90deg, #38bdf8, #22c55e)";
  roundProgressFill.style.transition = "width 120ms linear";
  roundProgressTrack.appendChild(roundProgressFill);
  metaBar.appendChild(metaText);
  metaBar.appendChild(roundProgressTrack);

  const playerCards = cornerAnchors.map((anchor) => {
    const card = document.createElement("section");
    card.style.position = "absolute";
    card.style.width = "min(150px, calc(50vw - 32px))";
    card.style.padding = "8px 10px 9px";
    card.style.display = "grid";
    card.style.gap = "4px";
    card.style.visibility = "hidden";
    applyPanelChrome(card);

    if (anchor.horizontal === "left") {
      card.style.left = sideInset;
    } else {
      card.style.right = sideInset;
    }

    if (anchor.vertical === "top") {
      card.style.top = topInset;
    } else {
      card.style.bottom = bottomInset;
    }

    const titleRow = document.createElement("div");
    titleRow.style.display = "flex";
    titleRow.style.justifyContent = "space-between";
    titleRow.style.alignItems = "center";
    titleRow.style.gap = "6px";

    const title = document.createElement("div");
    title.style.fontSize = "15px";
    title.style.fontWeight = "900";
    title.style.lineHeight = "1.2";
    title.style.overflow = "hidden";
    title.style.textOverflow = "ellipsis";
    title.style.whiteSpace = "nowrap";

    const materialBadge = document.createElement("div");
    materialBadge.style.fontSize = "11px";
    materialBadge.style.fontWeight = "800";
    materialBadge.style.color = hudInk.textSoft;
    materialBadge.style.padding = "2px 7px";
    materialBadge.style.borderRadius = "999px";
    materialBadge.style.background = "rgba(30, 41, 59, 0.88)";
    materialBadge.style.border = "1px solid rgba(148, 163, 184, 0.16)";
    materialBadge.style.flexShrink = "0";

    titleRow.appendChild(title);
    titleRow.appendChild(materialBadge);

    const progressionRow = document.createElement("div");
    progressionRow.style.display = "grid";
    progressionRow.style.gridTemplateColumns = "1fr auto";
    progressionRow.style.alignItems = "center";
    progressionRow.style.gap = "5px";

    const levelBadge = document.createElement("div");
    levelBadge.style.padding = "2px 5px";
    levelBadge.style.borderRadius = "8px";
    levelBadge.style.background = "rgba(14, 116, 144, 0.72)";
    levelBadge.style.fontSize = "11px";
    levelBadge.style.fontWeight = "900";

    const xpTrack = document.createElement("div");
    xpTrack.style.position = "relative";
    xpTrack.style.height = "14px";
    xpTrack.style.borderRadius = "999px";
    xpTrack.style.background = hudInk.track;
    xpTrack.style.overflow = "hidden";

    const xpFill = document.createElement("div");
    xpFill.style.height = "100%";
    xpFill.style.width = "0%";
    xpFill.style.borderRadius = "999px";
    xpFill.style.background = "linear-gradient(90deg, #0ea5e9, #22d3ee)";
    xpFill.style.transition = "width 120ms linear";

    const xpLabel = document.createElement("div");
    xpLabel.style.position = "absolute";
    xpLabel.style.inset = "0";
    xpLabel.style.display = "flex";
    xpLabel.style.alignItems = "center";
    xpLabel.style.justifyContent = "center";
    xpLabel.style.fontSize = "9px";
    xpLabel.style.fontWeight = "900";
    xpLabel.style.textShadow = "0 1px 2px rgba(2, 6, 23, 0.9)";

    xpTrack.appendChild(xpFill);
    xpTrack.appendChild(xpLabel);
    progressionRow.appendChild(xpTrack);
    progressionRow.appendChild(levelBadge);

    const hpRow = document.createElement("div");
    hpRow.style.display = "grid";

    const hpLabel = document.createElement("div");
    hpLabel.style.position = "absolute";
    hpLabel.style.inset = "0";
    hpLabel.style.display = "flex";
    hpLabel.style.alignItems = "center";
    hpLabel.style.justifyContent = "center";
    hpLabel.style.fontSize = "11px";
    hpLabel.style.fontWeight = "800";
    hpLabel.style.color = hudInk.textSoft;
    hpLabel.style.textShadow = "0 1px 2px rgba(2, 6, 23, 0.85)";

    const hpTrack = document.createElement("div");
    hpTrack.style.position = "relative";
    hpTrack.style.height = "13px";
    hpTrack.style.borderRadius = "999px";
    hpTrack.style.background = hudInk.track;
    hpTrack.style.overflow = "hidden";

    const hpFill = document.createElement("div");
    hpFill.style.height = "100%";
    hpFill.style.width = "0%";
    hpFill.style.borderRadius = "999px";
    hpFill.style.background = "#22c55e";
    hpFill.style.transition = "width 120ms linear";
    hpTrack.appendChild(hpFill);
    hpTrack.appendChild(hpLabel);

    hpRow.appendChild(hpTrack);

    card.appendChild(titleRow);
    card.appendChild(hpRow);
    card.appendChild(progressionRow);

    overlay.appendChild(card);

    return {
      card,
      title,
      materialBadge,
      levelBadge,
      xpFill,
      xpLabel,
      hpLabel,
      hpFill
    };
  });

  const summaryCard = document.createElement("section");
  summaryCard.style.position = "absolute";
  summaryCard.style.left = "50%";
  summaryCard.style.top = "50%";
  summaryCard.style.transform = "translate(-50%, -50%)";
  summaryCard.style.width = "min(1080px, calc(100vw - 44px))";
  summaryCard.style.maxHeight = "min(86vh, 760px)";
  summaryCard.style.overflow = "auto";
  summaryCard.style.padding = "24px clamp(18px, 3vw, 34px)";
  summaryCard.style.display = "none";
  summaryCard.style.gridTemplateColumns = "1fr";
  summaryCard.style.gap = "16px";
  applyPanelChrome(summaryCard);
  summaryCard.style.background = "rgba(2, 6, 23, 0.9)";
  summaryCard.style.zIndex = "48";

  const summaryEyebrow = document.createElement("div");
  summaryEyebrow.style.fontSize = "12px";
  summaryEyebrow.style.fontWeight = "900";
  summaryEyebrow.style.letterSpacing = "0.18em";
  summaryEyebrow.style.textTransform = "uppercase";
  summaryEyebrow.style.textAlign = "center";

  const summaryTitle = document.createElement("div");
  summaryTitle.style.fontSize = "clamp(24px, 3vw, 38px)";
  summaryTitle.style.fontWeight = "900";
  summaryTitle.style.textAlign = "center";

  const summaryBody = document.createElement("div");
  summaryBody.style.display = "grid";
  summaryBody.style.gap = "12px";

  const summaryActions = document.createElement("div");
  summaryActions.style.display = "none";
  summaryActions.style.gridTemplateColumns = "repeat(2, minmax(180px, 260px))";
  summaryActions.style.justifyContent = "center";
  summaryActions.style.gap = "12px";
  summaryActions.style.pointerEvents = "auto";

  function createSummaryButton(primary: boolean): HTMLButtonElement {
    const button = document.createElement("button");
    button.type = "button";
    button.style.minHeight = "48px";
    button.style.padding = "10px 18px";
    button.style.borderRadius = "14px";
    button.style.border = primary
      ? "1px solid rgba(255,255,255,0.26)"
      : "1px solid rgba(148,163,184,0.34)";
    button.style.background = primary ? "#ea580c" : "rgba(15, 23, 42, 0.78)";
    button.style.color = "#fff7ed";
    button.style.font = `900 15px ${hostTheme.bodyFont}`;
    button.style.cursor = "pointer";
    button.style.boxShadow = primary ? "0 10px 28px rgba(234, 88, 12, 0.28)" : "none";
    button.style.transition = "transform 140ms ease, filter 140ms ease";
    button.addEventListener("pointerenter", () => {
      if (!button.disabled) {
        button.style.transform = "translateY(-2px)";
        button.style.filter = "brightness(1.08)";
      }
    });
    button.addEventListener("pointerleave", () => {
      button.style.transform = "translateY(0)";
      button.style.filter = "none";
    });
    return button;
  }

  const restartButton = createSummaryButton(true);
  const setupButton = createSummaryButton(false);
  let actionPending = false;
  restartButton.addEventListener("click", () => {
    if (actionPending) return;
    actionPending = true;
    restartButton.disabled = true;
    setupButton.disabled = true;
    actions.onRestartRun();
  });
  setupButton.addEventListener("click", () => {
    if (actionPending) return;
    actionPending = true;
    restartButton.disabled = true;
    setupButton.disabled = true;
    actions.onReturnToSetup();
  });
  summaryActions.appendChild(restartButton);
  summaryActions.appendChild(setupButton);

  summaryCard.appendChild(summaryEyebrow);
  summaryCard.appendChild(summaryTitle);
  summaryCard.appendChild(summaryBody);
  summaryCard.appendChild(summaryActions);

  overlay.appendChild(metaBar);
  overlay.appendChild(summaryCard);
  document.body.appendChild(overlay);
  let lastSummarySignature = "";
  let lastRunningSignature: string | null = null;

  /**
   * Everything the running HUD shows, as rounded display values.
   *
   * The HUD gets every host state (~31 Hz), but what it prints changes a few
   * times per second at most. Writing ~40 styles and texts per update anyway
   * made the browser recalculate style and layout of the overlay each time.
   */
  function buildRunningSignature(state: ArenaSurvivorState, room: RoomSnapshot | null): string {
    const roundDurationMs = state.elapsedMs + state.remainingMs;
    const progress = roundDurationMs > 0 ? Math.round((state.elapsedMs / roundDurationMs) * 100) : 100;
    let signature = `${room?.language ?? ""}|${state.visualTheme}|${state.waveNumber}|${progress}`;

    for (const player of state.players) {
      const hpRatio = player.maxHp > 0 ? player.hp / player.maxHp : 0;
      const hpPercent = Math.round(hpRatio * 100);
      const hpBand = hpRatio > 0.5 ? "g" : hpRatio > 0.25 ? "o" : "r";
      const xpPercent = player.experienceToNextLevel > 0
        ? Math.round((player.experience / player.experienceToNextLevel) * 100)
        : 0;
      signature += `|${player.name}:${player.character.name}:${player.color}:${player.materials}:${player.level}` +
        `:${Math.round(player.experience)}:${Math.round(player.experienceToNextLevel)}:${xpPercent}` +
        `:${formatRoundedHp(player.hp)}:${formatRoundedHp(player.maxHp)}:${hpPercent}:${hpBand}`;
    }

    return signature;
  }

  function update(state: ArenaSurvivorState | null, room: RoomSnapshot | null = null): void {
    const runningSignature =
      state && state.result.outcome === "running" ? buildRunningSignature(state, room) : null;

    if (runningSignature !== null && runningSignature === lastRunningSignature) {
      return;
    }

    lastRunningSignature = runningSignature;
    const en = room?.language === "en";
    metaBar.style.display = state?.survival ? "none" : "grid";
    const obsidianRelay = state?.visualTheme === "obsidian-relay";
    const frostfireSaga = state?.visualTheme === "frostfire-saga";
    const marshmallowMayhem = state?.visualTheme === "marshmallow-mayhem";
    const runningRound = state?.result.outcome === "running";
    const roundDurationMs = state ? state.elapsedMs + state.remainingMs : 0;
    const roundProgress = state
      ? runningRound && roundDurationMs > 0
        ? Math.max(0, Math.min(1, state.elapsedMs / roundDurationMs))
        : 1
      : 0;

    metaText.textContent = state
      ? `${en ? "Wave" : "Welle"}: ${state.waveNumber}`
      : "Arena Survivor";
    roundProgressFill.style.width = `${Math.round(roundProgress * 100)}%`;
    roundProgressFill.style.background = obsidianRelay
      ? "linear-gradient(90deg, #d97745, #67e8f9)"
      : frostfireSaga
          ? "linear-gradient(90deg, #38bdf8, #fb923c)"
          : marshmallowMayhem
            ? "linear-gradient(90deg, #f59e0b, #ef4444)"
            : "linear-gradient(90deg, #38bdf8, #22c55e)";
    roundProgressTrack.style.opacity = state ? "1" : "0";
    metaBar.style.background = obsidianRelay
      ? "rgba(5, 15, 22, 0.9)"
      : frostfireSaga
          ? "rgba(7, 20, 34, 0.92)"
          : marshmallowMayhem
            ? "rgba(54, 24, 12, 0.9)"
            : "rgba(15, 23, 42, 0.84)";
    metaBar.style.borderColor = obsidianRelay
      ? "rgba(103, 232, 249, 0.42)"
      : frostfireSaga
          ? "rgba(251, 146, 60, 0.52)"
          : marshmallowMayhem
            ? "rgba(251, 191, 36, 0.52)"
            : "rgba(148, 163, 184, 0.22)";
    summaryCard.style.background = obsidianRelay
      ? "rgba(2, 9, 14, 0.94)"
      : frostfireSaga
          ? "rgba(5, 15, 27, 0.95)"
          : marshmallowMayhem
            ? "rgba(45, 20, 10, 0.95)"
            : "rgba(2, 6, 23, 0.9)";

    for (let index = 0; index < playerCards.length; index += 1) {
      const card = playerCards[index];
      const player = state?.players[index];

      if (!player) {
        card.card.style.visibility = "hidden";
        continue;
      }

      const hpRatio = player.maxHp > 0 ? Math.max(0, Math.min(1, player.hp / player.maxHp)) : 0;
      const borderColor = player.color;
      const hpColor = hpRatio > 0.5 ? "#22c55e" : hpRatio > 0.25 ? "#f59e0b" : "#ef4444";
      const experienceRatio = player.experienceToNextLevel > 0
        ? Math.max(0, Math.min(1, player.experience / player.experienceToNextLevel))
        : 0;

      card.card.style.visibility = "visible";
      card.card.style.borderColor = borderColor;
      card.card.style.background = obsidianRelay
        ? "rgba(5, 15, 22, 0.9)"
        : frostfireSaga
            ? "rgba(7, 20, 34, 0.9)"
            : "rgba(15, 23, 42, 0.84)";
      card.title.textContent = player.name;
      card.title.title = `${player.name} (${player.character.name})`;
      card.materialBadge.textContent = state?.survival ? `M ${player.materials} · ◆ ${player.evolutionCores ?? 0}` : `M ${player.materials}`;
      card.levelBadge.textContent = `${en ? "Lvl." : "Lvl."} ${player.level}`;
      card.xpLabel.textContent = `EXP ${Math.round(player.experience)}/${Math.round(player.experienceToNextLevel)}`;
      if (card.xpLabel.parentElement) card.xpLabel.parentElement.style.display = state?.survival ? "none" : "block";
      card.xpFill.style.width = `${Math.round(experienceRatio * 100)}%`;
      card.hpLabel.textContent = state?.survival && !player.alive && player.respawnAtMs !== undefined ? `Respawn ${Math.max(0, Math.ceil((player.respawnAtMs - state.elapsedMs) / 1000))}s` : `${en ? "HP" : "Leben"} ${formatRoundedHp(player.hp)}/${formatRoundedHp(player.maxHp)}`;
      card.hpFill.style.width = `${Math.round(hpRatio * 100)}%`;
      card.hpFill.style.background = hpColor;
    }

    const showSummary = Boolean(state && state.result.outcome !== "running");
    summaryCard.style.display = showSummary ? "grid" : "none";
    metaBar.style.opacity = showSummary ? "0.32" : "1";
    for (const card of playerCards) {
      card.card.style.display = showSummary ? "none" : "grid";
    }

    if (!showSummary || !state) {
      actionPending = false;
      restartButton.disabled = false;
      setupButton.disabled = false;
      lastSummarySignature = "";
      summaryEyebrow.textContent = "";
      summaryTitle.textContent = "";
      summaryBody.replaceChildren();
      summaryActions.style.display = "none";
      return;
    }

    const defeated = state.result.outcome === "defeated";
    const themeAccent = obsidianRelay
      ? "#67e8f9"
      : frostfireSaga
        ? "#fb923c"
        : marshmallowMayhem
          ? "#fbbf24"
          : "#38bdf8";
    const playerSurface = obsidianRelay
      ? "rgba(4, 20, 29, 0.9)"
      : frostfireSaga
        ? "rgba(12, 27, 43, 0.9)"
        : marshmallowMayhem
          ? "rgba(66, 30, 14, 0.9)"
          : "rgba(15, 23, 42, 0.88)";
    summaryEyebrow.textContent = defeated || state.survival
      ? en ? "Run statistics" : "Run-Statistik"
      : en ? "Wave report" : "Wellenbericht";
    summaryEyebrow.style.color = themeAccent;
    summaryTitle.textContent = state.result.title;
    summaryActions.style.display = defeated || state.survival ? "grid" : "none";
    restartButton.textContent = en ? "New run" : "Neuer Run";
    setupButton.textContent = en ? "Back to setup" : "Zurück zum Setup";
    restartButton.style.background = themeAccent;
    restartButton.style.color = obsidianRelay ? "#04141d" : "#2b160c";

    const summarySignature = JSON.stringify({
      outcome: state.result.outcome,
      theme: state.visualTheme,
      wave: state.waveNumber,
      players: state.players.map((player) => [
        player.playerId,
        player.level,
        player.runSummary.totalKills,
        player.runSummary.totalDamageDealt,
        player.loadout.weapons.map((weapon) => `${weapon.weaponId}:${weapon.level}`),
        player.loadout.items.map((item) => `${item.itemId}:${item.level}`)
      ])
    });
    if (summarySignature === lastSummarySignature) {
      return;
    }
    lastSummarySignature = summarySignature;

    const playerSummaries = state.players.map((player, playerIndex) => {
        const card = document.createElement("article");
        card.style.display = "grid";
        card.style.gridTemplateColumns = "clamp(92px, 12vw, 138px) minmax(0, 1fr)";
        card.style.gap = "clamp(12px, 2vw, 22px)";
        card.style.padding = "14px";
        card.style.borderRadius = "18px";
        card.style.background = playerSurface;
        card.style.border = `1px solid ${player.color}`;

        const portrait = document.createElement("img");
        portrait.src = resolveArenaSurvivorCharacterPortraitPath(player.character.id, state.visualTheme);
        portrait.alt = player.character.name;
        portrait.style.width = "100%";
        portrait.style.aspectRatio = "1";
        portrait.style.objectFit = "contain";
        portrait.style.alignSelf = "center";
        portrait.style.filter = "drop-shadow(0 10px 14px rgba(0,0,0,0.28))";

        const content = document.createElement("div");
        content.style.display = "grid";
        content.style.gap = "10px";
        content.style.minWidth = "0";

        const title = document.createElement("div");
        title.style.fontSize = "18px";
        title.style.fontWeight = "900";
        title.textContent = `${player.name} · ${player.character.name}`;

        const survivedSeconds = Math.max(0.001, player.runSummary.totalSurvivedMs / 1000);
        const damagePerSecond = player.runSummary.totalDamageDealt / survivedSeconds;
        const accuracy = player.runSummary.totalShotsFired > 0
          ? player.runSummary.totalHitsLanded / player.runSummary.totalShotsFired
          : 0;
        const statGrid = document.createElement("div");
        statGrid.style.display = "grid";
        statGrid.style.gridTemplateColumns = "repeat(5, minmax(72px, 1fr))";
        statGrid.style.gap = "7px";
        const runStats = [
          ["DPS", `${Math.round(damagePerSecond * 10) / 10}`],
          ["Kills", `${player.runSummary.totalKills}`],
          [en ? "Damage" : "Schaden", `${Math.round(player.runSummary.totalDamageDealt)}`],
          [en ? "Accuracy" : "Treffer", `${Math.round(accuracy * 100)}%`],
          [en ? "Level" : "Level", `${player.level}`]
        ];
        for (const [label, value] of runStats) {
          const stat = document.createElement("div");
          stat.style.padding = "7px 9px";
          stat.style.borderRadius = "10px";
          stat.style.background = "rgba(2, 6, 23, 0.38)";
          const valueElement = document.createElement("strong");
          valueElement.style.display = "block";
          valueElement.style.fontSize = "16px";
          valueElement.style.color = themeAccent;
          valueElement.textContent = value;
          const labelElement = document.createElement("span");
          labelElement.style.fontSize = "10px";
          labelElement.style.color = hudInk.textSoft;
          labelElement.textContent = label;
          stat.appendChild(valueElement);
          stat.appendChild(labelElement);
          statGrid.appendChild(stat);
        }

        const assetRow = document.createElement("div");
        assetRow.style.display = "flex";
        assetRow.style.gap = "7px";
        assetRow.style.flexWrap = "wrap";
        const assets = [
          ...player.loadout.weapons.map((weapon) => ({
            id: weapon.weaponId,
            kind: "weapon" as const,
            title: `${weapon.displayName} · Lv.${weapon.level}`
          })),
          ...player.loadout.items.map((item) => ({
            id: item.itemId,
            kind: "item" as const,
            title: `${item.displayName} · Lv.${item.level}`
          }))
        ];
        if (assets.length === 0) {
          const empty = document.createElement("span");
          empty.style.color = hudInk.muted;
          empty.style.fontSize = "12px";
          empty.textContent = en ? "No equipment" : "Keine Ausrüstung";
          assetRow.appendChild(empty);
        }
        for (const asset of assets) {
          const tile = document.createElement("div");
          tile.title = asset.title;
          tile.style.width = "46px";
          tile.style.height = "46px";
          tile.style.padding = "4px";
          tile.style.borderRadius = "11px";
          tile.style.background = "rgba(2, 6, 23, 0.5)";
          tile.style.border = "1px solid rgba(226, 232, 240, 0.16)";
          const image = document.createElement("img");
          image.src = resolveArenaSurvivorShopIconPath(asset.kind, asset.id, state.visualTheme);
          image.alt = asset.title;
          image.style.width = "100%";
          image.style.height = "100%";
          image.style.objectFit = "contain";
          tile.appendChild(image);
          assetRow.appendChild(tile);
        }

        const statsLine = document.createElement("div");
        statsLine.style.fontSize = "12px";
        statsLine.style.color = hudInk.muted;
        statsLine.textContent = `${formatPlayerStats(player)}  |  M ${player.runSummary.totalMaterialsCollected}`;

        content.appendChild(title);
        content.appendChild(statGrid);
        content.appendChild(assetRow);
        content.appendChild(statsLine);
        card.appendChild(portrait);
        card.appendChild(content);
        card.animate(
          [
            { opacity: 0, transform: "translateY(12px)" },
            { opacity: 1, transform: "translateY(0)" }
          ],
          { duration: 260, delay: playerIndex * 55, easing: "cubic-bezier(.2,.8,.2,1)", fill: "both" }
        );
        return card;
      });
    summaryBody.replaceChildren(...playerSummaries);
    summaryCard.animate(
      [
        { opacity: 0, transform: "translate(-50%, -47%) scale(.98)" },
        { opacity: 1, transform: "translate(-50%, -50%) scale(1)" }
      ],
      { duration: 240, easing: "cubic-bezier(.2,.8,.2,1)" }
    );
  }

  function destroy(): void {
    overlay.remove();
  }

  return { update, destroy };
}
