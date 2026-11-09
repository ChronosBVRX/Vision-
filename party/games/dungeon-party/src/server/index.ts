import { dungeonCopy, bossPhaseName } from "../text.js";
import { createBaseRoundState, roundPhaseDurations, transitionRoundState, type ScoreEntry, type ServerGame } from "@open-party-lab/game-core";
import { classActions, dungeonPartyManifest } from "../manifest.js";
import { campActions, cardCatalog, localizedRoom, roomPools, roomRules, routePools } from "../content.js";
import type { DungeonActionId, DungeonCard, DungeonEncounter, DungeonPartyControllerState, DungeonPartyInput, DungeonPartyPublicState, DungeonPartyState, DungeonCampResult } from "../protocol.js";

const maxHealth = 8;
const bossPhaseNames = ["Der Vertrag", "Das Drachenfeuer", "Die letzte Klausel"];
const bossPhaseCount = bossPhaseNames.length;
const bossPhaseAttemptLimit = 2;
const classNames: Record<string, string> = { warrior: "Krieger", mage: "Magier", rogue: "Schurke", cleric: "Kleriker", bard: "Barde", tinkerer: "Tüftler" };
function createEncounters(playerCount: number, language: "de" | "en", initialSeed: number): DungeonEncounter[] {
  let seed = initialSeed;
  const random = (length: number) => { seed = (Math.imul(seed, 1_664_525) + 1_013_904_223) >>> 0; return (seed >>> 8) % length; };
  const shuffle = <T,>(values: T[]): T[] => {
    const result = [...values];
    for (let index = result.length - 1; index > 0; index--) { const next = random(index + 1); [result[index], result[next]] = [result[next]!, result[index]!]; }
    return result;
  };
  const scale = Math.max(0, playerCount - 3) * 5;
  const early = shuffle(roomPools.early);
  const middle = shuffle(roomPools.middle);
  const late = shuffle(roomPools.late);
  const rules = shuffle(roomRules(language));
  const room = (template: (typeof early)[number], index: number) => ({ ...localizedRoom(template, language, scale), rule: rules[index % rules.length] });
  const branch = (id: string): DungeonEncounter => ({ id, name: language === "en" ? "Fork in the dungeon" : "Weggabelung im Dungeon", flavor: "", kind: "event", difficulty: 0, health: 0, maxHealth: 0, cleared: false });
  return [room(early[0]!, 0), room(early[1]!, 1), branch("first-route"), room(middle[0]!, 2), branch("second-route"), room(middle[1]!, 3), room(late[0]!, 4), {
    id: "boss", artKey: "boss", name: language === "en" ? "The dragon with the small print" : "Der Drache mit dem Kleingedruckten",
    flavor: language === "en" ? "The final boss would like to discuss the terms." : "Der Endgegner möchte kurz die Bedingungen besprechen.", kind: "boss", difficulty: 24 + scale, health: 3, maxHealth: 3, cleared: false
  }];
}

function routeOptions(state: DungeonPartyState, encounterIndex: number): NonNullable<DungeonPartyState["routeOptions"]> {
  const scale = Math.max(0, state.heroes.length - 3) * 5;
  const pool = [...routePools[encounterIndex === 2 ? 0 : 1]!];
  let seed = state.seed;
  for (let index = pool.length - 1; index > 0; index--) {
    seed = (Math.imul(seed, 1_664_525) + 1_013_904_223) >>> 0;
    const selected = (seed >>> 8) % (index + 1);
    [pool[index], pool[selected]] = [pool[selected]!, pool[index]!];
  }
  return pool.slice(0, 2).map((room) => {
    const localized = localizedRoom(room, state.language, scale);
    return { ...localized, artKey: room.artKey, description: localized.flavor, cleared: false as const };
  });
}

function pickClass(selectedId: string | null | undefined): string {
  return selectedId && classNames[selectedId] ? selectedId : "warrior";
}

function startPlanning(state: DungeonPartyState, now: number, encounterIndex: number, routeResolved = false): DungeonPartyState {
  if ([3, 7].includes(encounterIndex) && !state.completedCamps.includes(encounterIndex)) {
    return { ...state, stage: "camp", encounterIndex, campChoicesByPlayer: {}, campResults: [], continueByPlayerId: {}, completedCamps: [...state.completedCamps, encounterIndex], lastResolution: undefined, lastRolls: undefined, message: state.language === "en" ? "Prepare at the campfire." : "Bereitet euch am Lagerfeuer vor.", updatedAt: now };
  }
  if (!routeResolved && [2, 4].includes(encounterIndex)) {
    return {
      ...state, stage: "voting", encounterIndex, actionsByPlayer: {}, routeVotesByPlayer: {},
      responsesByPlayerId: {}, continueByPlayerId: {}, routeOptions: routeOptions(state, encounterIndex),
      lastRolls: undefined, lastResolution: undefined, updatedAt: now,
      message: dungeonCopy(state.language, "Wählt gemeinsam den nächsten Weg.", "Choose the next route together.")
    };
  }
  const moralePenalty = Math.max(0, 3 - state.partyMorale) * 2;
  const playerScale = Math.max(0, state.heroes.length - 3) * 7;
  const bossPhaseIndex = Math.min(state.bossPhaseIndex, bossPhaseCount - 1);
  const encounters = state.encounters.map((encounter, index) => index === encounterIndex
    ? {
      ...encounter, cleared: false,
      difficulty: encounter.id === "boss" ? 18 + bossPhaseIndex * 3 + playerScale + moralePenalty : encounter.maxHealth + moralePenalty,
      health: encounter.id === "boss" ? bossPhaseCount - bossPhaseIndex : encounter.maxHealth,
      maxHealth: encounter.id === "boss" ? bossPhaseCount : encounter.maxHealth,
      phaseIndex: encounter.id === "boss" ? bossPhaseIndex + 1 : undefined,
      phaseName: encounter.id === "boss" ? bossPhaseName(state.language, bossPhaseIndex) : undefined,
      phaseCount: encounter.id === "boss" ? bossPhaseCount : undefined,
      attempt: encounter.id === "boss" ? state.bossPhaseAttempts + 1 : undefined,
      attemptLimit: encounter.id === "boss" ? bossPhaseAttemptLimit : undefined,
      partyPower: undefined, resolution: undefined
    }
    : encounter);
  return { ...state, stage: "planning", encounterIndex, encounters, actionsByPlayer: {}, responsesByPlayerId: {}, routeVotesByPlayer: {}, continueByPlayerId: {}, routeOptions: undefined, lastRolls: undefined, lastResolution: undefined, message: state.language === "en" ? "Choose your actions." : "Wählt eure Aktionen.", updatedAt: now };
}

function resolveRouteVote(state: DungeonPartyState, now: number): DungeonPartyState {
  const options = state.routeOptions ?? [];
  if (options.length === 0) return state;
  const counts = options.map((option) => ({ option, votes: Object.values(state.routeVotesByPlayer).filter((routeId) => routeId === option.id).length }));
  const selected = [...counts].sort((a, b) => b.votes - a.votes || a.option.difficulty - b.option.difficulty || a.option.id.localeCompare(b.option.id))[0]!.option;
  const routeHistory = [...state.routeHistory, { encounterIndex: state.encounterIndex, routeId: selected.id, name: selected.name }];
  const encounters = state.encounters.map((encounter, index) => index === state.encounterIndex
    ? { ...selected, flavor: selected.description }
    : encounter);
  const next = startPlanning({ ...state, encounters, routeHistory, message: dungeonCopy(state.language, `Der Weg ist gewählt: ${selected.name}.`, `Route chosen: ${selected.name}.`) }, now, state.encounterIndex, true);
  return { ...next, message: dungeonCopy(state.language, `Der Weg ist gewählt: ${selected.name}.${counts[0]!.votes === counts[1]!.votes ? " Gleichstand: Die leichtere Route gewinnt." : ""}`, `Route chosen: ${selected.name}.${counts[0]!.votes === counts[1]!.votes ? " Tie: the easier route wins." : ""}`) };
}

function openResponseWindow(state: DungeonPartyState, now: number): DungeonPartyState {
  const responsesByPlayerId = Object.fromEntries(state.heroes
    .filter((hero) => !hasPlayableResponse(state, hero.playerId))
    .map((hero) => [hero.playerId, {}]));
  return {
    ...state, stage: "response", responsesByPlayerId, updatedAt: now,
    message: dungeonCopy(state.language, "Grundaktionen aufgedeckt. Reaktionskarten können gespielt werden.", "Actions revealed. You may now play response cards.")
  };
}

function hasPlayableResponse(state: DungeonPartyState, playerId: string): boolean {
  const otherHeroExists = state.heroes.some((hero) => hero.playerId !== playerId);
  return (state.handsByPlayerId[playerId] ?? []).some((card) => {
    if (card.kind !== "effect") return false;
    if (["intrigue", "false_bill", "jam"].includes(card.effect ?? "")) return otherHeroExists;
    if (card.effect === "ward") return state.heroes.length > 0;
    return true;
  });
}

function grantLootCard(hero: DungeonPartyState["heroes"][number], hands: Record<string, DungeonCard[]>, seed: number, encounterIndex: number, drawIndex: number, language: "de" | "en"): DungeonCard {
  const catalog = cardCatalog(language);
  const cardTemplate = catalog[((seed >>> 8) + encounterIndex + drawIndex) % catalog.length]!;
  const card = { ...cardTemplate, id: `${cardTemplate.id}-${hero.playerId}-${encounterIndex}-${seed}-${drawIndex}` };
  if (card.kind === "equipment") {
    if (!hero.items.some((item) => item.id === card.id)) hero.items.push({ id: card.id, name: card.name, description: card.description, attack: card.attack });
  } else {
    hands[hero.playerId] = [...(hands[hero.playerId] ?? []), card];
  }
  return card;
}

function playerPower(heroClass: string, action: DungeonActionId, roll: number, itemPower: number): number {
  if (action === "mage_burst") return 9 + roll + itemPower;
  if (action === "rogue_lift") return 2 + itemPower;
  if (action === "cleric_heal") return 2 + (heroClass === "cleric" ? 2 : 0) + Math.floor(roll / 4);
  if (action === "bard_inspire") return 2 + roll + itemPower + 1;
  if (action === "tinkerer_improvise") return 2 + roll + itemPower + 2 + (roll >= 4 ? 3 : 0);
  if (action === "aid") return 2 + (heroClass === "cleric" ? 2 : 0) + Math.floor(roll / 4);
  if (action === "loot") return 1 + (heroClass === "rogue" ? 1 : 0) + itemPower;
  return 2 + roll + itemPower + ({ warrior: 2, mage: 3, rogue: 1, cleric: 1, bard: 1, tinkerer: 2 }[heroClass] ?? 0);
}

function grantBossReward(hero: DungeonPartyState["heroes"][number], hands: Record<string, DungeonCard[]>, seed: number, encounterIndex: number, phaseIndex: number, language: "de" | "en"): DungeonCard {
  const effectCards = cardCatalog(language).filter((card) => card.kind === "effect");
  const template = effectCards[((seed >>> 8) + encounterIndex + phaseIndex) % effectCards.length]!;
  const card = { ...template, id: `${template.id}-${hero.playerId}-${encounterIndex}-${phaseIndex}-${seed}` };
  hands[hero.playerId] = [...(hands[hero.playerId] ?? []), card];
  return card;
}

function resolveCamp(state: DungeonPartyState, now: number): DungeonPartyState {
  const heroes = state.heroes.map((hero) => ({ ...hero, items: [...hero.items] }));
  const handsByPlayerId = Object.fromEntries(Object.entries(state.handsByPlayerId).map(([id, hand]) => [id, [...hand]]));
  let preparedPower = state.preparedPower;
  const campResults: DungeonCampResult[] = heroes.map((hero, index) => {
    const action = state.campChoicesByPlayer[hero.playerId]!;
    const before = hero.health;
    const cost = campActions[action].cost;
    hero.gold -= cost;
    const cards: DungeonCard[] = [];
    if (action === "rest") hero.health = Math.min(maxHealth, hero.health + 3);
    if (action === "scout") preparedPower += 2;
    if (action === "upgrade") hero.items.push({ id: `camp-upgrade-${state.encounterIndex}-${hero.playerId}`, name: state.language === "en" ? "Camp upgrade" : "Lager-Aufrüstung", description: "+1", attack: 1 });
    if (action === "stock") {
      for (let draw = 0; draw < 2; draw++) cards.push(grantBossReward(hero, handsByPlayerId, state.seed + index * 7919, state.encounterIndex, draw, state.language));
    }
    return { playerId: hero.playerId, playerName: hero.name, action, healthDelta: hero.health - before, goldDelta: -cost, powerDelta: action === "upgrade" ? 1 : action === "scout" ? 2 : 0, cards };
  });
  return { ...state, stage: "camp_reveal", heroes, handsByPlayerId, preparedPower, campResults, continueByPlayerId: {}, message: state.language === "en" ? "Everyone has prepared. Continue when ready." : "Alle sind vorbereitet. Weiter, wenn ihr bereit seid.", updatedAt: now };
}

function resolveEncounter(state: DungeonPartyState, now: number): DungeonPartyState {
  let seed = (state.seed >>> 0) || 17;
  const rollD6 = () => { seed = (Math.imul(seed, 1_664_525) + 1_013_904_223) >>> 0; return 1 + ((seed >>> 16) % 6); };
  const beforeByPlayer = new Map(state.heroes.map((hero) => [hero.playerId, { health: hero.health, fame: hero.fame, gold: hero.gold }]));
  const heroes: DungeonPartyState["heroes"] = state.heroes.map((hero) => ({ ...hero, items: [...hero.items], lastAction: state.actionsByPlayer[hero.playerId]?.action, lastFameDelta: 0, lastOutcome: undefined }));
  const handsByPlayerId = Object.fromEntries(Object.entries(state.handsByPlayerId).map(([playerId, hand]) => [playerId, [...hand]]));
  const current = state.encounters[state.encounterIndex];
  if (!current) return state;
  let partyPower = state.preparedPower;
  let difficultyModifier = 0;
  const rolls: NonNullable<DungeonPartyState["lastRolls"]> = [];
  const playedCards: Array<{ hero: DungeonPartyState["heroes"][number]; card: DungeonCard; targetPlayerId?: string }> = [];
  const gainedCards: Array<{ hero: DungeonPartyState["heroes"][number]; card: DungeonCard; source: "loot" | "boss" | "room" }> = [];
  const protectedPlayerIds = new Set<string>();
  const sabotagePlayers: Array<{ hero: DungeonPartyState["heroes"][number]; card: DungeonCard }> = [];

  for (const hero of heroes) {
    const choice = state.actionsByPlayer[hero.playerId] ?? { action: "fight" as const };
    const roll = rollD6();
    const ruleBonus = current.rule?.id === "frontline" && ["fight", "warrior_guard"].includes(choice.action) ? 2
      : current.rule?.id === "echo" && ["aid", "cleric_heal"].includes(choice.action) ? 3
        : current.rule?.id === "arcane" && !["fight", "aid", "loot"].includes(choice.action) ? 2 : 0;
    const contribution = playerPower(hero.classId, choice.action, roll, hero.items.reduce((sum, item) => sum + item.attack, 0)) + ruleBonus + (choice.action === "bard_inspire" ? 2 : 0);
    partyPower += contribution;
    rolls.push({ playerId: hero.playerId, roll, contribution });
    const response = state.responsesByPlayerId[hero.playerId];
    const hand = handsByPlayerId[hero.playerId] ?? [];
    const cardIndex = response?.cardId ? hand.findIndex((card) => card.id === response.cardId) : -1;
    const card = cardIndex >= 0 ? hand[cardIndex] : undefined;
    if (card) {
      handsByPlayerId[hero.playerId] = hand.filter((_entry, index) => index !== cardIndex);
      playedCards.push({ hero, card, targetPlayerId: response?.targetPlayerId });
    }
    if (choice.action === "loot") hero.gold += 2 + (hero.classId === "rogue" ? 1 : 0);
    if (choice.action === "fight" || choice.action === "aid") {
      hero.fame += 1;
      hero.lastFameDelta = (hero.lastFameDelta ?? 0) + 1;
    }
    if (hero.classId === "bard" && choice.action === "aid") {
      hero.fame += 1;
      hero.lastFameDelta = (hero.lastFameDelta ?? 0) + 1;
    }
    if (choice.action === "warrior_guard") protectedPlayerIds.add(hero.playerId);
    if (choice.action === "bard_inspire") {
      hero.lastOutcome = dungeonCopy(state.language, "Dein Kampflied gibt der Gruppe +2 Kampfkraft.", "Your battle song gives the party +2 combat power.");
    }
  }

  for (const { hero, card, targetPlayerId } of playedCards) {
    const target = heroes.find((candidate) => candidate.playerId === targetPlayerId);
    switch (card.effect) {
      case "intrigue":
        if (target && target.playerId !== hero.playerId) {
          const stolenFame = Math.min(1, Math.max(0, target.fame));
          target.fame -= stolenFame;
          target.lastFameDelta = (target.lastFameDelta ?? 0) - stolenFame;
          hero.fame += stolenFame;
          hero.lastFameDelta = (hero.lastFameDelta ?? 0) + stolenFame;
          hero.lastOutcome = dungeonCopy(state.language, `Intrige: ${target.name} verliert ${stolenFame} Ruhm; du erhältst ${stolenFame} Ruhm.`, `Intrigue: ${target.name} loses ${stolenFame} fame; you gain ${stolenFame} fame.`);
          partyPower -= 2;
          sabotagePlayers.push({ hero, card });
        }
        break;
      case "false_bill":
        if (target && target.playerId !== hero.playerId) {
          const stolen = Math.min(2, target.gold);
          target.gold -= stolen;
          hero.gold += stolen;
          hero.lastOutcome = dungeonCopy(state.language, `Falsche Rechnung: ${target.name} zahlt ${stolen} Gold.`, `False invoice: ${target.name} pays ${stolen} gold.`);
        }
        break;
      case "rally":
        partyPower += 4;
        hero.fame += 1;
        hero.lastFameDelta = (hero.lastFameDelta ?? 0) + 1;
        hero.lastOutcome = dungeonCopy(state.language, "Dein Heldengesang bringt +4 Gruppenstärke.", "Your heroic song adds +4 party power.");
        break;
      case "ward":
        if (target) {
          protectedPlayerIds.add(target.playerId);
          hero.lastOutcome = dungeonCopy(state.language, `${target.name} ist durch deinen Mantel geschützt.`, `${target.name} is protected by your cloak.`);
        }
        break;
      case "jam":
        if (target && target.playerId !== hero.playerId) {
          const targetRoll = rolls.find((entry) => entry.playerId === target.playerId);
          if (targetRoll) {
            const reduction = Math.min(3, targetRoll.contribution);
            targetRoll.contribution -= reduction;
            partyPower -= reduction;
          }
          hero.lastOutcome = dungeonCopy(state.language, `${target.name}s Beitrag wird durch eine lose Diele geschwächt.`, `${target.name} has their contribution weakened by a loose floorboard.`);
          sabotagePlayers.push({ hero, card });
        }
        break;
      case "reinforce":
        difficultyModifier += 5;
        hero.lastOutcome = dungeonCopy(state.language, "Du hast dem Gegner Verstärkung verschafft.", "You have sent reinforcements to the enemy.");
        sabotagePlayers.push({ hero, card });
        break;
      case "heal":
        if (target) target.health = Math.min(maxHealth, target.health + 2);
        break;
      case "weaken": difficultyModifier -= 4; break;
      case "barrier":
        for (const protectedHero of heroes) protectedPlayerIds.add(protectedHero.playerId);
        break;
      case "fortune": {
        const targetRoll = rolls.find((entry) => entry.playerId === targetPlayerId);
        if (target && targetRoll && targetRoll.roll <= 3) {
          targetRoll.originalRoll ??= targetRoll.roll;
          const newRoll = Math.max(targetRoll.roll, rollD6());
          const action = state.actionsByPlayer[target.playerId]!.action;
          const items = target.items.reduce((sum, item) => sum + item.attack, 0);
          const difference = playerPower(target.classId, action, newRoll, items) - playerPower(target.classId, action, targetRoll.roll, items);
          targetRoll.roll = newRoll;
          targetRoll.contribution += difference;
          partyPower += difference;
        }
        break;
      }
    }
  }

  for (const hero of heroes) {
    const action = state.actionsByPlayer[hero.playerId]?.action;
    if (hero.classId === "mage" && (action === "fight" || action === "mage_burst") && rolls.find((roll) => roll.playerId === hero.playerId)?.roll === 1) {
      const damage = action === "mage_burst" ? 2 : 1;
      hero.health = Math.max(0, hero.health - damage);
      hero.lastOutcome = dungeonCopy(state.language, `Der Zauber-Rückschlag kostet dich ${damage} Leben.`, `Spell backlash costs you ${damage} health.`);
    }
  }
  for (const healer of heroes.filter((hero) => hero.classId === "cleric" && ["aid", "cleric_heal"].includes(state.actionsByPlayer[hero.playerId]?.action ?? ""))) {
    const hurt = heroes.filter((hero) => hero.health < maxHealth).sort((a, b) => a.health - b.health)[0];
    if (hurt) hurt.health = Math.min(maxHealth, hurt.health + (state.actionsByPlayer[healer.playerId]?.action === "cleric_heal" ? 2 : 1));
  }
  const targetDifficulty = Math.max(1, current.difficulty + difficultyModifier);
  const success = partyPower >= targetDifficulty;
  const isBoss = current.id === "boss";
  const bossPhaseIndex = state.bossPhaseIndex;
  const nextBossPhaseIndex = isBoss && success ? bossPhaseIndex + 1 : bossPhaseIndex;
  const nextBossPhaseAttempts = isBoss ? (success ? 0 : state.bossPhaseAttempts + 1) : state.bossPhaseAttempts;
  const bossPhaseComplete = isBoss && success && nextBossPhaseIndex >= bossPhaseCount;
  const bossRunFailed = isBoss && !success && nextBossPhaseAttempts >= bossPhaseAttemptLimit;
  const campaignEnded = bossPhaseComplete || bossRunFailed;
  const nextEncounters = state.encounters.map((encounter, index) => index === state.encounterIndex
    ? {
      ...encounter, cleared: isBoss ? bossPhaseComplete : success,
      health: isBoss ? (success ? Math.max(0, bossPhaseCount - nextBossPhaseIndex) : encounter.health) : Math.max(0, encounter.maxHealth - partyPower),
      phaseIndex: isBoss ? bossPhaseIndex + 1 : undefined,
      phaseName: isBoss ? bossPhaseName(state.language, bossPhaseIndex) : undefined,
      phaseCount: isBoss ? bossPhaseCount : undefined,
      attempt: isBoss ? state.bossPhaseAttempts + 1 : undefined,
      attemptLimit: isBoss ? bossPhaseAttemptLimit : undefined,
      difficulty: targetDifficulty, partyPower,
      resolution: isBoss
        ? (success ? dungeonCopy(state.language, `${bossPhaseName(state.language, bossPhaseIndex)} geschafft!`, `${bossPhaseName(state.language, bossPhaseIndex)} completed!`) : bossRunFailed ? dungeonCopy(state.language, "Der Drache hält die Gruppe auf.", "The dragon stops the party.") : dungeonCopy(state.language, "Die Phase beginnt von vorn.", "The phase starts again."))
        : success ? dungeonCopy(state.language, "Geschafft!", "Success!") : dungeonCopy(state.language, "Der Dungeon merkt sich das.", "The dungeon will remember this.")
    }
    : encounter);

  for (const hero of heroes) {
    const action = state.actionsByPlayer[hero.playerId]?.action;
    if (success && action === "rogue_lift") {
      const target = [...heroes].filter((entry) => entry.playerId !== hero.playerId && entry.gold > 0).sort((a, b) => b.gold - a.gold)[0];
      if (target) {
        const stolen = Math.min(3, target.gold);
        target.gold -= stolen;
        hero.gold += stolen;
        hero.fame += 1;
        hero.lastOutcome = dungeonCopy(state.language, `${target.name} verliert ${stolen} Gold durch dein Taschenspiel.`, `${target.name} loses ${stolen} gold to your sleight of hand.`);
      } else hero.lastOutcome = dungeonCopy(state.language, "Niemand hatte Gold, das du stehlen konntest.", "Nobody had any gold for you to steal.");
    }
    if (success && action === "loot") {
      const firstCard = grantLootCard(hero, handsByPlayerId, seed + heroes.indexOf(hero) * 7919, state.encounterIndex, 0, state.language);
      gainedCards.push({ hero, card: firstCard, source: "loot" });
      hero.fame += 2;
      hero.lastFameDelta = (hero.lastFameDelta ?? 0) + 2;
      const extraCard = current.id === "cursed-armory" ? grantLootCard(hero, handsByPlayerId, seed + heroes.indexOf(hero) * 7919, state.encounterIndex, 1, state.language) : undefined;
      if (extraCard) gainedCards.push({ hero, card: extraCard, source: "loot" });
      const rewards = [firstCard, extraCard].filter((card): card is DungeonCard => Boolean(card)).map((card) => card.kind === "equipment" ? dungeonCopy(state.language, `Ausrüstung: ${card.name}.`, `Equipment: ${card.name}.`) : dungeonCopy(state.language, `Karte gezogen: ${card.name}.`, `Card drawn: ${card.name}.`)).join(" ");
      hero.lastOutcome = `${hero.lastOutcome ? `${hero.lastOutcome} ` : ""}${rewards}`;
    }
    if (!success) {
      const baseDamage = (action === "loot" || action === "rogue_lift" ? 2 : 1) + (current.rule?.id === "danger" ? 1 : 0);
      const damage = Math.max(0, baseDamage - (protectedPlayerIds.has(hero.playerId) ? 1 : 0));
      hero.health = Math.max(0, hero.health - damage);
      hero.lastOutcome = `${hero.lastOutcome ? `${hero.lastOutcome} ` : ""}${damage < baseDamage ? dungeonCopy(state.language, "Dein Schutz fängt 1 Schaden ab. ", "Your protection absorbs 1 damage. ") : ""}${dungeonCopy(state.language, `Dungeon-Schaden: ${damage}.`, `Dungeon damage: ${damage}.`)}`;
    }
    if (playedCards.some((entry) => entry.targetPlayerId === hero.playerId)) hero.lastOutcome = `${hero.lastOutcome ? `${hero.lastOutcome} ` : ""}${dungeonCopy(state.language, `Du warst Ziel einer Aktionskarte.`, `You were targeted by an action card.`)}`;
  }

  if (success && current.rule?.id === "riches") {
    for (const hero of heroes) if (state.actionsByPlayer[hero.playerId]?.action === "loot") hero.gold += 2;
  }
  if (success && current.rule?.id === "danger") for (const hero of heroes) hero.fame += 1;

  // Successful rooms replenish low hands; looting still grants its extra card/equipment.
  if (success && !isBoss) {
    for (const [index, hero] of heroes.entries()) {
      if ((handsByPlayerId[hero.playerId]?.length ?? 0) < 3) {
        gainedCards.push({ hero, card: grantBossReward(hero, handsByPlayerId, seed + index * 7919, state.encounterIndex, 0, state.language), source: "room" });
      }
    }
  }

  if (isBoss && success) {
    for (const hero of heroes) {
      const card = grantBossReward(hero, handsByPlayerId, seed + heroes.indexOf(hero) * 7919, state.encounterIndex, bossPhaseIndex, state.language);
      gainedCards.push({ hero, card, source: "boss" });
      hero.lastOutcome = `${hero.lastOutcome ? `${hero.lastOutcome} ` : ""}${dungeonCopy(state.language, `Drachenbeute: ${card.name} kommt auf deine Hand.`, `Dragon loot: ${card.name} goes into your hand.`)}`;
    }
  }

  for (const { hero, card } of sabotagePlayers) {
    const fameDelta = success ? (card.sabotageReward ?? 0) : -(card.sabotagePenalty ?? 2);
    hero.fame += fameDelta;
    hero.lastFameDelta = (hero.lastFameDelta ?? 0) + fameDelta;
    const result = success ? dungeonCopy(state.language, `Die Sabotage gelingt: ${fameDelta > 0 ? `+${fameDelta}` : "kein"} Ruhm.`, `Sabotage succeeds: ${fameDelta > 0 ? `+${fameDelta}` : "no"} fame.`) : dungeonCopy(state.language, `Die Gruppe scheitert: ${fameDelta} Ruhm für deine Sabotage.`, `The party fails: ${fameDelta} fame for your sabotage.`);
    hero.lastOutcome = `${hero.lastOutcome ? `${hero.lastOutcome} ` : ""}${result}`;
  }

  if (success && current.id === "merchant") {
    for (const hero of heroes) { hero.gold += 1; hero.lastOutcome = `${hero.lastOutcome ? `${hero.lastOutcome} ` : ""}${dungeonCopy(state.language, `Händlerbonus: +1 Gold.`, `Merchant bonus: +1 gold.`)}`; }
  } else if (success && current.id === "crypt") {
    const topPower = Math.max(...rolls.map((roll) => roll.contribution));
    for (const hero of heroes.filter((hero) => rolls.some((roll) => roll.playerId === hero.playerId && roll.contribution === topPower))) {
      hero.fame += 2; hero.lastFameDelta = (hero.lastFameDelta ?? 0) + 2; hero.lastOutcome = `${hero.lastOutcome ? `${hero.lastOutcome} ` : ""}${dungeonCopy(state.language, `Gruftbonus: +2 Ruhm für den stärksten Beitrag.`, `Crypt bonus: +2 fame for the strongest contribution.`)}`;
    }
  } else if (success && ["shrine", "well"].includes(current.id)) {
    for (const hero of heroes) {
      if (hero.health < maxHealth) { hero.health += 1; hero.lastOutcome = `${hero.lastOutcome ? `${hero.lastOutcome} ` : ""}${dungeonCopy(state.language, `Der Schrein heilt 1 Leben.`, `The shrine restores 1 health.`)}`; }
    }
  } else if (success && current.id === "library") {
    for (const hero of heroes) if (["aid", "cleric_heal"].includes(state.actionsByPlayer[hero.playerId]?.action ?? "")) hero.fame += 2;
  } else if (success && current.id === "forge") {
    for (const hero of heroes) hero.items.push({ id: `forge-${hero.playerId}`, name: state.language === "en" ? "Forge upgrade" : "Schmiede-Aufrüstung", description: "+1", attack: 1 });
  } else if (success && current.id === "arena") {
    for (const hero of heroes) hero.fame += 2;
  }

  if (success) {
    const topPower = Math.max(...rolls.map((roll) => roll.contribution));
    for (const top of heroes.filter((hero) => rolls.some((roll) => roll.playerId === hero.playerId && roll.contribution === topPower))) {
      top.fame += 1; top.lastFameDelta = (top.lastFameDelta ?? 0) + 1; top.lastOutcome = `${top.lastOutcome ? `${top.lastOutcome} ` : ""}${dungeonCopy(state.language, `Stärkster Kraftbeitrag: +1 Ruhm.`, `Strongest power contribution: +1 fame.`)}`;
    }
  }
  const encounter = nextEncounters[state.encounterIndex]!;
  const partyMorale = Math.max(0, Math.min(5, state.partyMorale + (success ? 1 : -1)));
  const rankedHeroes = [...heroes].sort((a, b) => b.fame - a.fame || b.gold - a.gold || b.health - a.health);
  const winnerHero = rankedHeroes[0];
  const winnerHeroes = winnerHero
    ? rankedHeroes.filter((hero) => hero.fame === winnerHero.fame && hero.gold === winnerHero.gold && hero.health === winnerHero.health)
    : [];
  const winnerIds = winnerHeroes.map((hero) => hero.playerId);
  const finalHeroes = bossPhaseComplete
    ? heroes.map((hero) => ({ ...hero, fame: hero.fame + (winnerIds.includes(hero.playerId) ? 3 : 1) }))
    : bossRunFailed ? heroes.map((hero) => ({ ...hero, fame: hero.fame + 1 })) : heroes;
  const finalWinner = [...finalHeroes].sort((a, b) => b.fame - a.fame || b.gold - a.gold || b.health - a.health)[0];
  const resolution = {
    id: `${state.encounterIndex}-${state.bossPhaseIndex}-${state.bossPhaseAttempts}-${seed}`,
    encounterId: current.id, encounterName: current.name, success, partyPower, targetDifficulty,
    difficultyModifier, sharedPower: partyPower - rolls.reduce((sum, roll) => sum + roll.contribution, 0),
    heroes: finalHeroes.map((hero) => {
      const before = beforeByPlayer.get(hero.playerId)!;
      const roll = rolls.find((entry) => entry.playerId === hero.playerId)!;
      return {
        playerId: hero.playerId, name: hero.name, classId: hero.classId,
        action: state.actionsByPlayer[hero.playerId]?.action ?? "fight",
        roll: roll.roll, originalRoll: roll.originalRoll, contribution: roll.contribution,
        healthDelta: hero.health - before.health, fameDelta: hero.fame - before.fame, goldDelta: hero.gold - before.gold,
        outcome: hero.lastOutcome
      };
    }),
    cards: playedCards.map(({ hero, card, targetPlayerId }) => ({
      playerId: hero.playerId, playerName: hero.name, cardId: card.id, name: card.name, description: card.description, effect: card.effect,
      ...(targetPlayerId ? { targetPlayerId, targetName: heroes.find((entry) => entry.playerId === targetPlayerId)?.name } : {})
    })),
    rewards: gainedCards.map(({ hero, card, source }) => ({
      playerId: hero.playerId, playerName: hero.name, cardId: card.id, name: card.name, description: card.description,
      kind: card.kind, source, effect: card.effect
    }))
  };
  const next: DungeonPartyState = {
    ...state, stage: campaignEnded ? "complete" : "reveal", heroes: finalHeroes, handsByPlayerId, encounters: nextEncounters,
    actionsByPlayer: {}, continueByPlayerId: {}, partyMorale, seed, preparedPower: 0,
    bossPhaseIndex: nextBossPhaseIndex, bossPhaseAttempts: nextBossPhaseAttempts,
    lastRolls: rolls, lastResolution: resolution, campaignWon: bossPhaseComplete,
    winnerPlayerId: campaignEnded ? finalWinner?.playerId : undefined,
    winnerPlayerIds: campaignEnded && finalWinner ? finalHeroes.filter((hero) => hero.fame === finalWinner.fame && hero.gold === finalWinner.gold && hero.health === finalWinner.health).map((hero) => hero.playerId) : undefined,
    updatedAt: now,
    message: bossPhaseComplete ? dungeonCopy(state.language, "Der Drache ist besiegt!", "The dragon is defeated!") : bossRunFailed ? dungeonCopy(state.language, "Der Drache hält stand. Die Gruppe muss sich zurückziehen.", "The dragon holds firm. The party must retreat.") : success ? dungeonCopy(state.language, `${encounter.name}: bestanden!`, `${encounter.name}: cleared!`) : dungeonCopy(state.language, `${encounter.name}: gescheitert.`, `${encounter.name}: failed.`)
  };
  return campaignEnded ? transitionRoundState(next, "locked", now, { durationMs: roundPhaseDurations.lockedMs, message: next.message }) : next;
}

export const serverGame: ServerGame<DungeonPartyState, DungeonPartyInput, DungeonPartyPublicState> = {
  manifest: dungeonPartyManifest,
  createInitialState(context) {
    const seed = (context.now + context.players.length * 997) >>> 0;
    const catalog = cardCatalog(context.language === "en" ? "en" : "de").filter((card) => card.kind === "effect");
    return {
      ...createBaseRoundState("round_intro", context.now, { durationMs: roundPhaseDurations.roundIntroMs, message: dungeonCopy(context.language === "en" ? "en" : "de", "Der Dungeon wartet.", "The dungeon awaits.") }),
      stage: "planning", language: context.language === "en" ? "en" : "de", encounterIndex: 0, encounters: createEncounters(context.players.length, context.language === "en" ? "en" : "de", seed),
      heroes: context.players.map((player) => ({ playerId: player.id, name: player.name, classId: pickClass(player.selectedCharacterId), health: maxHealth, fame: 0, gold: 2, items: [] })),
      handsByPlayerId: Object.fromEntries(context.players.map((player, index) => {
        const extras = catalog.filter((card) => !["intrigue", "rally"].includes(card.effect ?? ""));
        return [player.id, [catalog.find((card) => card.effect === "intrigue")!, catalog.find((card) => card.effect === "rally")!, extras[((seed >>> 8) + index * 3) % extras.length]!].map((card, draw) => ({ ...card, id: `${card.id}-${player.id}-start-${draw}` }))];
      })),
      actionsByPlayer: {}, responsesByPlayerId: {}, routeVotesByPlayer: {}, continueByPlayerId: {}, routeHistory: [], partyMorale: 3,
      bossPhaseIndex: 0, bossPhaseAttempts: 0,
      seed, campChoicesByPlayer: {}, campResults: [], completedCamps: [], preparedPower: 0
    };
  },
  startRound(state, context) {
    return transitionRoundState(startPlanning(state, context.now, 0), "playing", context.now, {
      startedAt: context.now,
      message: dungeonCopy(context.language === "en" ? "en" : "de", "Wählt eure Aktionen.", "Choose your actions.")
    });
  },
  handleInput(state, input, context) {
    if (state.phase !== "playing" || !context.players.some((player) => player.id === input.playerId) || !state.heroes.some((hero) => hero.playerId === input.playerId)) return state;
    if (input.type === "dungeon_camp_action") {
      if (state.stage !== "camp" || state.campChoicesByPlayer[input.playerId] || !Object.hasOwn(campActions, input.action)) return state;
      const hero = state.heroes.find((entry) => entry.playerId === input.playerId);
      if (!hero || hero.gold < campActions[input.action].cost) return state;
      return { ...state, campChoicesByPlayer: { ...state.campChoicesByPlayer, [input.playerId]: input.action }, updatedAt: context.now };
    }
    if (input.type === "dungeon_route_vote") {
      if (state.stage !== "voting" || state.routeVotesByPlayer[input.playerId] || !state.routeOptions?.some((option) => option.id === input.routeId)) return state;
      return { ...state, routeVotesByPlayer: { ...state.routeVotesByPlayer, [input.playerId]: input.routeId }, updatedAt: context.now };
    }
    if (input.type === "dungeon_card_response") {
      if (state.stage !== "response" || state.responsesByPlayerId[input.playerId]) return state;
      if (input.cardId) {
        const card = (state.handsByPlayerId[input.playerId] ?? []).find((entry) => entry.id === input.cardId);
        if (!card || card.kind !== "effect") return state;
        const needsTarget = ["intrigue", "false_bill", "ward", "jam", "heal", "fortune"].includes(card.effect ?? "");
        if (needsTarget && (!input.targetPlayerId || !state.heroes.some((hero) => hero.playerId === input.targetPlayerId))) return state;
        if (["intrigue", "false_bill", "jam"].includes(card.effect ?? "") && input.targetPlayerId === input.playerId) return state;
        return { ...state, responsesByPlayerId: { ...state.responsesByPlayerId, [input.playerId]: { cardId: card.id, targetPlayerId: input.targetPlayerId } }, updatedAt: context.now };
      }
      return { ...state, responsesByPlayerId: { ...state.responsesByPlayerId, [input.playerId]: {} }, updatedAt: context.now };
    }
    if (input.type === "dungeon_continue") {
      if (!["reveal", "camp_reveal"].includes(state.stage) || state.continueByPlayerId[input.playerId]) return state;
      return { ...state, continueByPlayerId: { ...state.continueByPlayerId, [input.playerId]: true }, updatedAt: context.now };
    }
    if (state.stage !== "planning" || input.type !== "dungeon_action" || state.actionsByPlayer[input.playerId]) return state;
    const hero = state.heroes.find((entry) => entry.playerId === input.playerId);
    const allowed = ["fight", "loot", "aid"].includes(input.action) || classActions[hero?.classId ?? ""]?.id === input.action;
    if (!allowed) return state;
    return { ...state, actionsByPlayer: { ...state.actionsByPlayer, [input.playerId]: { action: input.action } }, updatedAt: context.now };
  },
  tick(state, _deltaMs, context) {
    if (state.phase !== "playing") return state;
    if (state.stage === "camp" && Object.keys(state.campChoicesByPlayer).length >= state.heroes.length) return resolveCamp(state, context.now);
    if (state.stage === "camp_reveal" && Object.keys(state.continueByPlayerId).length >= state.heroes.length) return startPlanning(state, context.now, state.encounterIndex);
    if (state.stage === "voting" && Object.keys(state.routeVotesByPlayer).length >= state.heroes.length) {
      return resolveRouteVote(state, context.now);
    }
    if (state.stage === "planning" && state.phase === "playing" && Object.keys(state.actionsByPlayer).length >= state.heroes.length) {
      return openResponseWindow(state, context.now);
    }
    if (state.stage === "response" && Object.keys(state.responsesByPlayerId).length >= state.heroes.length) return resolveEncounter(state, context.now);
    if (state.stage === "reveal" && Object.keys(state.continueByPlayerId).length >= state.heroes.length) {
      const current = state.encounters[state.encounterIndex];
      const nextIndex = current?.id === "boss" && !current.cleared ? state.encounterIndex : state.encounterIndex + 1;
      return startPlanning(state, context.now, nextIndex);
    }
    return state;
  },
  isRoundFinished(state) { return state.stage === "complete"; },
  buildScore(state): ScoreEntry[] {
    if (state.stage !== "complete") return [];
    return state.heroes.map((hero) => ({ playerId: hero.playerId, delta: hero.fame, reason: dungeonCopy(state.language, "Dungeon-Ruhm", "Dungeon fame") }));
  },
  toPublicState(state) {
    const { actionsByPlayer: _actions, responsesByPlayerId: _responses, handsByPlayerId: _hands, routeVotesByPlayer: _votes, campChoicesByPlayer: _camp, seed: _seed, ...publicState } = state;
    const revealed = state.stage === "response"
      ? Object.fromEntries(Object.entries(state.actionsByPlayer).map(([playerId, action]) => [playerId, action.action]))
      : state.stage === "reveal" || state.stage === "complete"
        ? Object.fromEntries((state.lastResolution?.heroes ?? []).map((entry) => [entry.playerId, entry.action]))
      : {};
    return { ...publicState, heroes: state.heroes.map((hero) => ({ ...hero, handCount: (state.handsByPlayerId[hero.playerId] ?? []).length })), submittedCount: Object.keys(state.actionsByPlayer).length, responseCount: Object.keys(state.responsesByPlayerId).length, routeVoteCount: Object.keys(state.routeVotesByPlayer).length, campSubmittedCount: Object.keys(state.campChoicesByPlayer).length, revealedActionsByPlayer: revealed };
  },
  toControllerStateForPlayer(state, _context, playerId) {
    const publicState = serverGame.toPublicState!(state, _context);
    const revealed = state.stage === "response"
      ? Object.fromEntries(Object.entries(state.actionsByPlayer).map(([id, action]) => [id, action.action]))
      : state.stage === "reveal" || state.stage === "complete"
        ? Object.fromEntries((state.lastResolution?.heroes ?? []).map((entry) => [entry.playerId, entry.action]))
      : {};
    const ownResponse = state.responsesByPlayerId[playerId];
    const ownResponseCard = ownResponse?.cardId ? state.handsByPlayerId[playerId]?.find((card) => card.id === ownResponse.cardId) : undefined;
    const privateHand = state.handsByPlayerId[playerId] ?? [];
    return { ...publicState, ownCampSubmitted: Boolean(state.campChoicesByPlayer[playerId]), ownHand: ownResponseCard ? privateHand.filter((card) => card.id !== ownResponseCard.id) : privateHand, ownResponseCard, ownResponseTargetName: state.heroes.find((hero) => hero.playerId === ownResponse?.targetPlayerId)?.name, revealedActionsByPlayer: revealed, ownActionSubmitted: Boolean(state.actionsByPlayer[playerId]), ownResponseSubmitted: Boolean(state.responsesByPlayerId[playerId]), ownHasPlayableResponse: hasPlayableResponse(state, playerId), ownVoteSubmitted: Boolean(state.routeVotesByPlayer[playerId]), ownContinueSubmitted: Boolean(state.continueByPlayerId[playerId]), availableTargets: state.heroes.filter((hero) => hero.playerId !== playerId).map(({ playerId: id, name }) => ({ playerId: id, name })) } satisfies DungeonPartyControllerState;
  }
};
