import { getArenaSurvivorContentText } from "../contentText.js";
import type {
  ArenaSurvivorPlayerState,
  ArenaSurvivorShopOfferState,
  ArenaSurvivorState,
  ArenaSurvivorVisualTheme
} from "../protocol.js";
import {
  resolveArenaSurvivorLevelBonusIconPath,
  resolveArenaSurvivorShopIconPath
} from "../visualThemes.js";
import {
  createArenaSurvivorMoveInput,
  createArenaSurvivorShopInput,
  createArenaSurvivorShopCombineInput,
  createArenaSurvivorShopRerollInput,
  createArenaSurvivorShopSellInput
} from "./arenaSurvivorBindings.js";

type SupportedLanguage = "de" | "en";

interface LayoutStat {
  label: string;
  value: string;
  highlighted?: boolean;
}

interface ReadyLayoutModel {
  currentPlayerReady: boolean;
  readyCount: number;
  playerCount: number;
  label: string;
  description?: string;
  language?: SupportedLanguage;
  onToggleReady: () => void;
}

interface ShopOfferModel {
  id: string;
  kind: "item" | "weapon" | "upgrade";
  title: string;
  description: string;
  cost: number;
  affordable: boolean;
  purchased: boolean;
  iconPath?: string;
  targetLevel?: number;
  summary?: string;
  stats?: LayoutStat[];
  tags?: string[];
  detailLines?: Array<{
    label: string;
    value: string;
  }>;
}

interface ArenaSurvivorModernShopLayoutModel {
  survival?: { phase: string; cores: number; level: number };
  kind: "arena_survivor_modern_shop";
  title: string;
  subtitle?: string;
  helperText?: string;
  language?: SupportedLanguage;
  disabled: boolean;
  accentColor?: string;
  shopMode?: "regular" | "level_up";
  levelUpChoicesRemaining?: number;
  waveNumber: number;
  materials: number;
  ready?: ReadyLayoutModel;
  reroll?: {
    cost: number;
    count: number;
    affordable: boolean;
    onReroll: () => void;
  };
  loadout?: {
    weapons: Array<{
      weaponInstanceId: string;
      weaponId: string;
      level: number;
      maxLevel: number;
      displayName: string;
      description: string;
      iconPath?: string;
      sellValue?: number;
      sellable?: boolean;
      canCombine?: boolean;
      stats?: LayoutStat[];
    }>;
    items: Array<{
      itemId: string;
      level: number;
      displayName: string;
      description?: string;
      iconPath?: string;
    }>;
  };
  runSummary?: {
    wavesCleared: number;
    totalKills: number;
    totalMaterialsCollected: number;
    totalSurvivedMs: number;
  };
  playerStats?: Array<{
    label: string;
    value: string;
  }>;
  offers: ShopOfferModel[];
  onBuy: (offerId: string) => void;
  onSellWeapon?: (weaponInstanceId: string) => void;
  onCombineWeapon?: (weaponInstanceId: string) => void;
}

interface VirtualJoystickLayoutModel {
  kind: "virtual_joystick";
  title: string;
  subtitle?: string;
  helperText?: string;
  minimal?: boolean;
  stickPlacement?: "center" | "bottom" | "lower-middle";
  disabled: boolean;
  accentColor?: string;
  resetKey: string;
  centerLabel?: string;
  ready?: ReadyLayoutModel;
  stats?: LayoutStat[];
  onMoveChange: (moveX: number, moveY: number) => void;
}

interface ControllerGameRenderContext {
  state: {
    room?: {
      language?: SupportedLanguage;
      selectedGameId?: string;
      players?: Array<{ id: string; name: string; isReady?: boolean }>;
    } | null;
    player?: {
      id: string;
      color?: string;
      isReady?: boolean;
    } | null;
    game?: {
      phase?: string;
      roundNumber?: number;
      state?: unknown;
    } | null;
  };
  onInput(input: unknown): void;
  onSetReady?: (isReady: boolean) => void;
}

function formatTime(ms: number): string {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function formatRoundedHp(value: number | null | undefined): string {
  return `${Math.max(0, Math.round(value ?? 0))}`;
}

function formatPercent(value: number | null | undefined): string {
  const safeValue = Math.max(0, value ?? 0);
  return `${safeValue >= 10 ? Math.round(safeValue) : Math.round(safeValue * 10) / 10}%`;
}

function shouldHighlightOfferStat(label: string, index: number): boolean {
  return index === 0 || /crit|schaden|damage|projektil|projectile|reichweite|range/i.test(label);
}

function buildOfferStats(offer: ArenaSurvivorShopOfferState): LayoutStat[] {
  if (!offer.detailLines?.length) {
    return [];
  }

  return offer.detailLines.map((detailLine, index) => ({
    label: detailLine.label,
    value: detailLine.value,
    highlighted: shouldHighlightOfferStat(detailLine.label, index)
  }));
}

function enrichArenaSurvivorShopOffers(
  offers: ArenaSurvivorShopOfferState[],
  visualTheme: ArenaSurvivorVisualTheme
): ShopOfferModel[] {
  return offers.map((offer) => {
    const stats = buildOfferStats(offer);
    const iconPath = offer.kind === "weapon" && offer.weaponId
      ? resolveArenaSurvivorShopIconPath("weapon", offer.weaponId, visualTheme)
      : offer.kind === "item" && offer.itemId
        ? resolveArenaSurvivorShopIconPath("item", offer.itemId, visualTheme)
        : offer.kind === "upgrade" && offer.levelBonusId
          ? resolveArenaSurvivorLevelBonusIconPath(offer.levelBonusId, visualTheme)
        : offer.iconPath;
    const themedOffer = { ...offer, iconPath };

    return stats.length > 0 ? { ...themedOffer, stats } : themedOffer;
  });
}

function buildLoadoutWeaponStats(
  detailLines: ArenaSurvivorPlayerState["loadout"]["weapons"][number]["detailLines"]
): LayoutStat[] {
  return (
    detailLines?.map((detailLine, index) => ({
      label: detailLine.label,
      value: detailLine.value,
      highlighted: index === 0 || detailLine.label === "Crit" || detailLine.label === "Projektile"
    })) ?? []
  );
}

function resolveCurrentPlayer(
  context: ControllerGameRenderContext,
  state: ArenaSurvivorState | null
): ArenaSurvivorPlayerState | null {
  const playerId = context.state.player?.id;

  if (!playerId || !state) {
    return null;
  }

  return state.players.find((player) => player.playerId === playerId) ?? null;
}

function formatStatus(player: ArenaSurvivorPlayerState | null, state: ArenaSurvivorState | null, en: boolean): string {
  if (!player || !state) {
    return en ? "Waiting" : "Wartet";
  }

  if (state.result.outcome === "survived") {
    return en ? "Won" : "Gewonnen";
  }

  if (state.result.outcome === "defeated") {
    return en ? "Lost" : "Verloren";
  }

  if (!player.alive) {
    return en ? "Out" : "Ausgeschieden";
  }

  return en ? "Active" : "Aktiv";
}

function resolvePlayerStats(player: ArenaSurvivorPlayerState | null, language?: "de" | "en"): Array<{ label: string; value: string }> {
  if (!player) {
    return [];
  }

  const stats = player.stats;

  return [
    { label: "Move Speed", value: `${Math.round(stats.moveSpeed)}` },
    { label: "Max HP", value: formatRoundedHp(stats.maxHp) },
    { label: "Proj Dmg", value: `${Math.round(stats.projectileDamageMultiplier * 100)}%` },
    { label: "Fire Rate", value: `${Math.round(stats.autoFireRateMultiplier * 100)}%` },
    { label: "Armor", value: `${Math.round((stats.armor ?? 0) * 100) / 100}` },
    { label: "Dodge", value: formatPercent(stats.dodgePct) },
    { label: "Crit", value: `${Math.round(stats.critChancePct ?? 0)}%` },
    { label: "Atk Spd", value: `${Math.round((stats.attackSpeedMultiplier ?? 1) * 100)}%` },
    { label: "Range", value: `${Math.round((stats.weaponRangeMultiplier ?? 1) * 100)}%` },
    { label: "Luck", value: `${Math.round(stats.luck ?? 0)}` },
    { label: "Harvest", value: `${Math.round(stats.harvesting ?? 0)}` },
    { label: "Life Steal", value: formatPercent(stats.lifeStealPct) },
    { label: "Regen", value: `${Math.round((stats.hpRegen ?? 0) * 10) / 10}` }
  ].map((stat) => ({ ...stat, label: getArenaSurvivorContentText(stat.label, language) }));
}

function buildArenaSurvivorShopModel(
  context: ControllerGameRenderContext,
  state: ArenaSurvivorState,
  player: ArenaSurvivorPlayerState
): ArenaSurvivorModernShopLayoutModel {
  const currentPlayerReady = Boolean(context.state.player?.isReady);
  const language = context.state.room?.language;
  const en = language === "en";
  const playerCount = context.state.room?.players?.length ?? 0;
  const readyCount = (context.state.room?.players ?? []).filter((roomPlayer) => roomPlayer.isReady).length;
  const waitingForPlayers = readyCount < playerCount;
  const choosingLevelBonus = player.shop.mode === "level_up";
  const combineCounts = player.loadout.weapons.reduce((counts, weapon) => {
    const key = `${weapon.weaponId}:${weapon.level}`;
    counts.set(key, (counts.get(key) ?? 0) + 1);
    return counts;
  }, new Map<string, number>());

  return {
    kind: "arena_survivor_modern_shop",
    language,
    title: choosingLevelBonus
      ? en ? `${player.name} Level Up` : `${player.name} Level-Up`
      : `${player.name} Shop`,
    subtitle: choosingLevelBonus
      ? en
        ? `${player.pendingLevelUpChoices} bonus ${player.pendingLevelUpChoices === 1 ? "choice" : "choices"} remaining`
        : `Noch ${player.pendingLevelUpChoices} Bonus-${player.pendingLevelUpChoices === 1 ? "Wahl" : "Wahlen"}`
      : `${en ? "Wave" : "Welle"} ${state.waveNumber} | ${en ? "Materials" : "Material"} ${player.materials}`,
    helperText:
      choosingLevelBonus
        ? en
          ? "Choose one of four random bonuses for each earned level."
          : "Waehle fuer jedes verdiente Level einen von vier zufaelligen Boni."
        : waitingForPlayers
        ? en
          ? `Waiting for all players. ${readyCount}/${playerCount} ready.`
          : `Warte auf alle Spieler. ${readyCount}/${playerCount} bereit.`
        : player.shop.available
          ? en
            ? "Choose your upgrades for the next wave."
            : "Waehle deine Upgrades fuer die naechste Welle."
          : en
            ? "Ready check complete. The next wave starts soon."
            : "Bereitmeldung abgeschlossen. Die naechste Welle startet gleich.",
    disabled: false,
    accentColor: player.color,
    shopMode: player.shop.mode,
    levelUpChoicesRemaining: choosingLevelBonus ? player.pendingLevelUpChoices : undefined,
    waveNumber: state.waveNumber,
    materials: player.materials,
    ready: context.onSetReady && !choosingLevelBonus
      ? {
          currentPlayerReady,
          readyCount,
          playerCount,
          language,
          label: en ? "Next Wave" : "Naechste Welle",
          description: waitingForPlayers
            ? en
              ? "Buy upgrades, then ready up."
              : "Kaufe Upgrades und druecke dann auf bereit."
            : en
              ? "Everyone is ready. The next wave starts automatically."
              : "Alle sind bereit. Die naechste Welle startet automatisch.",
          onToggleReady: () => {
            context.onSetReady?.(!currentPlayerReady);
          }
        }
      : undefined,
    reroll: choosingLevelBonus ? undefined : {
      cost: player.shop.rerollCost,
      count: player.shop.rerollCount,
      affordable: player.shop.canReroll,
      onReroll: () => {
        if (!player.playerId) {
          return;
        }

        context.onInput(createArenaSurvivorShopRerollInput(player.playerId));
      }
    },
    loadout: choosingLevelBonus ? undefined : {
      weapons: player.loadout.weapons.map((weapon) => ({
        weaponInstanceId: weapon.weaponInstanceId,
        weaponId: weapon.weaponId,
        level: weapon.level,
        maxLevel: weapon.maxLevel,
        displayName: weapon.displayName,
        description: weapon.description,
        iconPath: resolveArenaSurvivorShopIconPath("weapon", weapon.weaponId, state.visualTheme),
        sellValue: weapon.sellValue,
        sellable: weapon.sellable,
        canCombine:
          weapon.level < weapon.maxLevel &&
          (combineCounts.get(`${weapon.weaponId}:${weapon.level}`) ?? 0) > 1,
        stats: buildLoadoutWeaponStats(weapon.detailLines)
      })),
      items: player.loadout.items.map((item) => ({
        ...item,
        iconPath: resolveArenaSurvivorShopIconPath("item", item.itemId, state.visualTheme)
      }))
    },
    runSummary: choosingLevelBonus ? undefined : player.runSummary,
    playerStats: resolvePlayerStats(player, language),
    offers: enrichArenaSurvivorShopOffers(player.shop.offers, state.visualTheme),
    onBuy: (offerId) => {
      if (!player.playerId) {
        return;
      }

      context.onInput(createArenaSurvivorShopInput(player.playerId, offerId));
    },
    onSellWeapon: choosingLevelBonus ? undefined : (weaponInstanceId) => {
      if (!player.playerId) {
        return;
      }

      context.onInput(createArenaSurvivorShopSellInput(player.playerId, weaponInstanceId));
    },
    onCombineWeapon: choosingLevelBonus ? undefined : (weaponInstanceId) => {
      if (!player.playerId) {
        return;
      }

      context.onInput(createArenaSurvivorShopCombineInput(player.playerId, weaponInstanceId));
    }
  };
}

function buildArenaSurvivorJoystickModel(
  context: ControllerGameRenderContext,
  state: ArenaSurvivorState | null,
  player: ArenaSurvivorPlayerState | null
): VirtualJoystickLayoutModel {
  const playerId = context.state.player?.id ?? "";
  const language = context.state.room?.language;
  const en = language === "en";
  const running =
    !state?.survival?.pause &&
    context.state.game?.phase === "playing" &&
    Boolean(player?.alive) &&
    state?.result.outcome === "running";
  const timeText =
    state?.result.outcome === "running"
      ? `${en ? "Time" : "Zeit"} ${formatTime(state.remainingMs)}`
      : `${en ? "Survived" : "Ueberlebt"} ${formatTime(state?.elapsedMs ?? 0)}`;

  return {
    kind: "virtual_joystick",
    title: player?.name ?? "Arena Survivor",
    minimal: true,
    // Thumb zone: the stick sits at the lower screen edge while playing.
    stickPlacement: "lower-middle",
    subtitle: player?.respawnAtMs && state?.survival ? `Respawn ${Math.max(0, Math.ceil((player.respawnAtMs - state.elapsedMs) / 1000))}s` : running
      ? en
        ? "Move freely. Auto-fire is active."
        : "Bewege dich frei. Automatische Schuesse laufen."
      : en
        ? "Waiting for the next round"
        : "Warte auf die naechste Runde",
    helperText:
      state?.result.outcome === "running"
        ? en
          ? "Your weapon fires automatically. Collected material is shared with the whole team."
          : "Deine Waffe schiesst automatisch. Gesammeltes Material erhaelt das ganze Team."
        : state?.result.title ?? (en ? "Move your character with the virtual stick." : "Bewege den Charakter mit dem virtuellen Stick."),
    disabled: !running,
    accentColor: player?.color ?? context.state.player?.color ?? "#38bdf8",
    resetKey: `${context.state.game?.roundNumber ?? 0}:${context.state.game?.phase ?? "idle"}`,
    centerLabel: en ? "MOVE" : "LOS",
    stats: [
      { label: en ? "HP" : "Leben", value: `${formatRoundedHp(player?.hp)}/${formatRoundedHp(player?.maxHp)}` },
      { label: en ? "Lvl." : "Stufe", value: `${player?.level ?? 1}` },
      { label: en ? "XP" : "EP", value: `${Math.round(player?.experience ?? 0)}/${Math.round(player?.experienceToNextLevel ?? 1)}` },
      { label: en ? "Materials" : "Material", value: `${player?.materials ?? 0}` },
      { label: "Status", value: formatStatus(player, state, en), highlighted: true },
      { label: en ? "Time" : "Zeit", value: timeText },
      { label: state?.survival ? (en ? "Intensity" : "Intensitaet") : en ? "Wave" : "Welle", value: `${state?.waveNumber ?? 1}` },
      ...(state?.survival ? [{ label: en ? "Cores" : "Kerne", value: `${player?.evolutionCores ?? 0}` }] : [])
    ],
    onMoveChange: (moveX, moveY) => {
      if (!playerId) {
        return;
      }

      context.onInput(createArenaSurvivorMoveInput(playerId, moveX, moveY));
    }
  };
}

export function buildArenaSurvivorControllerModel(
  context: ControllerGameRenderContext
): ArenaSurvivorModernShopLayoutModel | VirtualJoystickLayoutModel {
  const gameState = (context.state.game?.state ?? null) as ArenaSurvivorState | null;
  const currentPlayer = resolveCurrentPlayer(context, gameState);
  const controllerPhase = context.state.game?.phase;
  if (gameState?.survival && gameState.result.outcome !== "running" && currentPlayer) {
    const model = buildArenaSurvivorShopModel(context, gameState, currentPlayer);
    model.survival = { phase: "result", cores: currentPlayer.evolutionCores ?? 0, level: gameState.survival.level };
    model.title = gameState.result.title;
    model.helperText = context.state.room?.language === "en" ? "Start a new run or return to setup on the host." : "Starte am Host einen neuen Run oder kehre zum Setup zurueck.";
    model.offers = []; model.ready = undefined; model.reroll = undefined;
    model.onSellWeapon = undefined; model.onCombineWeapon = undefined; model.disabled = true;
    return model;
  }
  const shouldShowShop =
    Boolean(currentPlayer) && !gameState?.survival?.pause &&
    (Boolean(currentPlayer?.shop.available) ||
      controllerPhase === "result" ||
      controllerPhase === "scoreboard" ||
      controllerPhase === "finished" ||
      controllerPhase === "locked");

  if (gameState && currentPlayer && shouldShowShop) {
    return buildArenaSurvivorShopModel(context, gameState, currentPlayer);
  }

  if (gameState?.survival?.pause && currentPlayer) {
    return buildSurvivalPauseModel(context, gameState, currentPlayer);
  }

  return buildArenaSurvivorJoystickModel(context, gameState, currentPlayer);
}

function buildSurvivalPauseModel(context: ControllerGameRenderContext, state: ArenaSurvivorState, player: ArenaSurvivorPlayerState): ArenaSurvivorModernShopLayoutModel {
  const s = state.survival!;
  const en = context.state.room?.language === "en";
  const ready = s.readyPlayerIds.includes(player.playerId);
  const model = buildArenaSurvivorShopModel(context, state, player);
  model.survival = { phase: s.pause ?? "", cores: player.evolutionCores ?? 0, level: s.level };
  model.title = s.pause === "victory" ? (en ? "Survival won!" : "Survival gewonnen!") : s.pause === "forge" ? (en ? "Evolution Forge" : "Evolutionsschmiede") : s.pause === "level_up" ? `Level ${s.level}` : "Survival Shop";
  model.subtitle = `${en ? "Personal cores" : "Eigene Kerne"}: ${player.evolutionCores ?? 0}`;
  model.helperText = ready ? (en ? "Waiting for the team." : "Warte auf das Team.") : s.pause === "level_up" ? (en ? "Choose one of three powers. No timer." : "Waehle eines von drei Power-Ups. Ohne Zeitlimit.") : (en ? "Combat is paused for everyone. Finish when ready." : "Der Kampf pausiert fuer alle. Bestaetige, wenn du fertig bist.");
  model.disabled = ready;
  if (s.pause === "level_up") {
    model.shopMode = "level_up";
    model.levelUpChoicesRemaining = ready ? 0 : 1;
    model.reroll = undefined;
    model.onSellWeapon = undefined;
    model.onCombineWeapon = undefined;
    model.loadout = undefined;
  }
  model.ready = s.pause === "shop" || s.pause === "forge" ? {
    currentPlayerReady: ready, readyCount: s.readyPlayerIds.length, playerCount: s.participantIds.length,
    language: context.state.room?.language, label: en ? "Continue" : "Fortsetzen", description: model.helperText,
    onToggleReady: () => context.onInput({ type: "survival:ready", playerId: player.playerId, sentAt: Date.now() })
  } : undefined;
  if (s.pause === "chest") {
    const reward = s.chestReward;
    const ownsReward = reward?.playerId === player.playerId;
    model.title = en ? "Chest loot" : "Kistenfund";
    model.helperText = ownsReward
      ? (en ? "Keep the reward or salvage it for gold. Combat resumes after your choice." : "Fund behalten oder fuer Gold verwerten. Nach deiner Wahl geht es weiter.")
      : (en ? "A teammate is choosing their chest reward." : "Ein Mitspieler entscheidet ueber seinen Kistenfund.");
    model.shopMode = "level_up"; model.loadout = undefined; model.reroll = undefined;
    model.onSellWeapon = undefined; model.onCombineWeapon = undefined; model.disabled = !ownsReward;
    const loot = reward?.offer;
    model.offers = ownsReward && reward ? [
      { ...(loot ? enrichArenaSurvivorShopOffers([loot], state.visualTheme)[0] : {}), id: `${reward.id}:take`,
        kind: loot?.kind ?? "upgrade", title: loot ? `${en ? "Keep" : "Behalten"}: ${loot.title}` : (en ? "Keep Evolution Core" : "Evolutionskern behalten"),
        description: loot?.description ?? (en ? "One personal core for the Forge." : "Ein eigener Kern für die Schmiede."),
        iconPath: loot ? resolveArenaSurvivorShopIconPath("weapon", loot.weaponId!, state.visualTheme) : "/arena-survivor/themes/frostfire-saga/upgrades/core.png",
        targetLevel: loot?.targetLevel ?? 1, cost: 0, affordable: true, purchased: false },
      { id: `${reward.id}:salvage`, kind: "upgrade", title: en ? `Salvage: +${reward.salvageGold} gold` : `Verwerten: +${reward.salvageGold} Gold`,
        description: en ? "Receive gold instead of this reward." : "Erhalte Gold anstelle dieses Fundes.", iconPath: "/arena-survivor/themes/frostfire-saga/pickups/material.png", targetLevel: 1, cost: 0, affordable: true, purchased: false }
    ] : [];
  }
  if (s.pause === "forge" || s.pause === "victory") {
    model.reroll = undefined; model.onSellWeapon = undefined; model.onCombineWeapon = undefined;
    model.shopMode = "level_up";
    model.offers = s.pause === "victory" ? [true, false].map(continueRun => ({
      id: continueRun ? "endless" : "finish", kind: "upgrade" as const, title: continueRun ? (en ? "Continue Endless" : "Endlos weiterspielen") : en ? "Finish run" : "Run abschliessen",
      description: continueRun ? (en ? "Keep your build and keep fighting." : "Mit deinem Build weiterspielen.") : (en ? "Save the victory." : "Mit dem Sieg abschliessen."),
      targetLevel: 1, cost: 0, affordable: !ready, purchased: false
    })) : player.loadout.weapons.filter(w => w.level === 4 && !w.evolved).map(w => ({
      id: w.weaponInstanceId, kind: "weapon" as const, weaponId: w.weaponId, title: `✦ ${w.displayName}`,
      description: en ? "1 personal core · 2× damage, faster attacks, greater range" : "1 eigener Kern · 2× Schaden, schnellere Angriffe, mehr Reichweite",
      targetLevel: 4, cost: 0, affordable: (player.evolutionCores ?? 0) > 0, purchased: false,
      iconPath: resolveArenaSurvivorShopIconPath("weapon", w.weaponId, state.visualTheme)
    }));
    model.onBuy = id => context.onInput(s.pause === "victory" ? { type: "survival:endless", playerId: player.playerId, sentAt: Date.now(), continueRun: id === "endless" } : { type: "survival:evolve", playerId: player.playerId, sentAt: Date.now(), weaponInstanceId: id });
  }
  return model;
}
