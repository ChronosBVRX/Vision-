import { localizeSpell, localizeGestureReason } from "../text.js";
import {
  createBaseRoundState,
  roundPhaseDurations,
  transitionRoundState,
  type BaseRoundState,
  type ScoreEntry,
  type ServerGame,
  type ServerGameContext,
  type SupportedLanguage
} from "@open-party-lab/game-core";
import {
  defaultMageDuelLoadoutSpellIds,
  getMageDuelSpellDefinition,
  mageDuelSpellCatalog,
  recognizeMageDuelGesture,
  type MageDuelActiveCastState,
  type MageDuelControllerState,
  type MageDuelDuelistState,
  type MageDuelEffectKind,
  type MageDuelEffectState,
  type MageDuelInput,
  type MageDuelProjectileState,
  type MageDuelPublicState,
  type MageDuelSpellDefinition,
  type MageDuelSpellId
} from "../protocol.js";
import { magicDuellManifest } from "../manifest.js";

interface MageDuel3dState extends BaseRoundState, MageDuelPublicState {
  tick: number;
  nextCastSequence: number;
  nextEffectSequence: number;
  nextProjectileSequence: number;
}

const maxActionLogEntries = 8;
const maxEffects = 24;
const maxProjectiles = 12;
const duelDistance = 8;

const spellBook = mageDuelSpellCatalog;

const mageDuelText = {
  de: {
    intro: "Magic Duell: Zeichne Zauber auf deinem Handy.",
    start: "Das Duell beginnt. Mana regeneriert automatisch.",
    fallbackPlayer: "Magier",
    unknownGesture: (name: string, reason: string) => `${name}: ${reason}.`,
    noMana: (name: string, spell: string) => `${name} hat zu wenig Mana fuer ${spell}.`,
    cooldown: (name: string, spell: string) => `${spell} ist fuer ${name} noch nicht bereit.`,
    casting: (name: string, spell: string) => `${name} wirkt ${spell}.`,
    fire: (name: string, target: string, damage: number) => `${name} trifft ${target} fuer ${damage} Schaden.`,
    shield: (name: string) => `${name} ruft ein Schild hervor.`,
    heal: (name: string, heal: number) => `${name} heilt ${heal} Lebenspunkte.`,
    regeneration: (name: string) => `${name} beginnt zu regenerieren.`,
    fireWall: (name: string) => `${name} entfacht eine Feuerwand.`,
    manaSurge: (name: string, mana: number) => `${name} sammelt ${mana} Mana.`,
    projectile: (name: string, spell: string) => `${name} schleudert ${spell}.`,
    fizzled: (name: string) => `${name}s Zauber verpufft.`,
    wins: (name: string) => `${name} gewinnt das Magierduell!`,
    draw: "Das Duell endet unentschieden."
  },
  en: {
    intro: "Magic Duel: Draw spells on your phone.",
    start: "The duel begins. Mana regenerates automatically.",
    fallbackPlayer: "Mage",
    unknownGesture: (name: string, reason: string) => `${name}: ${reason}.`,
    noMana: (name: string, spell: string) => `${name} lacks mana for ${spell}.`,
    cooldown: (name: string, spell: string) => `${spell} is not ready for ${name} yet.`,
    casting: (name: string, spell: string) => `${name} casts ${spell}.`,
    fire: (name: string, target: string, damage: number) => `${name} hits ${target} for ${damage} damage.`,
    shield: (name: string) => `${name} summons a shield.`,
    heal: (name: string, heal: number) => `${name} restores ${heal} health.`,
    regeneration: (name: string) => `${name} starts regenerating.`,
    fireWall: (name: string) => `${name} raises a fire wall.`,
    manaSurge: (name: string, mana: number) => `${name} gathers ${mana} mana.`,
    projectile: (name: string, spell: string) => `${name} launches ${spell}.`,
    fizzled: (name: string) => `${name}'s spell fizzles.`,
    wins: (name: string) => `${name} wins the mage duel!`,
    draw: "The duel ends in a draw."
  }
} satisfies Record<SupportedLanguage, {
  intro: string;
  start: string;
  fallbackPlayer: string;
  unknownGesture: (name: string, reason: string) => string;
  noMana: (name: string, spell: string) => string;
  cooldown: (name: string, spell: string) => string;
  casting: (name: string, spell: string) => string;
  fire: (name: string, target: string, damage: number) => string;
  shield: (name: string) => string;
  heal: (name: string, heal: number) => string;
  regeneration: (name: string) => string;
  fireWall: (name: string) => string;
  manaSurge: (name: string, mana: number) => string;
  projectile: (name: string, spell: string) => string;
  fizzled: (name: string) => string;
  wins: (name: string) => string;
  draw: string;
}>;

function getSpell(spellId: MageDuelSpellId): MageDuelSpellDefinition {
  const spell = getMageDuelSpellDefinition(spellId);

  if (!spell) {
    throw new Error(`Unknown Mage Duel spell "${spellId}".`);
  }

  return spell;
}

function appendLog(state: MageDuel3dState, entry: string): string[] {
  return [...state.actionLog, entry].slice(-maxActionLogEntries);
}

function clampQuality(quality: number): number {
  return Math.max(0, Math.min(1, quality));
}

function scaleByQuality(value: number | undefined, quality: number): number {
  return Math.max(0, value ?? 0) * clampQuality(quality);
}

function formatAmount(value: number): number {
  return Math.max(0, Math.round(value));
}

function createEffect(
  state: MageDuel3dState,
  kind: MageDuelEffectKind,
  spellId: MageDuelSpellId | null,
  ownerPlayerId: string,
  targetPlayerId: string | null,
  now: number,
  durationMs: number
): MageDuelEffectState {
  return {
    id: `fx-${state.nextEffectSequence}`,
    kind,
    spellId,
    ownerPlayerId,
    targetPlayerId,
    createdAt: now,
    endsAt: now + durationMs
  };
}

function withEffect(state: MageDuel3dState, effect: MageDuelEffectState): MageDuel3dState {
  return {
    ...state,
    nextEffectSequence: state.nextEffectSequence + 1,
    effects: [...state.effects, effect].slice(-maxEffects)
  };
}

function createProjectile(
  state: MageDuel3dState,
  spell: MageDuelSpellDefinition,
  ownerPlayerId: string,
  targetPlayerId: string,
  damage: number,
  quality: number,
  now: number
): MageDuelProjectileState {
  return {
    id: `projectile-${state.nextProjectileSequence}`,
    spellId: spell.id,
    ownerPlayerId,
    targetPlayerId,
    createdAt: now,
    impactAt: now + (spell.projectileTravelMs ?? 600),
    damage,
    quality,
    manaBurn: scaleByQuality(spell.manaBurn, quality),
    lifeSteal: scaleByQuality(spell.lifeSteal, quality),
    damageOverTime: scaleByQuality(spell.damageOverTime, quality),
    damageDurationMs: spell.damageDurationMs,
    shieldPiercePct: spell.shieldPiercePct
  };
}

function createCooldownReadyAt(now: number): Record<MageDuelSpellId, number> {
  return Object.fromEntries(spellBook.map((spell) => [spell.id, now])) as Record<MageDuelSpellId, number>;
}

function withProjectile(state: MageDuel3dState, projectile: MageDuelProjectileState): MageDuel3dState {
  return {
    ...state,
    nextProjectileSequence: state.nextProjectileSequence + 1,
    projectiles: [...state.projectiles, projectile].slice(-maxProjectiles)
  };
}

function createDuelists(context: ServerGameContext): MageDuelDuelistState[] {
  return context.players.slice(0, 2).map((player, index) => ({
    playerId: player.id,
    name: player.name || `${mageDuelText[context.language].fallbackPlayer} ${index + 1}`,
    color: player.color || (index === 0 ? "#38bdf8" : "#fb7185"),
    slot: index as 0 | 1,
    loadoutSpellIds: sanitizeLoadout(resolvePlayerLoadout(player)),
    hp: 100,
    maxHp: 100,
    mana: 100,
    maxMana: 100,
    manaRegenPerSecond: 5,
    shieldHp: 0,
    shieldEndsAt: null,
    fireWallEndsAt: null,
    fireWallBonusDamage: 0,
    regeneration: null,
    damageOverTime: [],
    manaRegenBoostEndsAt: null,
    manaRegenBonusPerSecond: 0,
    cooldownReadyAt: createCooldownReadyAt(context.now),
    activeCast: null,
    lastGesture: null
  }));
}

function resolvePlayerLoadout(player: ServerGameContext["players"][number]): readonly string[] | undefined {
  const setupLoadout = player.setupSelections?.spellLoadout;

  if (Array.isArray(setupLoadout)) {
    return setupLoadout;
  }

  return (player as { mageDuelLoadoutSpellIds?: readonly string[] }).mageDuelLoadoutSpellIds;
}

function sanitizeLoadout(spellIds: readonly string[] | undefined): MageDuelSpellId[] {
  const validSpellIds = new Set(spellBook.map((spell) => spell.id));
  const normalized = [...new Set(spellIds ?? defaultMageDuelLoadoutSpellIds)]
    .filter((spellId): spellId is MageDuelSpellId => validSpellIds.has(spellId as MageDuelSpellId));

  return normalized.length > 0 ? normalized : [...defaultMageDuelLoadoutSpellIds];
}

function getOpponent(state: MageDuel3dState, playerId: string): MageDuelDuelistState | undefined {
  return state.duelists.find((duelist) => duelist.playerId !== playerId && duelist.hp > 0);
}

function isAlive(duelist: MageDuelDuelistState): boolean {
  return duelist.hp > 0;
}

function createCast(
  state: MageDuel3dState,
  spell: MageDuelSpellDefinition,
  targetPlayerId: string | null,
  quality: number,
  now: number
): MageDuelActiveCastState {
  return {
    id: `cast-${state.nextCastSequence}`,
    spellId: spell.id,
    startedAt: now,
    resolvesAt: now + spell.castMs,
    targetPlayerId,
    quality: clampQuality(quality)
  };
}

function updateDuelist(
  state: MageDuel3dState,
  playerId: string,
  updater: (duelist: MageDuelDuelistState) => MageDuelDuelistState
): MageDuelDuelistState[] {
  return state.duelists.map((duelist) => (duelist.playerId === playerId ? updater(duelist) : duelist));
}

function pruneEffects(state: MageDuel3dState, now: number): MageDuelEffectState[] {
  return state.effects.filter((effect) => effect.endsAt > now);
}

function regenAndExpire(state: MageDuel3dState, deltaMs: number, now: number): MageDuelDuelistState[] {
  const seconds = Math.max(0, deltaMs / 1000);

  return state.duelists.map((duelist) => {
    const shieldExpired = duelist.shieldEndsAt !== null && duelist.shieldEndsAt <= now;
    const fireWallExpired = duelist.fireWallEndsAt !== null && duelist.fireWallEndsAt <= now;
    const manaBoostExpired = duelist.manaRegenBoostEndsAt !== null && duelist.manaRegenBoostEndsAt <= now;
    const manaRegenBonus = manaBoostExpired ? 0 : duelist.manaRegenBonusPerSecond;
    const mana = Math.min(duelist.maxMana, duelist.mana + (duelist.manaRegenPerSecond + manaRegenBonus) * seconds);
    let hp = duelist.hp;
    let regeneration = duelist.regeneration;
    const damageOverTime = duelist.damageOverTime
      .map((dot) => {
        const duration = Math.max(1, dot.endsAt - dot.startedAt);
        const elapsedRatio = Math.min(1, Math.max(0, (now - dot.startedAt) / duration));
        const targetAppliedDamage = dot.totalDamage * elapsedRatio;
        const damageDelta = Math.max(0, targetAppliedDamage - dot.appliedDamage);
        hp = Math.max(0, hp - damageDelta);

        return {
          ...dot,
          appliedDamage: dot.appliedDamage + damageDelta
        };
      })
      .filter((dot) => dot.endsAt > now && hp > 0);

    if (regeneration) {
      const duration = Math.max(1, regeneration.endsAt - regeneration.startedAt);
      const elapsedRatio = Math.min(1, Math.max(0, (now - regeneration.startedAt) / duration));
      const targetAppliedHeal = regeneration.totalHeal * elapsedRatio;
      const healDelta = Math.max(0, targetAppliedHeal - regeneration.appliedHeal);
      hp = Math.min(duelist.maxHp, hp + healDelta);
      regeneration =
        now >= regeneration.endsAt
          ? null
          : {
              ...regeneration,
              appliedHeal: regeneration.appliedHeal + healDelta
            };
    }

    return {
      ...duelist,
      hp,
      mana,
      shieldHp: shieldExpired ? 0 : duelist.shieldHp,
      shieldEndsAt: shieldExpired ? null : duelist.shieldEndsAt,
      fireWallEndsAt: fireWallExpired ? null : duelist.fireWallEndsAt,
      fireWallBonusDamage: fireWallExpired ? 0 : duelist.fireWallBonusDamage,
      damageOverTime,
      manaRegenBoostEndsAt: manaBoostExpired ? null : duelist.manaRegenBoostEndsAt,
      manaRegenBonusPerSecond: manaBoostExpired ? 0 : duelist.manaRegenBonusPerSecond,
      regeneration
    };
  });
}

function applyProjectileImpact(
  state: MageDuel3dState,
  projectile: MageDuelProjectileState,
  context: ServerGameContext
): MageDuel3dState {
  const caster = state.duelists.find((duelist) => duelist.playerId === projectile.ownerPlayerId);
  const target = state.duelists.find((duelist) => duelist.playerId === projectile.targetPlayerId && isAlive(duelist));

  if (!caster || !target) {
    return {
      ...state,
      projectiles: state.projectiles.filter((entry) => entry.id !== projectile.id)
    };
  }

  const spell = getSpell(projectile.spellId);
  const damage = projectile.damage;
  const shieldPiercePct = Math.max(0, Math.min(1, projectile.shieldPiercePct ?? 0));
  const piercingDamage = damage * shieldPiercePct;
  const shieldableDamage = damage - piercingDamage;
  const absorbed = Math.min(target.shieldHp, shieldableDamage);
  const remainingDamage = shieldableDamage - absorbed + piercingDamage;
  const manaBurn = projectile.manaBurn ?? 0;
  const lifeSteal = projectile.lifeSteal ?? 0;
  const damageOverTime = projectile.damageOverTime ?? 0;
  const nextTarget: MageDuelDuelistState = {
    ...target,
    shieldHp: Math.max(0, target.shieldHp - absorbed),
    shieldEndsAt: target.shieldHp - absorbed <= 0 ? null : target.shieldEndsAt,
    hp: Math.max(0, target.hp - remainingDamage),
    mana: Math.max(0, target.mana - manaBurn),
    damageOverTime:
      damageOverTime > 0
        ? [
            ...target.damageOverTime,
            {
              id: `dot-${projectile.id}`,
              spellId: spell.id,
              startedAt: context.now,
              endsAt: context.now + (projectile.damageDurationMs ?? 1),
              totalDamage: damageOverTime,
              appliedDamage: 0
            }
          ]
        : target.damageOverTime
  };
  const nextCaster: MageDuelDuelistState = {
    ...caster,
    hp: Math.min(caster.maxHp, caster.hp + lifeSteal)
  };
  const duelists = state.duelists.map((duelist) =>
    duelist.playerId === target.playerId
        ? nextTarget
        : duelist.playerId === caster.playerId
          ? nextCaster
        : duelist
  );
  const message = mageDuelText[context.language].fire(caster.name, target.name, formatAmount(remainingDamage));

  let nextState = withEffect(
    {
      ...state,
      duelists,
      projectiles: state.projectiles.filter((entry) => entry.id !== projectile.id),
      actionLog: appendLog(state, message),
      message,
      updatedAt: context.now
    },
    createEffect(state, "impact", spell.id, caster.playerId, target.playerId, context.now, 650)
  );

  if (manaBurn > 0) {
    nextState = withEffect(
      nextState,
      createEffect(nextState, "mana", spell.id, caster.playerId, target.playerId, context.now, 750)
    );
  }

  if (damageOverTime > 0) {
    nextState = withEffect(
      nextState,
      createEffect(nextState, "dot", spell.id, caster.playerId, target.playerId, context.now, projectile.damageDurationMs ?? 1_000)
    );
  }

  return nextState;
}

function launchProjectile(
  state: MageDuel3dState,
  caster: MageDuelDuelistState,
  target: MageDuelDuelistState,
  spell: MageDuelSpellDefinition,
  context: ServerGameContext
): MageDuel3dState {
  const fireWallActive = caster.fireWallEndsAt !== null && caster.fireWallEndsAt > context.now;
  const quality = caster.activeCast?.quality ?? 1;
  const damage = scaleByQuality(spell.damage, quality) + (fireWallActive ? caster.fireWallBonusDamage : 0);
  const message = mageDuelText[context.language].projectile(caster.name, localizeSpell(spell, context.language).displayName);
  const duelists = updateDuelist(state, caster.playerId, (duelist) => ({
    ...duelist,
    activeCast: null
  }));
  const projectile = createProjectile(
    { ...state, duelists },
    spell,
    caster.playerId,
    target.playerId,
    damage,
    quality,
    context.now
  );

  return withProjectile(
    {
      ...state,
      duelists,
      actionLog: appendLog(state, message),
      message,
      updatedAt: context.now
    },
    projectile
  );
}

function applySelfSpellExtras(
  duelist: MageDuelDuelistState,
  spell: MageDuelSpellDefinition,
  quality: number,
  now: number
): MageDuelDuelistState {
  const cooldownReductionMs = scaleByQuality(spell.cooldownReductionMs, quality);
  const manaRegenBoostPerSecond = scaleByQuality(spell.manaRegenBoostPerSecond, quality);
  const cooldownReadyAt =
    cooldownReductionMs > 0
      ? Object.fromEntries(
          Object.entries(duelist.cooldownReadyAt).map(([spellId, readyAt]) => [
            spellId,
            Math.max(now, readyAt - cooldownReductionMs)
          ])
        ) as Record<MageDuelSpellId, number>
      : duelist.cooldownReadyAt;

  return {
    ...duelist,
    cooldownReadyAt,
    damageOverTime: spell.cleanse ? [] : duelist.damageOverTime,
    manaRegenBoostEndsAt:
      manaRegenBoostPerSecond > 0
        ? now + (spell.manaRegenBoostDurationMs ?? 1)
        : duelist.manaRegenBoostEndsAt,
    manaRegenBonusPerSecond:
      manaRegenBoostPerSecond > 0 ? manaRegenBoostPerSecond : duelist.manaRegenBonusPerSecond
  };
}

function withSelfExtraEffects(
  state: MageDuel3dState,
  spell: MageDuelSpellDefinition,
  caster: MageDuelDuelistState,
  context: ServerGameContext
): MageDuel3dState {
  let nextState = state;

  if (spell.cleanse) {
    nextState = withEffect(
      nextState,
      createEffect(nextState, "cleanse", spell.id, caster.playerId, caster.playerId, context.now, 800)
    );
  }

  if (spell.manaRegenBoostPerSecond !== undefined) {
    nextState = withEffect(
      nextState,
      createEffect(
        nextState,
        "boost",
        spell.id,
        caster.playerId,
        caster.playerId,
        context.now,
        spell.manaRegenBoostDurationMs ?? 1_000
      )
    );
  }

  return nextState;
}

function applyShield(
  state: MageDuel3dState,
  caster: MageDuelDuelistState,
  spell: MageDuelSpellDefinition,
  context: ServerGameContext
): MageDuel3dState {
  const shieldHp = scaleByQuality(spell.shieldHp, caster.activeCast?.quality ?? 1);
  const quality = caster.activeCast?.quality ?? 1;
  const message = mageDuelText[context.language].shield(caster.name);
  const duelists = updateDuelist(state, caster.playerId, (duelist) =>
    applySelfSpellExtras(
      {
        ...duelist,
        activeCast: null,
        shieldHp: Math.max(duelist.shieldHp, shieldHp),
        shieldEndsAt: context.now + (spell.shieldDurationMs ?? 0)
      },
      spell,
      quality,
      context.now
    )
  );

  return withSelfExtraEffects(
    withEffect(
      {
        ...state,
        duelists,
        actionLog: appendLog(state, message),
        message,
        updatedAt: context.now
      },
      createEffect(state, "shield", spell.id, caster.playerId, caster.playerId, context.now, spell.shieldDurationMs ?? 900)
    ),
    spell,
    caster,
    context
  );
}

function applyHeal(
  state: MageDuel3dState,
  caster: MageDuelDuelistState,
  spell: MageDuelSpellDefinition,
  context: ServerGameContext
): MageDuel3dState {
  const heal = scaleByQuality(spell.heal, caster.activeCast?.quality ?? 1);
  const quality = caster.activeCast?.quality ?? 1;
  const totalHealOverTime = scaleByQuality(spell.healOverTime, quality);
  const message = mageDuelText[context.language].heal(caster.name, formatAmount(heal));
  const duelists = updateDuelist(state, caster.playerId, (duelist) =>
    applySelfSpellExtras(
      {
        ...duelist,
        activeCast: null,
        hp: Math.min(duelist.maxHp, duelist.hp + heal),
        regeneration:
          totalHealOverTime > 0
            ? {
                id: `regen-${state.nextCastSequence}`,
                startedAt: context.now,
                endsAt: context.now + (spell.healDurationMs ?? 1),
                totalHeal: totalHealOverTime,
                appliedHeal: 0
              }
            : duelist.regeneration
      },
      spell,
      quality,
      context.now
    )
  );

  return withSelfExtraEffects(
    withEffect(
      {
        ...state,
        duelists,
        actionLog: appendLog(state, message),
        message,
        updatedAt: context.now
      },
      createEffect(state, "heal", spell.id, caster.playerId, caster.playerId, context.now, 850)
    ),
    spell,
    caster,
    context
  );
}

function applyRegeneration(
  state: MageDuel3dState,
  caster: MageDuelDuelistState,
  spell: MageDuelSpellDefinition,
  context: ServerGameContext
): MageDuel3dState {
  const totalHeal = scaleByQuality(spell.healOverTime, caster.activeCast?.quality ?? 1);
  const message = mageDuelText[context.language].regeneration(caster.name);
  const duelists = updateDuelist(state, caster.playerId, (duelist) => ({
    ...duelist,
    activeCast: null,
    regeneration: {
      id: `regen-${state.nextCastSequence}`,
      startedAt: context.now,
      endsAt: context.now + (spell.healDurationMs ?? 1),
      totalHeal,
      appliedHeal: 0
    }
  }));

  return withEffect(
    {
      ...state,
      duelists,
      actionLog: appendLog(state, message),
      message,
      updatedAt: context.now
    },
    createEffect(state, "heal", spell.id, caster.playerId, caster.playerId, context.now, 1_150)
  );
}

function applyFireWall(
  state: MageDuel3dState,
  caster: MageDuelDuelistState,
  spell: MageDuelSpellDefinition,
  context: ServerGameContext
): MageDuel3dState {
  const bonusDamage = scaleByQuality(spell.fireBonusDamage, caster.activeCast?.quality ?? 1);
  const message = mageDuelText[context.language].fireWall(caster.name);
  const duelists = updateDuelist(state, caster.playerId, (duelist) => ({
    ...duelist,
    activeCast: null,
    fireWallEndsAt: context.now + (spell.fireWallDurationMs ?? 0),
    fireWallBonusDamage: bonusDamage
  }));

  return withEffect(
    {
      ...state,
      duelists,
      actionLog: appendLog(state, message),
      message,
      updatedAt: context.now
    },
    createEffect(state, "fire_wall", spell.id, caster.playerId, caster.playerId, context.now, spell.fireWallDurationMs ?? 900)
  );
}

function applyManaSurge(
  state: MageDuel3dState,
  caster: MageDuelDuelistState,
  spell: MageDuelSpellDefinition,
  context: ServerGameContext
): MageDuel3dState {
  const mana = scaleByQuality(spell.manaRestore, caster.activeCast?.quality ?? 1);
  const quality = caster.activeCast?.quality ?? 1;
  const message = mageDuelText[context.language].manaSurge(caster.name, formatAmount(mana));
  const duelists = updateDuelist(state, caster.playerId, (duelist) =>
    applySelfSpellExtras(
      {
        ...duelist,
        activeCast: null,
        mana: Math.min(duelist.maxMana, duelist.mana + mana)
      },
      spell,
      quality,
      context.now
    )
  );

  return withSelfExtraEffects(
    withEffect(
      {
        ...state,
        duelists,
        actionLog: appendLog(state, message),
        message,
        updatedAt: context.now
      },
      createEffect(state, "mana", spell.id, caster.playerId, caster.playerId, context.now, 950)
    ),
    spell,
    caster,
    context
  );
}

function fizzleCast(
  state: MageDuel3dState,
  caster: MageDuelDuelistState,
  context: ServerGameContext
): MageDuel3dState {
  const message = mageDuelText[context.language].fizzled(caster.name);
  const duelists = updateDuelist(state, caster.playerId, (duelist) => ({
    ...duelist,
    activeCast: null
  }));

  return withEffect(
    {
      ...state,
      duelists,
      actionLog: appendLog(state, message),
      message,
      updatedAt: context.now
    },
    createEffect(state, "fizzle", null, caster.playerId, caster.playerId, context.now, 450)
  );
}

function resolveCast(
  state: MageDuel3dState,
  caster: MageDuelDuelistState,
  context: ServerGameContext
): MageDuel3dState {
  const cast = caster.activeCast;

  if (!cast || cast.resolvesAt > context.now || !isAlive(caster)) {
    return state;
  }

  const spell = getSpell(cast.spellId);

  if (spell.shieldHp !== undefined) {
    return applyShield(state, caster, spell, context);
  }

  if (spell.heal !== undefined) {
    return applyHeal(state, caster, spell, context);
  }

  if (spell.healOverTime !== undefined) {
    return applyRegeneration(state, caster, spell, context);
  }

  if (spell.fireWallDurationMs !== undefined) {
    return applyFireWall(state, caster, spell, context);
  }

  if (spell.manaRestore !== undefined) {
    return applyManaSurge(state, caster, spell, context);
  }

  const target = state.duelists.find((duelist) => duelist.playerId === cast.targetPlayerId && isAlive(duelist));

  if (!target) {
    return fizzleCast(state, caster, context);
  }

  return launchProjectile(state, caster, target, spell, context);
}

function finishIfNeeded(state: MageDuel3dState, context: ServerGameContext): MageDuel3dState {
  if (state.phase !== "playing") {
    return state;
  }

  const aliveDuelists = state.duelists.filter(isAlive);

  if (aliveDuelists.length <= 1) {
    const winner = aliveDuelists[0] ?? null;
    const message = winner ? mageDuelText[context.language].wins(winner.name) : mageDuelText[context.language].draw;

    return transitionRoundState(
      {
        ...state,
        winnerPlayerId: winner?.playerId,
        winnerName: winner?.name,
        isDraw: !winner,
        actionLog: appendLog(state, message)
      },
      "locked",
      context.now,
      {
        durationMs: roundPhaseDurations.lockedMs,
        message
      }
    );
  }

  return state;
}

function toPublicState(state: MageDuel3dState): MageDuelPublicState {
  return {
    roundEndsAt: state.roundEndsAt,
    duelists: state.duelists,
    spellBook: state.spellBook,
    projectiles: state.projectiles,
    effects: state.effects,
    actionLog: state.actionLog,
    winnerPlayerId: state.winnerPlayerId,
    winnerName: state.winnerName,
    isDraw: state.isDraw
  };
}

function buildScore(state: MageDuel3dState): ScoreEntry[] {
  return state.winnerPlayerId ? [{ playerId: state.winnerPlayerId, delta: 1, reason: "Mage Duel Sieg" }] : [];
}

export const mageDuel3dServerGame: ServerGame<
  MageDuel3dState,
  MageDuelInput,
  MageDuelPublicState | MageDuelControllerState
> = {
  manifest: magicDuellManifest,
  createInitialState(context) {
    return {
      ...createBaseRoundState("round_intro", context.now, {
        durationMs: roundPhaseDurations.roundIntroMs,
        message: mageDuelText[context.language].intro
      }),
      roundEndsAt: null,
      duelists: createDuelists(context),
      spellBook: spellBook.map((spell) => localizeSpell(spell, context.language)),
      projectiles: [],
      effects: [],
      actionLog: [],
      winnerPlayerId: undefined,
      winnerName: undefined,
      isDraw: false,
      tick: 0,
      nextCastSequence: 1,
      nextEffectSequence: 1,
      nextProjectileSequence: 1
    };
  },
  startRound(state, context) {
    return transitionRoundState(
      {
        ...state,
        roundEndsAt: null,
        duelists: createDuelists(context),
        projectiles: [],
        effects: [],
        actionLog: [],
        winnerPlayerId: undefined,
        winnerName: undefined,
        isDraw: false,
        tick: 0,
        nextCastSequence: 1,
        nextEffectSequence: 1,
        nextProjectileSequence: 1
      },
      "playing",
      context.now,
      {
        startedAt: context.now,
        message: mageDuelText[context.language].start
      }
    );
  },
  handleInput(state, input, context) {
    if (state.phase !== "playing" || input.type !== "spell:gesture") {
      return state;
    }

    const caster = state.duelists.find((duelist) => duelist.playerId === input.playerId);

    if (!caster || !isAlive(caster) || caster.activeCast) {
      return state;
    }

    const spell = getSpell(input.spellId);
    const recognition = recognizeMageDuelGesture(spell.color, input.points);
    const gestureMatchesSpell =
      recognition.shape === spell.shape &&
      spell.color === input.color &&
      recognition.confidence >= 0.5;
    const lastGesture = {
      color: spell.color,
      spellId: spell.id,
      confidence: recognition.confidence,
      reason: localizeGestureReason(gestureMatchesSpell ? recognition.reason : "Zauber misslungen", context.language),
      at: context.now
    };

    if (!caster.loadoutSpellIds.includes(spell.id)) {
      return state;
    }

    if (caster.mana < spell.manaCost) {
      const message = mageDuelText[context.language].noMana(caster.name, localizeSpell(spell, context.language).displayName);

      return {
        ...state,
        duelists: updateDuelist(state, caster.playerId, (duelist) => ({
          ...duelist,
          lastGesture
        })),
        actionLog: appendLog(state, message),
        message,
        updatedAt: context.now
      };
    }

    if (caster.cooldownReadyAt[spell.id] > context.now) {
      const message = mageDuelText[context.language].cooldown(caster.name, localizeSpell(spell, context.language).displayName);

      return {
        ...state,
        duelists: updateDuelist(state, caster.playerId, (duelist) => ({
          ...duelist,
          lastGesture
        })),
        actionLog: appendLog(state, message),
        message,
        updatedAt: context.now
      };
    }

    if (!gestureMatchesSpell) {
      const message = mageDuelText[context.language].unknownGesture(caster.name, lastGesture.reason);

      return withEffect(
        {
          ...state,
          duelists: updateDuelist(state, caster.playerId, (duelist) => ({
            ...duelist,
            mana: Math.max(0, duelist.mana - spell.manaCost),
            lastGesture
          })),
          actionLog: appendLog(state, message),
          message,
          updatedAt: context.now
        },
        createEffect(state, "fizzle", null, caster.playerId, caster.playerId, context.now, 520)
      );
    }

    const target = spell.damage !== undefined ? getOpponent(state, caster.playerId) : undefined;
    const activeCast = createCast(state, spell, target?.playerId ?? null, recognition.confidence, context.now);
    const message = mageDuelText[context.language].casting(caster.name, localizeSpell(spell, context.language).displayName);
    const duelists = updateDuelist(state, caster.playerId, (duelist) => ({
      ...duelist,
      mana: Math.max(0, duelist.mana - spell.manaCost),
      cooldownReadyAt: {
        ...duelist.cooldownReadyAt,
        [spell.id]: context.now + spell.cooldownMs
      },
      activeCast,
      lastGesture
    }));

    return withEffect(
      {
        ...state,
        duelists,
        nextCastSequence: state.nextCastSequence + 1,
        actionLog: appendLog(state, message),
        message,
        updatedAt: context.now
      },
      createEffect(state, "cast", spell.id, caster.playerId, target?.playerId ?? caster.playerId, context.now, spell.castMs)
    );
  },
  tick(state, deltaMs, context) {
    if (state.phase !== "playing") {
      return state;
    }

    let nextState: MageDuel3dState = {
      ...state,
      tick: state.tick + 1,
      duelists: regenAndExpire(state, deltaMs, context.now),
      effects: pruneEffects(state, context.now),
      updatedAt: context.now
    };

    for (const duelist of nextState.duelists) {
      nextState = resolveCast(nextState, duelist, context);
    }

    for (const projectile of [...nextState.projectiles]) {
      if (projectile.impactAt <= context.now) {
        nextState = applyProjectileImpact(nextState, projectile, context);
      }
    }

    return finishIfNeeded(nextState, context);
  },
  isRoundFinished(state) {
    return state.phase === "locked";
  },
  buildScore(state, context) {
    return buildScore(state).map(entry => ({ ...entry, reason: context.language === "en" ? "Magic Duel victory" : "Magic Duell Sieg" }));
  },
  toPublicState(state) {
    return toPublicState(state);
  },
  toControllerStateForPlayer(state, _context, playerId) {
    return {
      ...toPublicState(state),
      ownPlayerId: playerId
    };
  }
};

export const mageDuel3dDebug = {
  duelDistance
} as const;
