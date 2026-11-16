import { transitionRoundState } from "@open-party-lab/game-core";
import type { ArenaSurvivorInput, ArenaSurvivorSurvivalState } from "../protocol.js";
import { createEmptyShopState, createSeededRandom, type ArenaSurvivorRuntimeState as State } from "./arenaSurvivorState.js";
import { applyArenaSurvivorShopPurchase, applyArenaSurvivorShopSell, applyArenaSurvivorShopCombine, applyArenaSurvivorShopReroll, createArenaSurvivorShopStateForPlayer } from "./loadout/arenaSurvivorLoadout.js";
import { createArenaSurvivorLevelUpShopState } from "./progression/arenaSurvivorLevelBonuses.js";
import { resolveArenaSurvivorEnemyExperienceReward } from "./progression/arenaSurvivorProgression.js";
import { resolveArenaSurvivorExperienceToNextLevel } from "./progression/arenaSurvivorProgression.js";
import { createArenaSurvivorEnemy } from "./factories/createEnemy.js";
import { createId } from "./utils/createId.js";
import { frostfireWorld, isFrostfireWalkable } from "../survivalWorld.js";
import { createLoadoutWeaponState, createArenaSurvivorWeaponRuntimeStates, resolveArenaSurvivorPlayerStats } from "./loadout/arenaSurvivorLoadout.js";

export const survivalDurationMs = 20 * 60_000;
export const survivalWorld = { width: frostfireWorld.width, height: frostfireWorld.height, groupDistance: 620 };
export function createSurvivalState(): ArenaSurvivorSurvivalState {
  return { level: 1, experience: 0, experienceToNextLevel: 18, pause: null, readyPlayerIds: [], participantIds: [], won: false, endless: false,
    nextShopMs: 180_000, nextForgeMs: 360_000, nextChestMs: 45_000, nextBossMs: 300_000,
    maxGroupDistance: survivalWorld.groupDistance, objectives: [] };
}
export function initializeSurvival(state: State): State {
  return { ...state, visualTheme: "frostfire-saga", arenaWidth: survivalWorld.width, arenaHeight: survivalWorld.height,
    survival: createSurvivalState(), elapsedMs: 0, remainingMs: survivalDurationMs,
    players: state.players.map((p, i) => ({ ...p, x: 2400 + (i % 2) * 60, y: 1800 + Math.floor(i / 2) * 60,
      invulnerableUntilMs: 0, evolutionCores: 0, deaths: 0, respawnAtMs: undefined, level: 1, experience: 0, pendingLevelUpChoices: 0 })) };
}
function random(state: State): number {
  const r = createSeededRandom(state.seed); state.seed = r.seed; return r.value;
}
function addObjective(state: State, kind: ArenaSurvivorSurvivalState["objectives"][number]["kind"]) {
  const p = state.players.find(p => p.alive) ?? state.players[0];
  const angle = random(state) * Math.PI * 2;
  const distance = 650 + random(state) * 600;
  const objective = { id: createId(kind), kind, x: Math.max(120, Math.min(state.arenaWidth - 120, p.x + Math.cos(angle) * distance)),
    y: Math.max(120, Math.min(state.arenaHeight - 120, p.y + Math.sin(angle) * distance)) };
  const available = () => isFrostfireWalkable(objective.x, objective.y, 110) && state.survival!.objectives.every(o => Math.hypot(o.x - objective.x, o.y - objective.y) > 220);
  for (let attempt = 0; attempt < 32 && !available(); attempt++) {
    const direction = random(state) * Math.PI * 2, range = 650 + random(state) * 750;
    objective.x = Math.max(120, Math.min(state.arenaWidth - 120, p.x + Math.cos(direction) * range));
    objective.y = Math.max(120, Math.min(state.arenaHeight - 120, p.y + Math.sin(direction) * range));
  }
  if (!available()) {
    const safe = frostfireWorld.sites.find(site => isFrostfireWalkable(site.x, site.y, 110) && state.survival!.objectives.every(o => Math.hypot(o.x - site.x, o.y - site.y) > 220));
    if (safe) { objective.x = safe.x; objective.y = safe.y; }
  }
  state.survival!.objectives.push(objective); return objective;
}
function recipient(state: State, aliveOnly = false) {
  const eligible = state.players.filter(p => state.survival!.participantIds.includes(p.playerId) && (!aliveOnly || p.alive));
  return eligible[Math.floor(random(state) * eligible.length)];
}
function awardCore(state: State) {
  const p = recipient(state); if (p) p.evolutionCores = (p.evolutionCores ?? 0) + 1;
}
function levelOffers(state: State, player: State["players"][number]) {
  const result = createArenaSurvivorLevelUpShopState(player, state.seed, state.language, true);
  state.seed = result.seed;
  return result.shop;
}
export function openSurvivalPause(state: State, pause: "level_up" | "shop" | "forge" | "victory" | "chest"): State {
  const s = state.survival!; s.pause = pause; s.readyPlayerIds = [];
  state.players = state.players.map(p => {
    let shop = createEmptyShopState();
    if (pause === "level_up") shop = levelOffers(state, p);
    if (pause === "shop") {
      p = { ...p, loadout: { ...p.loadout, weapons: p.loadout.weapons.map(w => ({ ...w, sellable: true, sellValue: createLoadoutWeaponState(w.weaponId, w.level, w).sellValue })) } };
      const result = createArenaSurvivorShopStateForPlayer(p, state.waveNumber, 0, state.seed, state.language, 1.35);
      state.seed = result.seed; shop = result.shop;
    }
    return { ...p, moveInputX: 0, moveInputY: 0, vx: 0, vy: 0, shop };
  });
  return state;
}
function finishRun(state: State, now: number, won: boolean): State {
  state.survival!.pause = null;
  state.players = state.players.map(p => ({ ...p, shop: createEmptyShopState(), runSummary: {
    ...p.runSummary, totalKills: p.runStats.kills, totalDamageDealt: p.runStats.damageDealt,
    totalMaterialsCollected: p.runStats.materialsCollected, totalSurvivedMs: state.elapsedMs,
    totalShotsFired: p.runStats.shotsFired, totalHitsLanded: p.runStats.hitsLanded, totalDamageTaken: p.runStats.damageTaken
  } }));
  const en = state.language === "en";
  const title = won ? (en ? "Survival won" : "Survival gewonnen") : state.survival!.won ? (en ? "Endless ended · Survival won" : "Endlos beendet · Survival gewonnen") : (en ? "Survival · Game Over" : "Survival · Spiel vorbei");
  return transitionRoundState({ ...state, result: { outcome: won ? "survived" : "defeated", title } }, "locked", now, { message: title });
}
export function handleSurvivalInput(state: State, input: ArenaSurvivorInput, now: number): State {
  const s = state.survival!;
  if (!s.pause || !s.participantIds.includes(input.playerId)) return state;
  let p = state.players.find(p => p.playerId === input.playerId)!;
  if (s.readyPlayerIds.includes(p.playerId)) return state;
  if (s.pause === "chest") {
    const reward = s.chestReward;
    if (!reward || reward.playerId !== p.playerId || input.type !== "shop:buy") return state;
    if (input.offerId === `${reward.id}:salvage`) {
      p.materials += reward.salvageGold; p.runStats.materialsCollected += reward.salvageGold;
    } else if (input.offerId === `${reward.id}:take`) {
      if (reward.core) p.evolutionCores = (p.evolutionCores ?? 0) + 1;
      else if (reward.offer) {
        const weapon = p.loadout.weapons.find(w => w.weaponInstanceId === reward.offer!.targetWeaponInstanceId);
        if (!weapon || weapon.evolved || weapon.level >= 4 || reward.offer.targetLevel !== weapon.level + 1) return state;
        Object.assign(weapon, createLoadoutWeaponState(weapon.weaponId, weapon.level + 1, weapon));
        p.weaponRuntimeStates = createArenaSurvivorWeaponRuntimeStates(p.loadout, p.weaponRuntimeStates);
        p.stats = resolveArenaSurvivorPlayerStats(p.loadout, p.character.id, p.levelBonusModifiers);
      } else return state;
    } else return state;
    s.chestReward = undefined; s.pause = null; s.readyPlayerIds = [];
    state.players = state.players.map(player => ({ ...player, pendingLevelUpChoices: 0, shop: createEmptyShopState() }));
    return { ...state, survival: s, updatedAt: now };
  }
  if (input.type === "survival:endless" && s.pause === "victory") {
    if (!input.continueRun) return finishRun(state, now, true);
    s.readyPlayerIds.push(p.playerId);
  } else if (input.type === "survival:ready" && (s.pause === "forge" || s.pause === "shop")) {
    s.readyPlayerIds.push(p.playerId); p.shop = createEmptyShopState();
  } else if (input.type === "survival:evolve" && s.pause === "forge") {
    const w = p.loadout.weapons.find(w => w.weaponInstanceId === input.weaponInstanceId);
    if (w && w.level === 4 && !w.evolved && (p.evolutionCores ?? 0) > 0) {
      p.evolutionCores!--; w.evolved = true; w.displayName = `✦ ${w.displayName}`;
      w.description = state.language === "en" ? "Evolved: double damage, faster attacks, greater range" : "Evolviert: doppelter Schaden, schnellere Angriffe, mehr Reichweite";
    }
  } else if (s.pause === "shop") {
    if (input.type === "shop:buy") state = applyArenaSurvivorShopPurchase(state, p.playerId, input.offerId);
    if (input.type === "shop:sell") state = applyArenaSurvivorShopSell(state, p.playerId, input.weaponInstanceId);
    if (input.type === "shop:combine") state = applyArenaSurvivorShopCombine(state, p.playerId, input.weaponInstanceId);
    if (input.type === "shop:reroll") state = applyArenaSurvivorShopReroll(state, p.playerId);
  } else if (s.pause === "level_up" && input.type === "shop:buy") {
    const o = p.shop.offers.find(o => o.id === input.offerId);
    if (!o || !o.affordable || o.kind !== "upgrade" || !o.levelBonusModifiers) return state;
    state = applyArenaSurvivorShopPurchase(state, p.playerId, o.id);
    p = state.players.find(p => p.playerId === input.playerId)!;
    p.pendingLevelUpChoices = 0; p.shop = createEmptyShopState(); s.readyPlayerIds.push(p.playerId);
  }
  if (s.participantIds.every(id => s.readyPlayerIds.includes(id))) {
    if (s.pause === "victory") s.endless = true;
    s.pause = null; s.readyPlayerIds = [];
    state.players = state.players.map(p => ({ ...p, shop: createEmptyShopState() }));
  }
  return { ...state, survival: s, updatedAt: now };
}
export function updateSurvivalPause(state: State, now: number, activeIds: string[]): State {
  // Pauses still need a fresh snapshot: the runtime broadcasts only changed
  // state references, and controller playing-state packets can be dropped.
  state = { ...state, survival: { ...state.survival! }, updatedAt: now };
  const s = state.survival!;
  s.participantIds = state.players.filter(p => activeIds.includes(p.playerId)).map(p => p.playerId);
  // A disconnected recipient cannot hold the whole team in the chest screen.
  if (s.pause === "chest" && s.chestReward && !s.participantIds.includes(s.chestReward.playerId)) {
    const owner = state.players.find(p => p.playerId === s.chestReward!.playerId);
    if (owner) { owner.materials += s.chestReward.salvageGold; owner.runStats.materialsCollected += s.chestReward.salvageGold; owner.shop = createEmptyShopState(); }
    s.chestReward = undefined; s.pause = null; s.readyPlayerIds = [];
  }
  if (s.pause && s.participantIds.length > 0 && s.participantIds.every(id => s.readyPlayerIds.includes(id))) {
    if (s.pause === "victory") s.endless = true;
    s.pause = null; s.readyPlayerIds = [];
    state.players = state.players.map(p => ({ ...p, shop: createEmptyShopState() }));
  }
  return state;
}
export function advanceSurvivalWorld(state: State): State {
  const s = state.survival!;
  // Cull before the combat snapshot is taken: despawning is never a kill and
  // must not pass through damage/death handling or shared XP accounting.
  const living = state.players.filter(p => p.alive && s.participantIds.includes(p.playerId));
  if (living.length) {
    const nearTeam = (entity: { x: number; y: number }) => living.some(p => (p.x - entity.x) ** 2 + (p.y - entity.y) ** 2 <= 1800 ** 2);
    const bossIds = new Set(s.objectives.filter(o => o.kind === "boss").map(o => o.enemyId));
    state.enemies = state.enemies.filter(enemy => bossIds.has(enemy.id) || nearTeam(enemy));
    state.spawnIndicators = state.spawnIndicators.filter(nearTeam);
  }
  if (s.endless && s.nextBossMs < state.elapsedMs - 120_000) s.nextBossMs = state.elapsedMs + 120_000;
  if (state.elapsedMs >= s.nextShopMs) { addObjective(state, "shop"); s.nextShopMs += 180_000; }
  if (state.elapsedMs >= s.nextForgeMs) { addObjective(state, "forge"); s.nextForgeMs += 240_000; }
  if (state.elapsedMs >= s.nextChestMs) {
    if (s.objectives.filter(o => o.kind.includes("chest")).length < 6) addObjective(state, random(state) < 0.2 ? "rare_chest" : "chest");
    s.nextChestMs += 60_000;
  }
  const final = !s.finalBossId && state.elapsedMs >= survivalDurationMs;
  if (final || (state.elapsedMs >= s.nextBossMs && (state.elapsedMs < survivalDurationMs || s.endless))) {
    const o = addObjective(state, "boss");
    const hp = (final ? 6000 : 600 + state.elapsedMs / 1000 * 2) * (1 + (state.players.length - 1) * 0.6) * (0.7 + state.difficultyTier * 0.15);
    const boss = createArenaSurvivorEnemy(final ? "crimson-overlord" : "scrap-goliath", o, state.players.find(p => p.alive) ?? state.players[0], state.elapsedMs,
      { maxHp: hp, contactDamage: Math.round((final ? 70 : 40) * (1 + (state.difficultyTier - 1) * 0.2)), projectileDamageMultiplier: 2 * (1 + (state.difficultyTier - 1) * 0.2), moveSpeed: final ? 75 : 65 });
    state.enemies.push(boss); Object.assign(o, { enemyId: boss.id });
    if (final) s.finalBossId = boss.id; else s.nextBossMs += s.endless ? 120_000 : 300_000;
  }
  return state;
}
export function resolveSurvivalCombat(state: State, before: State, now: number): State {
  const s = state.survival!;
  // Compare the full combat pipeline: both melee and projectile kills award shared XP.
  const ids = new Set(state.enemies.map(e => e.id));
  for (const e of before.enemies) if (!ids.has(e.id)) {
    s.experience += resolveArenaSurvivorEnemyExperienceReward(e.definitionId);
    const o = s.objectives.find(o => o.enemyId === e.id);
    if (o) { awardCore(state); s.objectives = s.objectives.filter(entry => entry.id !== o.id); }
    if (s.finalBossId === e.id) s.won = true;
  }
  const active = state.players.filter(p => s.participantIds.includes(p.playerId));
  if (active.length && active.every(p => !p.alive)) return finishRun(state, now, false);
  for (const p of state.players) {
    if (!p.alive && p.respawnAtMs === undefined) { p.deaths = (p.deaths ?? 0) + 1; p.respawnAtMs = state.elapsedMs + Math.min(25_000, p.deaths * 5000); }
    if (!p.alive && active.length > 1 && s.participantIds.includes(p.playerId) && state.elapsedMs >= (p.respawnAtMs ?? Infinity)) {
      const teammate = active.find(p => p.alive);
      if (teammate) { p.x = teammate.x; p.y = teammate.y; p.alive = true; p.hp = p.maxHp * 0.25; p.invulnerableUntilMs = state.elapsedMs + 3000; p.respawnAtMs = undefined; }
    }
    p.level = s.level; p.experience = s.experience; p.experienceToNextLevel = s.experienceToNextLevel; p.pendingLevelUpChoices = 0;
  }
  if (s.won && !s.endless) return openSurvivalPause(state, "victory");
  if (s.experience >= s.experienceToNextLevel) {
    s.experience -= s.experienceToNextLevel; s.level++; s.experienceToNextLevel = Math.ceil(resolveArenaSurvivorExperienceToNextLevel(s.level) * 1.5);
    state.players.forEach(p => { p.level = s.level; p.experience = s.experience; p.experienceToNextLevel = s.experienceToNextLevel; p.pendingLevelUpChoices = 1; });
    return openSurvivalPause(state, "level_up");
  }
  for (const o of [...s.objectives]) {
    if (o.kind === "boss" || !active.some(p => p.alive && Math.hypot(p.x - o.x, p.y - o.y) < 65)) continue;
    s.objectives = s.objectives.filter(entry => entry.id !== o.id);
    if (o.kind === "shop" || o.kind === "forge") return openSurvivalPause(state, o.kind);
    const lootPlayer = recipient(state);
    if (lootPlayer) {
      const offers = lootPlayer.loadout.weapons.filter(w => !w.evolved && w.level < 4).map(w => {
        const upgrade = createLoadoutWeaponState(w.weaponId, w.level + 1, w);
        return { id: createId("chest-upgrade"), kind: "weapon" as const, weaponId: w.weaponId, targetWeaponInstanceId: w.weaponInstanceId,
          title: upgrade.displayName, description: upgrade.description, targetLevel: upgrade.level,
          cost: (upgrade.sellValue ?? 1) * 2, affordable: true, purchased: false };
      });
      const offer = offers[Math.floor(random(state) * offers.length)];
      const core = o.kind === "rare_chest" || !offer;
      state = openSurvivalPause(state, "chest");
      s.chestReward = { id: o.id, playerId: lootPlayer.playerId, core, offer: core ? undefined : offer, salvageGold: core ? 35 : Math.max(8, Math.ceil(offer.cost * 0.5)) };
      const owner = state.players.find(p => p.playerId === lootPlayer.playerId)!;
      if (!core) owner.shop = { ...createEmptyShopState(), available: true, offers: [{ ...offer, cost: 0, affordable: true }] };
      return state;
    }
  }
  return state;
}
