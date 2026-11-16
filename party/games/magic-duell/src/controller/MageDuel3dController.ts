import { getMageDuelShapeIconPath } from "../protocol.js";
import type {
  MageDuelControllerState,
  MageDuelDuelistState,
  MageDuelSpellDefinition,
  MageDuelSpellId
} from "../protocol.js";
import type { SpellCastingDuelistModel, SpellCastingLayoutModel } from "./platform-layout-models.js";
import type { ControllerGameRenderContext } from "./platform-registry.js";
import { magicDuellManifest } from "../manifest.js";
import { createMageDuelGestureInput } from "./mageDuel3dBindings.js";

function getSpellLabel(spellBook: MageDuelSpellDefinition[], spellId: MageDuelSpellId | null, en: boolean): string {
  if (!spellId) {
    return en ? "Unclear" : "Unklar";
  }

  return spellBook.find((spell) => spell.id === spellId)?.displayName ?? spellId;
}

function buildDuelistModel(
  duelist: MageDuelDuelistState | undefined,
  spellBook: MageDuelSpellDefinition[],
  now: number,
  en: boolean
): SpellCastingDuelistModel | null {
  if (!duelist) {
    return null;
  }

  const activeSpell = duelist.activeCast
    ? spellBook.find((spell) => spell.id === duelist.activeCast?.spellId)
    : undefined;
  const activeCastRemainingMs = duelist.activeCast
    ? Math.max(0, duelist.activeCast.resolvesAt - now)
    : 0;

  return {
    playerId: duelist.playerId,
    name: duelist.name,
    color: duelist.color,
    hp: duelist.hp,
    maxHp: duelist.maxHp,
    mana: duelist.mana,
    maxMana: duelist.maxMana,
    shieldHp: duelist.shieldHp,
    shieldRemainingMs: duelist.shieldEndsAt ? Math.max(0, duelist.shieldEndsAt - now) : 0,
    activeCastLabel: activeSpell ? `${activeSpell.displayName} ${(activeCastRemainingMs / 1000).toFixed(1)}s` : undefined,
    lastGesture: duelist.lastGesture
      ? {
          spellLabel: getSpellLabel(spellBook, duelist.lastGesture.spellId, en),
          confidence: duelist.lastGesture.confidence,
          reason: duelist.lastGesture.reason
        }
      : undefined
  };
}

export function buildMageDuel3dControllerModel(
  context: ControllerGameRenderContext
): SpellCastingLayoutModel {
  const { state, onInput } = context;
  const en = state.room?.language === "en";
  const now = Date.now();
  const playerId = state.player?.id ?? "";
  const gameState = (state.game?.state ?? {}) as Partial<MageDuelControllerState>;
  const spellBook = [...(gameState.spellBook ?? [])];
  const self = gameState.duelists?.find((duelist) => duelist.playerId === playerId);
  const opponent = gameState.duelists?.find((duelist) => duelist.playerId !== playerId);
  const title =
    state.room?.availableGames?.find((game) => game.id === magicDuellManifest.id)?.displayName ??
    magicDuellManifest.displayName;
  const busy = Boolean(self?.activeCast);
  const dead = Boolean(self && self.hp <= 0);
  const nextSpellReadyMs = self?.activeCast ? Math.max(0, self.activeCast.resolvesAt - now) : 0;
  const nextSpellTotalMs = self?.activeCast
    ? Math.max(1, self.activeCast.resolvesAt - self.activeCast.startedAt)
    : 0;
  const loadoutSpellIds = self?.loadoutSpellIds?.length
    ? self.loadoutSpellIds
    : spellBook.map((spell) => spell.id);
  const loadoutSpells = loadoutSpellIds
    .map((spellId) => spellBook.find((spell) => spell.id === spellId))
    .filter((spell): spell is MageDuelSpellDefinition => Boolean(spell));
  const selectedSpellId =
    self?.lastGesture?.spellId && loadoutSpellIds.includes(self.lastGesture.spellId)
      ? self.lastGesture.spellId
      : loadoutSpells[0]?.id ?? null;

  return {
    kind: "spell_casting",
    title,
    subtitle: undefined,
    helperText:
      state.game?.message ??
      (en
        ? "Choose a spell, draw its symbol, and release."
        : "Waehle einen Zauber, zeichne sein Symbol und lasse los."),
    language: state.room?.language,
    disabled: state.game?.phase !== "playing" || !self || !opponent || busy || dead,
    resetKey: `${state.game?.roundNumber ?? 0}:${state.game?.phase ?? "idle"}:${self?.activeCast?.id ?? "ready"}`,
    nextSpellReadyMs,
    nextSpellTotalMs,
    selectedSpellId,
    self: buildDuelistModel(self, spellBook, now, en),
    opponent: buildDuelistModel(opponent, spellBook, now, en),
    spells: loadoutSpells.map((spell) => {
      const cooldownRemainingMs = self ? Math.max(0, (self.cooldownReadyAt?.[spell.id] ?? 0) - now) : 0;
      const lacksMana = self ? self.mana < spell.manaCost : true;

      return {
        id: spell.id,
        label: spell.displayName,
        description: spell.description,
        color: spell.color,
        shape: spell.shape,
        iconPath: getMageDuelShapeIconPath(spell.shape),
        manaCost: spell.manaCost,
        cooldownMs: spell.cooldownMs,
        cooldownRemainingMs,
        castMs: spell.castMs,
        disabled: cooldownRemainingMs > 0 || lacksMana || busy || dead
      };
    }),
    actionLog: gameState.actionLog ?? [],
    onCastGesture: (spellId, points) => {
      if (!playerId) {
        return;
      }

      const spell = spellBook.find((entry) => entry.id === spellId);

      if (!spell) {
        return;
      }

      onInput(createMageDuelGestureInput(playerId, spell.id, spell.color, points));
    }
  };
}
