import { classActions, dungeonPartyManifest } from "../manifest.js";
import type { DungeonPartyPublicState } from "../protocol.js";
import { classEmblem, effectSymbol, encounterIllustration, campIllustration } from "./art.js";
import { campActions } from "../content.js";
import { DungeonAudioRig } from "./audio.js";

interface HostState {
  game?: { phase?: string; state?: unknown } | null;
  room?: { language?: "de" | "en" } | null;
}
interface HostSource {
  getState(): HostState | null;
  subscribe(callback: (state: HostState) => void): () => void;
}

function esc(value: unknown): string {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]!);
}
function className(id: string, en: boolean): string {
  const names: Record<string, [string, string]> = { warrior: ["Krieger", "Warrior"], mage: ["Magier", "Mage"], rogue: ["Schurke", "Rogue"], cleric: ["Kleriker", "Cleric"], bard: ["Barde", "Bard"], tinkerer: ["manitas", "Tinkerer"] };
  return names[id]?.[en ? 1 : 0] ?? id;
}

function delta(value: number, icon: string): string {
  return value === 0 ? "" : '<span class="dp-delta ' + (value > 0 ? "up" : "down") + '">' + icon + " " + (value > 0 ? "+" : "") + value + "</span>";
}

type StoryBeat = { resolutionId: string; scene: "actions" | "cards" | "rolls" | "verdict" | "impact" | "settled"; index: number };

function cubeSvg(value: number, rolling = false, en = false): string {
  const pipGrid = [
    [], [[1, 1]], [[0, 0], [2, 2]], [[0, 0], [1, 1], [2, 2]],
    [[0, 0], [0, 2], [2, 0], [2, 2]], [[0, 0], [0, 2], [1, 1], [2, 0], [2, 2]],
    [[0, 0], [0, 1], [0, 2], [2, 0], [2, 1], [2, 2]]
  ][Math.max(1, Math.min(6, value))]!;
  const dots = pipGrid.map(([column, row]) => {
    const u = column / 2;
    const v = row / 2;
    const x = 26 + 24 * u;
    const y = 47 + 12.6 * u + 20 * v;
    return `<circle cx="${x}" cy="${y}" r="3.1"/>`;
  }).join("");
  return `<svg class="dp-cube ${rolling ? "rolling" : ""}" viewBox="0 0 100 100" role="img" aria-label="${en ? "D6 shows" : "espectáculos de dados"} ${value}"><title>d6: ${value}</title><g class="dp-cube-body"><path class="dp-cube-top" d="M19 36 57 16 82 29 44 50z"/><path class="dp-cube-side" d="M57 16 82 29 82 69 57 89z"/><path class="dp-cube-front" d="M19 36 57 56 57 89 19 69z"/><g class="dp-cube-pips">${dots}</g><path class="dp-cube-edge" d="m19 36 38 20 25-27M57 56v33M19 69l38 20 25-20"/></g></svg>`;
}

function render(root: HTMLElement, appState: HostState | null, beat?: StoryBeat, soundEnabled = true): void {
  const state = (appState?.game?.state ?? {}) as Partial<DungeonPartyPublicState>;
  const en = appState?.room?.language === "en";
  const encounter = state.encounters?.[state.encounterIndex ?? -1];
  const heroes = [...(state.heroes ?? [])].sort((a, b) => b.fame - a.fame);
  const final = state.stage === "complete";
  const done = state.phase === "finished" || state.phase === "scoreboard" || state.phase === "result" || state.phase === "locked";
  const result = state.lastResolution;
  const modifiers = result && (result.sharedPower !== 0 || result.difficultyModifier !== 0)
    ? '<p class="dp-party-modifiers">' + (en ? 'CARDS & SCOUTS: ' : "MAPAS Y EXPLORADORES: ") + (result.sharedPower > 0 ? '+' : '') + result.sharedPower + ' ⚔ · ' + (en ? 'TARGET MODIFIER: ' : "CAMBIO DE VALOR OBJETIVO: ") + (result.difficultyModifier > 0 ? '+' : '') + result.difficultyModifier + '</p>' : '';
  const camp = state.stage === "camp" || state.stage === "camp_reveal";
  const readyPhase = state.stage === "reveal" || state.stage === "camp_reveal";
  const actionLabel = (action?: string) => ({ fight: en ? "Fight" : "Kampf", aid: en ? "Help" : "Hilfe", loot: en ? "Loot" : "Beute", ...Object.fromEntries(Object.values(classActions).map((entry) => [entry.id, entry.name[en ? "en" : "de"]])) } as Record<string, string>)[action ?? "fight"];
  const stageLabel = camp ? (en ? "CAMPFIRE" : "LAGERFEUER") : state.stage === "voting" ? (en ? "ROUTE VOTE" : "WEGWAHL")
    : state.stage === "planning" ? (en ? "SECRET ACTIONS" : "GEHEIME AKTIONEN")
      : state.stage === "response" ? (en ? "PLAY A CARD" : "TARJETA DE JUEGO")
        : state.stage === "reveal" ? (en ? "ROOM RESULT" : "RAUMERGEBNIS")
          : final ? (en ? "CAMPAIGN END" : "KAMPAGNENENDE") : "DUNGEON";
  const readyCount = state.stage === "camp" ? state.campSubmittedCount ?? 0 : state.stage === "voting" ? state.routeVoteCount ?? 0
    : state.stage === "response" ? state.responseCount ?? 0
      : readyPhase ? Object.keys(state.continueByPlayerId ?? {}).length
        : state.submittedCount ?? 0;
  const nextPaths = (state.routeHistory ?? []).map((route) => '<span class="dp-path-step"><i></i><b>' + esc(route.name) + "</b></span>").join("");
  const routeOptions = (state.routeOptions ?? []).map((route, index) =>
    '<article class="dp-route-card" style="--route-i:' + index + '"><div class="dp-route-art">' + encounterIllustration(route.artKey) + '</div><div><span>' +
    esc(({ battle: en ? "BATTLE" : "KAMPF", challenge: en ? "CHALLENGE" : "HERAUSFORDERUNG", event: en ? "EVENT" : "EREIGNIS", boss: en ? "BOSS" : "ENDGEGNER" })[route.kind]) + " · " + (en ? "TARGET" : "ZIELWERT") + " " + route.difficulty + "</span><h3>" + esc(route.name) + "</h3><p>" + esc(route.description) + "</p></div></article>"
  ).join("");
  const campPanel = camp ? '<section class="dp-camp"><div class="dp-camp-landscape">' + campIllustration() + '<div class="dp-camp-title"><small>' + (en ? "A BREATH BEFORE THE NEXT ROOM" : "RESPIRA ANTES DE LA SIGUIENTE HABITACIÓN") + '</small><h2>' + (en ? "Around the campfire" : "Rund ums Lagerfeuer") + '</h2><p>' + (state.stage === "camp" ? (en ? "Heal, restock, upgrade or scout. Choose on your phone." : "Cura, compra cartas, mejora o explora. Marcaciones en el celular.") : (en ? "Your preparations are complete. Continue when everyone is ready." : "Los preparativos están completos. Continúe cuando todos estén listos.")) + '</p></div></div>' +
    (state.stage === "camp" ? '<div class="dp-camp-options">' + Object.values(campActions).map((action) => '<article><i>' + action.symbol + '</i><b>' + esc(action.name[en ? "en" : "de"]) + '</b><span>' + esc(action.description[en ? "en" : "de"]) + '</span></article>').join("") + '</div>' :
      '<div class="dp-camp-results">' + (state.campResults ?? []).map((entry) => '<article><i>' + campActions[entry.action].symbol + '</i><div><b>' + esc(entry.playerName) + '</b><span>' + esc(campActions[entry.action].name[en ? "en" : "de"]) + '</span><small>' + entry.cards.map((card) => esc(card.name)).join(" · ") + '</small></div><strong>' + delta(entry.healthDelta, "♥") + delta(entry.goldDelta, "¤") + delta(entry.powerDelta, "⚔") + '</strong></article>').join("") + '</div>') + '</section>' : "";
  const responseActions = state.stage === "response" ?
    '<section class="dp-action-strip"><div class="dp-section-label"><span>' + (en ? "SEALED ACTIONS REVEALED" : "AKTIONEN AUFGEDECKT") + '</span><b>' + (state.submittedCount ?? heroes.length) + "/" + heroes.length + '</b></div><div class="dp-action-chips">' +
    heroes.map((hero) => '<span class="dp-action-chip"><i class="action-' + (state.revealedActionsByPlayer?.[hero.playerId] ?? "fight") + '"></i><b>' + esc(hero.name) + "</b><em>" + actionLabel(state.revealedActionsByPlayer?.[hero.playerId]) + "</em></span>").join("") +
    "</div></section>" : "";
  const routeVoting = state.stage === "voting" ?
    '<section class="dp-route-vote"><div class="dp-section-label"><span>' + (en ? "CHOOSE THE BRANCH" : "ELIGE EL CAMINO") + '</span><b>' + readyCount + "/" + heroes.length + '</b></div><div class="dp-route-grid">' + routeOptions + "</div></section>" : "";
  const responseCards = result?.cards?.length ?
    '<section class="dp-card-reveal"><div class="dp-section-label"><span>' + (en ? "PLAYED CARDS" : "TARJETAS USADAS") + '</span></div><div class="dp-revealed-cards">' +
    result.cards.map((card) => '<article class="dp-card-reveal-item"><div class="dp-card-glyph">' + effectSymbol(card.effect) + '</div><div><small>' + esc(card.playerName) + (en ? " plays" : " spielt") + '</small><b>' + esc(card.name) + '</b><span>' +
      (card.targetName ? (en ? "Target: " : "Ziel: ") + esc(card.targetName) : (en ? "Group effect" : "Gruppeneffekt")) + "</span><em>" + esc(card.description) + "</em></div></article>").join("") +
    "</div></section>" : "";
  const rewardCards = result?.rewards?.length ?
    '<section class="dp-boss-rewards"><div class="dp-section-label"><span>' + (en ? "NEW LOOT & CARDS" : "NEUE BEUTE & HANDKARTEN") + '</span></div><div class="dp-reward-cards">' +
    result.rewards.map((card, index) => '<article class="dp-reward-card" style="--reward-i:' + index + '"><span class="dp-card-glyph">' + (card.kind === "equipment" ? "⚒" : effectSymbol(card.effect)) + '</span><span><small>' + esc(card.playerName) + " · " + (card.source === "boss" ? (en ? "BOSS REWARD" : "BOSSBELOHNUNG") : (card.kind === "equipment" ? (en ? "EQUIPPED" : "ANGELEGT") : (en ? "HAND CARD" : "HANDKARTE"))) + '</small><b>' + esc(card.name) + '</b><em>' + esc(card.description) + '</em></span></article>').join("") + "</div></section>" : "";
  const focusHero = result?.heroes[beat?.index ?? -1];
  const focusCard = result?.cards[beat?.index ?? -1];
  const storyScene = result && beat && beat.scene !== "settled" ? (() => {
    const steps = beat.scene === "actions" ? result.heroes.length : beat.scene === "cards" ? result.cards.length : beat.scene === "rolls" ? result.heroes.length : 1;
    const current = beat.scene === "cards" ? focusCard?.playerName : focusHero?.name;
    const stepLabel = (en ? "SCENE " : "SZENE ") + `${Math.min(beat.index + 1, Math.max(1, steps))} / ${Math.max(1, steps)}`;
    if (beat.scene === "actions" && focusHero) {
      const special = classActions[focusHero.classId]?.id === focusHero.action ? classActions[focusHero.classId] : undefined;
      return '<section class="dp-story-scene action-scene" data-scene="action"><div class="dp-story-eyebrow">' + stepLabel + ' · ' + (en ? "HERO ACTION" : "HELDENAKTION") + '</div><div class="dp-story-hero"><span class="dp-story-portrait">' + classEmblem(focusHero.classId) + '</span><div><small>' + esc(className(focusHero.classId, en).toLocaleUpperCase(en ? "en" : "de")) + '</small><h2>' + esc(focusHero.name) + '</h2><b>' + esc(actionLabel(focusHero.action)) + '</b><p>' + esc(special?.description[en ? "en" : "de"] ?? (focusHero.action === "fight" ? (en ? "Roll, add your class power and equipment." : "Tirada de dados más poder y equipo de clase.") : focusHero.action === "aid" ? (en ? "Add support to the party total." : "Aportas apoyo al ensayo del grupo.") : (en ? "Trade power for personal treasure." : "Cambias poder de combate por botín personal."))) + '</p></div></div><div class="dp-story-progress">' + result.heroes.map((_hero, index) => '<i class="' + (index <= beat.index ? "on" : "") + '"></i>').join("") + '</div></section>';
    }
    if (beat.scene === "cards" && focusCard) {
      return '<section class="dp-story-scene card-scene"><div class="dp-story-eyebrow">' + stepLabel + ' · ' + (en ? "CARD ON THE TABLE" : "TARJETA SOBRE LA MESA") + '</div><article class="dp-story-card"><span class="dp-story-card-art">' + effectSymbol(focusCard.effect) + '</span><div><small>' + esc(focusCard.playerName) + (en ? " plays" : " spielt") + '</small><h2>' + esc(focusCard.name) + '</h2><p>' + esc(focusCard.description) + '</p><p>' + (focusCard.targetName ? (en ? "Target: " : "Ziel: ") + esc(focusCard.targetName) : (en ? "Affects the whole party." : "Afecta a todo el grupo.")) + '</p></div></article><div class="dp-story-progress">' + result.cards.map((_card, index) => '<i class="' + (index <= beat.index ? "on" : "") + '"></i>').join("") + '</div></section>';
    }
    if (beat.scene === "rolls" && focusHero) {
      return '<section class="dp-story-scene roll-scene"><div class="dp-story-eyebrow">' + stepLabel + ' · ' + (en ? "D6 ROLL" : "W6-WURF") + '</div><div class="dp-story-roll"><div class="dp-big-cube-wrap">' + cubeSvg(focusHero.roll, true, en) + '</div><div class="dp-story-roll-copy"><small>' + esc(actionLabel(focusHero.action)) + '</small><h2>' + esc(focusHero.name) + '</h2>' + (focusHero.originalRoll ? '<p class="dp-luck-roll">⚄ ' + focusHero.originalRoll + ' → ' + focusHero.roll + ' · ' + (en ? "LUCK CARD" : "TARJETA DE LA SUERTE") + '</p>' : '') + '<b>' + (en ? "POWER ADDED" : "KRAFTBEITRAG") + ' <i>+' + focusHero.contribution + '</i></b></div></div><div class="dp-story-progress">' + result.heroes.map((_hero, index) => '<i class="' + (index <= beat.index ? "on" : "") + '"></i>').join("") + '</div></section>';
    }
    if (beat.scene === "verdict") {
      return '<section class="dp-story-scene verdict-scene ' + (result.success ? "success" : "failure") + '"><div class="dp-story-verdict-icon">' + (result.success ? "✦" : "⚡") + '</div><div class="dp-story-eyebrow">' + (en ? "THE DUNGEON ANSWERS" : "LA MAZMORRA RESPONDE") + '</div><h2>' + (result.success ? (en ? "THE PARTY BREAKS THROUGH" : "EL GRUPO SE ROMPE") : (en ? "THE DUNGEON STRIKES" : "LA MAZMORRA CONTRAATACA")) + '</h2><div class="dp-story-total"><span>' + result.partyPower + '</span><i>/</i><b>' + result.targetDifficulty + '</b></div><div class="dp-story-meter"><i style="width:' + Math.min(100, result.partyPower / Math.max(1, result.targetDifficulty) * 100) + '%"></i></div></section>';
    }
    return '<section class="dp-story-scene impact-scene"><div class="dp-story-eyebrow">' + (en ? "FALLOUT & FAME" : "FOLGEN & RUHM") + '</div><div class="dp-story-impact-grid">' + result.heroes.map((entry) => '<article class="dp-impact-card"><span>' + classEmblem(entry.classId) + '</span><div><b>' + esc(entry.name) + '</b><small>' + esc(actionLabel(entry.action)) + '</small></div><strong>' + delta(entry.healthDelta, "♥") + delta(entry.fameDelta, "✦") + delta(entry.goldDelta, "¤") + '</strong>' + (entry.outcome && !en ? '<p>' + esc(entry.outcome) + '</p>' : "") + '</article>').join("") + '</div>' + rewardCards + '</section>';
  })() : "";
  const resultPanel = result && (!beat || beat.scene === "settled") ?
    '<section class="dp-result ' + (result.success ? "success" : "failure") + '">' +
    '<div class="dp-result-heading"><div class="dp-verdict-mark">' + (result.success ? "✓" : "×") + '</div><div><small>' + (en ? "ROOM CHECK · " : "RAUMPROBE · ") + esc(result.encounterName) + '</small><h2>' +
    (result.success ? (en ? "The party prevails" : "el grupo lo hace") : (en ? "The dungeon strikes back" : "La mazmorra contraataca")) +
    '</h2></div><div class="dp-total"><span>' + (en ? "POWER / TARGET" : "KRAFT / ZIEL") + '</span><b>' + result.partyPower + '<i>/</i>' + result.targetDifficulty + "</b></div></div>" +
    '<div class="dp-dice-grid">' + result.heroes.map((entry, index) =>
      '<article class="dp-roll-card" style="--roll-i:' + index + '"><div class="dp-roll-person"><span class="dp-die">' + cubeSvg(entry.roll, false, en) + '</span><div><b>' + esc(entry.name) + '</b><small>' + actionLabel(entry.action) +
      '</small></div></div><strong class="dp-contribution">+' + entry.contribution + '</strong><div class="dp-roll-changes">' + delta(entry.healthDelta, "♥") + delta(entry.fameDelta, "✦") + delta(entry.goldDelta, "¤") + "</div>" +
      (entry.outcome && !en ? "<p>" + esc(entry.outcome) + "</p>" : "") + "</article>").join("") + "</div>" + responseCards + rewardCards + "</section>" : "";
  const pending = !result && state.stage !== "voting" && !final && !camp ?
    '<section class="dp-pending"><div class="dp-pending-icon">' + (state.stage === "response" ? "▱" : "⚄") + '</div><div><b>' +
    (state.stage === "response" ? (en ? "Cards in hand" : "Tarjetas en mano") : (en ? "Choose your move" : "Elige tu acción")) +
    '</b><span>' + (state.stage === "response" ? (en ? "The group reacts at its own pace." : "El grupo juega a las cartas sin presión de tiempo.") : (en ? "Choices stay open until everyone submits." : "La elección permanece abierta hasta que todos hayan decidido.")) +
    '</span></div><strong>' + readyCount + "/" + heroes.length + "</strong></section>" : "";
  const heroRows = heroes.map((hero, index) => {
    const rowResult = result?.heroes.find((entry) => entry.playerId === hero.playerId);
    const hp = Math.max(0, Math.min(100, hero.health / 8 * 100));
    const classLabel = en
      ? ({ warrior: "Warrior", mage: "Mage", rogue: "Rogue", cleric: "Cleric", bard: "Bard", tinkerer: "Tinkerer" } as Record<string, string>)[hero.classId]
      : ({ warrior: "Krieger", mage: "Magier", rogue: "Schurke", cleric: "Kleriker", bard: "Barde", tinkerer: "manitas" } as Record<string, string>)[hero.classId];
    return '<article class="dp-hero-row ' + (hero.playerId === state.winnerPlayerId ? "winner " : "") + (rowResult?.healthDelta && rowResult.healthDelta < 0 ? "hurt" : "") + '" style="--hero-i:' + index + '"><div class="dp-rank">' +
      String(index + 1).padStart(2, "0") + '</div><div class="dp-hero-emblem">' + classEmblem(hero.classId) + '</div><div class="dp-hero-main"><div class="dp-hero-name"><b>' + esc(hero.name) + '</b><span>' + esc(classLabel ?? hero.classId) +
      '</span></div><div class="dp-hp-line"><div class="dp-hp-bar"><i style="width:' + hp + '%"></i></div><span>♥ ' + hero.health + '/8</span>' +
      (rowResult?.healthDelta ? '<b class="dp-hp-delta ' + (rowResult.healthDelta < 0 ? "down" : "up") + '">' + (rowResult.healthDelta > 0 ? "+" : "") + rowResult.healthDelta + "</b>" : "") +
      '</div></div><div class="dp-score"><span>' + (en ? "FAME" : "RUHM") + '</span><b>' + hero.fame + '</b><small>¤ ' + hero.gold + " · ▱ " + (hero.handCount ?? 0) + "</small></div></article>";
  }).join("");
  const cinematic = Boolean(result && beat && beat.scene !== "settled");
  const finalPanel = (final || done) && !cinematic ?
    '<section class="dp-finale"><div class="dp-finale-mark">' + (state.campaignWon ? "♛" : "⌁") + '</div><div><small>' + (state.campaignWon ? (en ? "BOSS DEFEATED" : "DRACHE BESIEGT") : (en ? "CAMPAIGN FAILED" : "KAMPAGNE GESCHEITERT")) +
    '</small><h2>' + (state.campaignWon ? (en ? "The party escaped." : "El grupo escapó.") : (en ? "The dungeon held." : "La mazmorra ganó.")) +
    '</h2><p>' + (state.winnerPlayerIds ?? (state.winnerPlayerId ? [state.winnerPlayerId] : [])).map((id) => esc(heroes.find((hero) => hero.playerId === id)?.name)).join(en ? " & " : " y ") + (en ? " wins on fame." : " gana con gloria.") + "</p></div></section>" : "";
  const bossName = encounter?.id === "boss" ? (en ? ["THE CONTRACT", "DRAGONFIRE", "THE FINAL CLAUSE"] : ["EL CONTRATO", "EL FUEGO DEL DRAGÓN", "LA CLÁUSULA FINAL"])[(encounter.phaseIndex ?? 1) - 1] : undefined;
  const sceneText = state.stage === "planning" ? encounter?.flavor
    : state.stage === "response" ? (en ? "Actions are in. The party can still help, meddle, or protect." : "Las acciones están en marcha. Ahora el grupo puede ayudar, planear o proteger.")
      : encounter?.resolution ?? encounter?.flavor;
  const revealProgress = encounter?.id === "boss" ? ((state.bossPhaseIndex ?? 0) / 3 * 100)
    : result ? Math.min(100, result.partyPower / Math.max(1, result.targetDifficulty) * 100) : 0;
  const routeStatus = state.stage === "voting" ? (en ? "VOTE" : "ABSTIMMUNG")
    : state.stage === "response" ? (en ? "REACTIONS" : "REAKTIONEN")
      : readyPhase ? (en ? "CONTINUE WHEN READY" : "CONTINUAR SI ESTÁ LISTO")
        : camp ? (en ? "PREPARE ON YOUR PHONE" : "PREPÁRATE EN TU TELÉFONO MÓVIL")
        : (en ? "NO TURN TIMER" : "OHNE ZUG-ZEITLIMIT");

  root.innerHTML = '<main class="dp-shell ' + (camp ? 'dp-camp-shell ' : '') + (heroes.length > 4 ? 'dp-large-party' : '') + '">' +
    '<header class="dp-header"><div class="dp-brand"><span class="dp-brand-mark">⌘</span><div><small>' + (en ? "A COMPETITIVE CO-OP DUNGEON" : "UNA MAZMORRA COOPERATIVA COMPETITIVA") + '</small><h1>DUNGEON <i>PARTY</i></h1></div></div><button type="button" class="dp-sound-button" data-dp-sound aria-pressed="' + soundEnabled + '">' + (soundEnabled ? (en ? "♫ SFX ON" : "♫ Efectos activados") : (en ? "♫ SFX OFF" : "♫ Efectos desactivados")) + '</button><div class="dp-path"><span class="dp-path-label">' + (en ? "ROUTE" : "WEG") + '</span>' +
    (state.encounters ?? []).map((room, index) => '<i class="' + (index < (state.encounterIndex ?? 0) ? room.cleared ? "cleared" : "failed" : index === state.encounterIndex ? "current" : "") + '" title="' + esc(room.name) + '"></i>').join("") +
    "</div></header>" +
    (nextPaths ? '<nav class="dp-history"><b>' + (en ? "PATH TAKEN" : "CAMINO ELEGIDO") + '</b><div>' + nextPaths + "</div></nav>" : "") +
    finalPanel +
    ((!final && !done || cinematic) && state.stage !== "voting" && !camp ? '<section class="dp-scene ' + (beat && beat.scene !== "settled" ? "story-" + beat.scene : "") + '"><div class="dp-illustration">' + encounterIllustration(encounter?.artKey ?? encounter?.id, encounter?.phaseIndex) + '<span class="dp-scene-index">' + String((state.encounterIndex ?? 0) + 1).padStart(2, "0") +
      '</span></div><div class="dp-scene-info"><div class="dp-scene-kicker"><span>' + stageLabel + '</span><b>' + (encounter?.id === "boss" ? (en ? "PHASE " : "PHASE ") + (encounter.phaseIndex ?? 1) + "/" + (encounter.phaseCount ?? 3) : (en ? "ROOM " : "SALA ") + ((state.encounterIndex ?? 0) + 1)) +
      '</b></div><h2>' + esc(bossName ?? encounter?.name ?? "Héroes del sótano") + '</h2><p>' + esc(sceneText ?? "") + '</p><div class="dp-progress-track"><i style="width:' + revealProgress + '%"></i></div><div class="dp-scene-metrics"><span>' +
      (state.stage === "planning" ? (en ? "POWER TARGET" : "KRAFTZIEL") : (en ? "PARTY POWER / TARGET" : "GRUPPENKRAFT / ZIEL")) + '</span><b>' + (result ? result.partyPower + " / " + result.targetDifficulty : (encounter?.partyPower ?? "—") + " / " + (encounter?.difficulty ?? 0)) + "</b></div></div></section>" : "") +
    (!camp && encounter?.rule ? '<aside class="dp-room-rule"><i>' + encounter.rule.symbol + '</i><div><b>' + esc(encounter.rule.name) + '</b><span>' + esc(encounter.rule.description) + '</span></div></aside>' : '') +
    ((state.preparedPower ?? 0) > 0 ? '<div class="dp-scout-bonus">⌖ +' + state.preparedPower + ' ' + (en ? 'PARTY POWER FROM SCOUTS' : "EL PODER DEL GRUPO POR LOS SCOUTS") + '</div>' : '') +
    campPanel + routeVoting + responseActions + (storyScene || resultPanel) + (!beat || ["verdict", "impact", "settled"].includes(beat.scene) ? modifiers : '') + pending +
    ((!final && !done || cinematic) ? '<section class="dp-party"><div class="dp-section-label"><span>' + (en ? "THE PARTY" : "EL GRUPO") + '</span><b>' + (state.partyMorale ?? 3) + " " + (en ? "MORALE" : "MORAL") + '</b></div><div class="dp-hero-grid">' + heroRows + "</div></section>" : "") +
    '<footer class="dp-footer"><span>' + routeStatus + '</span><span>' + readyCount + "/" + heroes.length + " " + (state.stage === "voting" ? (en ? "votes" : "Stimmen") : state.stage === "response" ? (en ? "responses" : "Reaktionen") : readyPhase ? (en ? "ready" : "bereit") : (en ? "locked" : "entschieden")) +
    '</span><span>' + (en ? "CO-OP PROGRESS · INDIVIDUAL FAME" : "PROGRESO COOPERATIVO · GLORIA PERSONAL") + "</span></footer></main>";
}

const expansionStyles = `
.dp-camp-shell{background:radial-gradient(ellipse at 50% 22%,#61432955,transparent 65%),#171d1a}
.dp-party-modifiers{margin:0;color:#d9c390;font-size:11px;letter-spacing:.06em}
.dp-camp{overflow:hidden;border:1px solid #796143;border-radius:18px;background:#22251e;animation:rise .5s ease-out}
.dp-camp-landscape{position:relative;min-height:300px;overflow:hidden}.dp-camp-art{display:block;width:100%;height:300px;object-fit:cover}
.dp-camp-title{position:absolute;inset:24px 24px auto;text-align:center;text-shadow:0 2px 12px #000}.dp-camp-title small{font-size:9px;letter-spacing:.2em;color:#e7bd77}.dp-camp-title h2{font:clamp(28px,4vw,46px)/1.1 Georgia,serif;margin:9px 0}.dp-camp-title p{font-size:12px;margin:0;color:#d7c9ab}
.dp-camp-options{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;background:#715b3844}.dp-camp-options article{display:grid;gap:7px;align-content:start;padding:20px;background:#26291f}.dp-camp-options i{font:30px Georgia,serif;color:#edc989;font-style:normal}.dp-camp-options b{font:18px Georgia,serif}.dp-camp-options span{font-size:12px;line-height:1.4;color:#c5b99e}
.dp-camp-results{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:9px;padding:18px}.dp-camp-results article{display:flex;gap:12px;align-items:center;padding:10px;border-bottom:1px solid #665a3d}.dp-camp-results i{font:28px Georgia,serif;color:#e3ba75;font-style:normal}.dp-camp-results article>div{display:grid;gap:4px;flex:1}.dp-camp-results span{font-size:11px;color:#d3c1a3}.dp-camp-results small{font-size:10px;color:#a9b398}.dp-camp-results strong{display:flex;gap:4px;flex-wrap:wrap}
.dp-room-rule{display:flex;align-items:center;gap:16px;padding:10px 0;border-block:1px solid #94724155}.dp-room-rule>i{font:32px Georgia,serif;font-style:normal;color:#d6b173}.dp-room-rule>div{display:flex;align-items:baseline;flex-wrap:wrap;gap:8px 20px}.dp-room-rule b{font:18px Georgia,serif;color:#eed3a2}.dp-room-rule span{font-size:12px;color:#c0b195}.dp-scout-bonus{font-size:11px;letter-spacing:.1em;color:#b5cd95}
.dp-route-card{grid-template-columns:130px minmax(0,1fr);padding:0;overflow:hidden}.dp-route-card>div:last-child{padding:12px 14px 12px 0}.dp-route-art{height:100%;min-height:140px;background:transparent;border-radius:0;overflow:hidden}.dp-route-art svg{width:190px;height:100%;min-height:140px;stroke:none;fill:initial;flex:none}
.dp-flames{transform-origin:400px 246px;animation:dp-fire 1.6s ease-in-out infinite alternate}.dp-embers{animation:dp-sparks 2.4s ease-out infinite}.dp-luck-roll{font-size:14px!important;color:#c6d9a4!important}.dp-story-roll .dp-cube.rolling .dp-cube-body{animation-name:dp-cube-settle}
@keyframes dp-fire{from{transform:scaleX(.92) scaleY(.95);opacity:.85}to{transform:scaleX(1.06) scaleY(1.08);opacity:1}}
@keyframes dp-sparks{from{transform:translateY(18px);opacity:0}35%{opacity:.9}to{transform:translateY(-32px);opacity:0}}
@keyframes dp-cube-settle{0%{transform:translateY(-35px) rotate(-28deg) scale(.75)}45%{transform:translateY(6px) rotate(340deg) scale(1.08)}75%{transform:translateY(-6px) rotate(690deg)}100%{transform:translateY(0) rotate(720deg) scale(1)}}
@media(max-width:760px){.dp-camp-options{grid-template-columns:repeat(2,minmax(0,1fr))}.dp-camp-options article{padding:14px}.dp-camp-title{inset:20px 14px auto}.dp-camp-title p{max-width:40ch;margin:auto}.dp-route-card{grid-template-columns:110px minmax(0,1fr)}.dp-room-rule>div{gap:4px}.dp-room-rule span{font-size:11px}}
@media(min-width:761px){.dp-shell:has(.dp-result) .dp-scene{min-height:110px;grid-template-columns:150px minmax(0,1fr)}.dp-shell:has(.dp-result) .dp-illustration{min-height:110px}.dp-shell:has(.dp-result) .dp-scene-info{padding:14px 22px}.dp-shell:has(.dp-result) .dp-scene-info h2{font-size:28px;max-width:none;margin:6px 0 0}.dp-shell:has(.dp-result) .dp-scene-info>p,.dp-shell:has(.dp-result) .dp-progress-track,.dp-shell:has(.dp-result) .dp-scene-metrics{display:none}.dp-shell:has(.dp-result) .dp-scene-index{font-size:22px;left:10px;bottom:8px}}
@media(min-width:761px) and (max-height:820px){.dp-shell{padding:18px 24px;gap:10px}.dp-shell:has(.dp-result) .dp-history{display:none}.dp-shell:has(.dp-result) .dp-room-rule{padding:5px 0;gap:10px}.dp-shell:has(.dp-result) .dp-room-rule>i{font-size:22px}.dp-shell:has(.dp-result) .dp-room-rule b{font-size:14px}.dp-shell:has(.dp-result) .dp-result{padding:10px 12px;gap:7px}.dp-shell:has(.dp-result) .dp-die{width:34px;height:34px}.dp-shell:has(.dp-result) .dp-roll-card{padding:6px 8px;gap:4px 8px}.dp-shell:has(.dp-result) .dp-roll-card p{display:none}.dp-shell:has(.dp-result) .dp-roll-person{gap:6px}.dp-shell:has(.dp-result) .dp-hero-row{padding:7px 10px}.dp-shell:has(.dp-result) .dp-card-reveal-item em,.dp-shell:has(.dp-result) .dp-reward-card em{display:none}.dp-shell:has(.dp-result) .dp-card-reveal-item{padding:6px 9px}.dp-shell:has(.dp-result) .dp-card-glyph{width:24px;height:28px;font-size:18px}.dp-shell:has(.dp-result) .dp-reward-card{padding:5px 7px}.dp-shell:has(.dp-result) .dp-party{gap:7px}.dp-shell:has(.dp-result) .dp-footer{padding-top:7px}.dp-camp-landscape,.dp-camp-art{min-height:230px;height:230px}.dp-camp-options article{padding:14px}}
@media(prefers-reduced-motion:reduce){.dp-flames,.dp-embers{animation:none}.dp-shell *{animation-delay:0ms!important}}
@media(min-width:761px) and (max-height:820px){.dp-large-party:has(.dp-result){padding-block:12px;gap:8px}.dp-large-party:has(.dp-result) .dp-scene{display:none}.dp-large-party:has(.dp-result) .dp-hero-row{padding:5px 10px}.dp-large-party:has(.dp-result) .dp-hero-grid{gap:6px}.dp-shell:has(.dp-story-scene)>.dp-party{display:none}}
`;

const styles = [
  ".dp-shell{box-sizing:border-box;min-height:100%;padding:clamp(20px,3vw,48px);display:grid;grid-template-rows:auto auto auto 1fr auto;gap:clamp(14px,2.1vh,24px);background:linear-gradient(180deg,#171613d0,#171613f2),radial-gradient(ellipse at 52% -18%,#604329 0%,transparent 48%),url('/dungeon-party/ambient.webp') center/cover no-repeat,#171613;color:#f5ecd9;font-family:Inter,ui-sans-serif,system-ui,sans-serif}",
  ".dp-header{display:flex;align-items:center;justify-content:space-between;gap:24px}.dp-brand{display:flex;align-items:center;gap:13px}.dp-brand-mark{display:grid;place-items:center;width:48px;height:48px;border:1px solid #8f6736;border-radius:50%;font:30px Georgia,serif;color:#edc273;box-shadow:0 0 26px #d19a4c22}.dp-brand small{display:block;font-size:9px;letter-spacing:.2em;color:#c9a76b;font-weight:800}.dp-brand h1{font:700 clamp(20px,2.2vw,32px)/1 Georgia,serif;letter-spacing:.08em;margin:5px 0 0}.dp-brand h1 i{font-weight:400;color:#d6a451}.dp-path{display:flex;align-items:center;gap:7px}.dp-path-label{color:#a99574;font-size:9px;letter-spacing:.16em;margin-right:5px}.dp-path i{display:block;width:clamp(13px,1.4vw,21px);height:5px;border-radius:8px;background:#4d4538}.dp-path i.current{background:#efbf6e;box-shadow:0 0 14px #d49a49aa}.dp-path i.cleared{background:#7d9a6a}.dp-path i.failed{background:#9d574c}",
  ".dp-history{display:flex;align-items:center;gap:14px;color:#cfb983}.dp-history>b{font-size:9px;letter-spacing:.16em;white-space:nowrap}.dp-history>div{display:flex;align-items:center;gap:9px;overflow:hidden}.dp-path-step{display:flex;align-items:center;gap:6px;color:#b4a489;font:12px Georgia,serif;white-space:nowrap}.dp-path-step i{width:6px;height:6px;background:#bd8a4b;border-radius:50%;box-shadow:0 0 8px #bd8a4b77}",
  ".dp-scene{position:relative;min-height:clamp(250px,32vh,390px);display:grid;grid-template-columns:minmax(260px,.95fr) minmax(340px,1.05fr);overflow:hidden;border:1px solid #594832;border-radius:22px;background:linear-gradient(107deg,#31271c,#241f19 62%,#1e1b17);box-shadow:0 22px 70px #0006}.dp-illustration{position:relative;min-height:250px;background:#1b1915;overflow:hidden}.dp-illustration svg{position:absolute;width:100%;height:100%;inset:0;object-fit:cover}.dp-scene:after{position:absolute;content:\"\";inset:0;pointer-events:none;background:linear-gradient(90deg,transparent 32%,#262119 51%,transparent 100%);opacity:.56}.dp-scene-index{position:absolute;left:20px;bottom:15px;color:#e1bd7e;font:40px Georgia,serif;opacity:.72}.dp-scene-info{position:relative;z-index:1;padding:clamp(20px,3vw,40px);display:flex;flex-direction:column;justify-content:center}.dp-scene-kicker{display:flex;gap:15px;align-items:center;color:#dfb977;font-size:10px;font-weight:800;letter-spacing:.16em}.dp-scene-kicker b{padding:5px 8px;border:1px solid #715936;border-radius:999px;font-size:9px}.dp-scene-info h2{font:500 clamp(28px,4vw,51px)/1.03 Georgia,serif;margin:14px 0 8px;max-width:15ch}.dp-scene-info p{font-size:clamp(13px,1.25vw,17px);line-height:1.45;color:#c4b69c;max-width:48ch;margin:0}.dp-progress-track{height:6px;background:#4c3c2b;border-radius:9px;overflow:hidden;margin-top:26px}.dp-progress-track i{height:100%;display:block;background:linear-gradient(90deg,#ac5c3f,#eabf6f);transition:width .45s}.dp-scene-metrics{display:flex;justify-content:space-between;align-items:center;margin-top:8px;color:#ad9b7c;font-size:9px;letter-spacing:.13em}.dp-scene-metrics b{font:22px Georgia,serif;color:#f2d49b;letter-spacing:0}",
  ".dp-section-label{display:flex;justify-content:space-between;align-items:center;color:#beaa84;font-size:9px;font-weight:800;letter-spacing:.17em}.dp-section-label b{font:12px Georgia,serif;color:#edcd8b;letter-spacing:0}.dp-route-vote,.dp-action-strip,.dp-result,.dp-party{display:grid;gap:11px}.dp-route-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.dp-route-card{display:grid;grid-template-columns:62px minmax(0,1fr);gap:13px;align-items:center;min-height:112px;padding:12px;border:1px solid #695337;border-radius:15px;background:linear-gradient(120deg,#352a1e,#27211a);animation:rise .35s ease-out both;animation-delay:calc(var(--route-i)*70ms)}.dp-route-art{display:grid;place-items:center;height:62px;border-radius:13px;background:#473624;color:#e6bd72}.dp-route-art svg{width:42px;stroke:currentColor;stroke-width:2.4;fill:none}.dp-route-card span{color:#d0a866;font-size:8px;font-weight:800;letter-spacing:.14em}.dp-route-card h3{font:20px/1.05 Georgia,serif;margin:5px 0}.dp-route-card p{font-size:11px;line-height:1.3;color:#b9ad99;margin:0}",
  ".dp-action-chips{display:flex;gap:8px;flex-wrap:wrap}.dp-action-chip{display:flex;align-items:center;gap:8px;padding:8px 10px;border-radius:10px;background:#29241e;border:1px solid #514332}.dp-action-chip i{width:23px;height:23px;border-radius:50%;display:grid;place-items:center;background:#4b3927;color:#e8c780}.dp-action-chip i:before{content:\"⚔\";font-size:12px}.dp-action-chip i.action-aid:before,.dp-action-chip i.action-cleric_heal:before{content:\"✚\"}.dp-action-chip i.action-loot:before,.dp-action-chip i.action-rogue_lift:before{content:\"◈\"}.dp-action-chip i.action-warrior_guard:before{content:\"⬟\"}.dp-action-chip i.action-mage_burst:before{content:\"✦\"}.dp-action-chip i.action-bard_inspire:before{content:\"♫\"}.dp-action-chip i.action-tinkerer_improvise:before{content:\"⚒\"}.dp-action-chip b{font-size:11px}.dp-action-chip em{font:italic 11px Georgia,serif;color:#b9a98c}",
  ".dp-pending{display:flex;align-items:center;gap:13px;padding:12px 15px;background:#27221b;border-left:3px solid #c4934e;border-radius:0 11px 11px 0}.dp-pending-icon{display:grid;place-items:center;width:38px;height:38px;border-radius:50%;background:#493726;color:#efc374;font-size:22px}.dp-pending div:nth-child(2){display:grid;gap:3px;flex:1}.dp-pending b{font:17px Georgia,serif}.dp-pending span{font-size:11px;color:#b9aa91}.dp-pending>strong{font:19px Georgia,serif;color:#eac47a}",
  ".dp-result{padding:15px 18px 18px;border-radius:17px;border:1px solid #5a4a35;background:#211e19}.dp-result.success{border-color:#6a7a50}.dp-result.failure{border-color:#8b5145}.dp-result-heading{display:flex;align-items:center;gap:13px}.dp-verdict-mark{width:42px;height:42px;border-radius:50%;display:grid;place-items:center;background:#31412b;color:#c9dc9b;font:25px Georgia,serif}.failure .dp-verdict-mark{background:#542f29;color:#f1aa88}.dp-result-heading small{font-size:8px;letter-spacing:.19em;color:#b7a27d;font-weight:800}.dp-result-heading h2{font:22px Georgia,serif;margin:4px 0 0}.dp-total{margin-left:auto;display:grid;text-align:right;gap:3px}.dp-total span{font-size:8px;letter-spacing:.13em;color:#ac9c80}.dp-total b{font:25px Georgia,serif;color:#f4d99f}.dp-total i{font:14px Georgia,serif;color:#9e8b6d;padding:0 5px}.dp-dice-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(215px,1fr));gap:8px}.dp-roll-card{position:relative;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:7px 10px;padding:9px 11px;border:1px solid #473b2b;border-radius:12px;background:#2b261f;animation:rise .38s ease-out both;animation-delay:calc(var(--roll-i)*75ms)}.dp-roll-person{display:flex;align-items:center;gap:9px}.dp-roll-person>div{display:grid;gap:3px}.dp-roll-person>div>b{font-size:12px}.dp-roll-person small{font-size:9px;color:#b9a98c}.dp-die{display:grid;place-items:center;width:32px;height:32px;border-radius:8px;background:linear-gradient(140deg,#f0d494,#a97b3d);box-shadow:inset 0 1px 3px #fff7,0 4px 10px #0006;color:#322518;transform:rotate(-7deg);font:20px Georgia,serif}.dp-die.tumble{animation:die-tumble .9s cubic-bezier(.17,.7,.25,1) both}.dp-contribution{align-self:center;color:#efd08b;font:19px Georgia,serif}.dp-roll-changes{grid-column:1/-1;display:flex;gap:7px;min-height:16px}.dp-delta{padding:2px 6px;border-radius:99px;font-size:10px;background:#4a352a}.dp-delta.up{background:#33452d;color:#d4e2ad}.dp-delta.down{color:#f2b3a0}.dp-roll-card p{grid-column:1/-1;margin:0;color:#bfad8e;font-size:10px;line-height:1.35}",
  ".dp-card-reveal{display:grid;gap:7px;border-top:1px solid #51432f;padding-top:12px}.dp-revealed-cards{display:flex;gap:8px;overflow:auto}.dp-card-reveal-item{display:flex;align-items:center;gap:9px;min-width:190px;padding:8px 10px;border-radius:10px;border:1px solid #735638;background:linear-gradient(125deg,#483322,#30271e)}.dp-card-glyph{width:35px;height:43px;display:grid;place-items:center;border-radius:6px;background:#c99a58;color:#34271a;font:21px Georgia,serif;box-shadow:inset 0 0 0 2px #efd294}.dp-card-reveal-item div:last-child{display:grid;gap:2px}.dp-card-reveal-item small{font-size:9px;color:#bea987}.dp-card-reveal-item b{font:14px Georgia,serif}.dp-card-reveal-item span,.dp-card-reveal-item em{font-size:9px;color:#c2b295}.dp-card-reveal-item em{max-width:245px;font-style:normal;line-height:1.3;white-space:normal}",
  ".dp-party{align-content:start}.dp-hero-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(245px,1fr));gap:8px}.dp-hero-row{display:grid;grid-template-columns:20px 36px minmax(0,1fr) auto;align-items:center;gap:9px;padding:9px 11px;border:1px solid #40382c;border-radius:13px;background:#24211c;animation:rise .32s ease-out both;animation-delay:calc(var(--hero-i)*35ms)}.dp-hero-row.winner{border-color:#bd9655;background:#30271b}.dp-hero-row.hurt{animation:hit-shake .38s ease-out}.dp-rank{font:12px Georgia,serif;color:#8a7c64}.dp-hero-emblem{display:grid;place-items:center;width:34px;height:34px;border-radius:50%;background:#473a29;color:#ecc77b;font:18px Georgia,serif}.dp-hero-name{display:flex;align-items:baseline;gap:7px;min-width:0}.dp-hero-name b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.dp-hero-name span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#a99a7e;font-size:9px}.dp-hp-line{display:flex;align-items:center;gap:6px;margin-top:6px}.dp-hp-bar{height:4px;flex:1;max-width:94px;background:#45372b;border-radius:9px;overflow:hidden}.dp-hp-bar i{display:block;height:100%;background:linear-gradient(90deg,#a54f3d,#dfaa62)}.dp-hp-line>span{color:#c6b69b;font-size:9px;white-space:nowrap}.dp-hp-delta{font-size:9px}.dp-hp-delta.down{color:#f2a28b}.dp-hp-delta.up{color:#b9d498}.dp-score{text-align:right;display:grid;gap:1px}.dp-score span{font-size:7px;letter-spacing:.14em;color:#ae9b78}.dp-score b{font:21px Georgia,serif;color:#f1cf8c}.dp-score small{font-size:8px;color:#a99a7e;white-space:nowrap}",
  ".dp-finale{display:flex;align-items:center;gap:18px;padding:23px 28px;border:1px solid #9b7845;border-radius:20px;background:radial-gradient(ellipse at 15% 50%,#644727,#272119 70%)}.dp-finale-mark{display:grid;place-items:center;width:68px;height:68px;border-radius:50%;border:1px solid #d5ad63;color:#f0cd84;font:40px Georgia,serif}.dp-finale small{font-size:9px;letter-spacing:.2em;color:#d7ae66;font-weight:800}.dp-finale h2{font:32px Georgia,serif;margin:6px 0}.dp-finale p{margin:0;color:#c6b798;font-size:13px}.dp-footer{display:flex;justify-content:space-between;gap:12px;color:#8d806b;font-size:8px;letter-spacing:.16em;font-weight:800;border-top:1px solid #332e25;padding-top:12px}",
  "@keyframes die-tumble{0%{transform:rotate(-7deg) translateY(-13px) rotateX(0);filter:brightness(1.8)}40%{transform:rotate(220deg) translateY(-4px) rotateX(210deg)}75%{transform:rotate(430deg) translateY(-1px) rotateX(380deg)}100%{transform:rotate(713deg);filter:brightness(1)}}@keyframes rise{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:translateY(0)}}@keyframes card-flip{0%{opacity:0;transform:rotateY(80deg) translateY(10px)}100%{opacity:1;transform:rotateY(0) translateY(0)}}@keyframes hit-shake{0%,100%{transform:translateX(0)}25%{transform:translateX(-5px);box-shadow:0 0 0 1px #c65a49}60%{transform:translateX(4px)}}",
  "@media(max-width:760px){.dp-shell{padding:16px;gap:14px}.dp-header{align-items:flex-start}.dp-brand-mark{width:39px;height:39px;font-size:24px}.dp-brand h1{font-size:20px}.dp-brand small{font-size:7px}.dp-path{gap:4px}.dp-path i{width:11px}.dp-history{align-items:flex-start;flex-direction:column;gap:7px}.dp-scene{grid-template-columns:minmax(0,1fr);min-height:0}.dp-illustration{min-height:210px;height:24vh}.dp-scene:after{background:linear-gradient(180deg,transparent 35%,#262119 100%)}.dp-scene-info{padding:19px}.dp-scene-info h2{font-size:32px}.dp-route-grid{grid-template-columns:1fr}.dp-route-card{min-height:88px}.dp-dice-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.dp-hero-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.dp-hero-row{grid-template-columns:16px 29px minmax(0,1fr) auto;padding:8px 7px;gap:6px}.dp-hero-emblem{width:28px;height:28px;font-size:14px}.dp-hero-name{display:block}.dp-hero-name b{display:block;font-size:11px}.dp-hero-name span{display:block;font-size:8px}.dp-hp-bar{max-width:48px}.dp-footer{flex-wrap:wrap}.dp-footer span:last-child{width:100%}}",
  "@media(max-width:450px){.dp-path{max-width:45%;flex-wrap:wrap;justify-content:flex-end}.dp-path-label{width:100%;text-align:right}.dp-dice-grid{grid-template-columns:1fr}.dp-hero-grid{grid-template-columns:1fr}.dp-result-heading h2{font-size:19px}.dp-total b{font-size:21px}.dp-scene-metrics{font-size:8px}}",
  ".dp-sound-button{border:1px solid #756044;border-radius:999px;background:#29231b;color:#e7cb91;padding:7px 10px;font-size:9px;font-weight:800;letter-spacing:.08em;cursor:pointer}.dp-story-scene{min-height:205px;display:grid;align-content:center;justify-items:center;gap:13px;padding:20px;border:1px solid #755937;border-radius:18px;background:radial-gradient(ellipse at 50% 0%,#604328 0%,#29231b 73%);box-shadow:0 16px 50px #0007;animation:dp-story-in .42s cubic-bezier(.2,.7,.2,1) both}.dp-story-eyebrow{color:#e4bf79;font-size:9px;font-weight:900;letter-spacing:.2em;text-align:center}.dp-story-hero,.dp-story-card,.dp-story-roll{width:min(100%,760px);display:flex;align-items:center;justify-content:center;gap:clamp(18px,4vw,42px)}.dp-story-portrait{display:grid;place-items:center;width:clamp(72px,11vw,118px);height:clamp(72px,11vw,118px);flex:none;border:1px solid #b3844b;border-radius:50%;background:radial-gradient(circle at 40% 30%,#896139,#34271d 74%);color:#f3d290;font:clamp(38px,6vw,66px) Georgia,serif;box-shadow:0 0 45px #d2a04f24}.dp-story-hero h2,.dp-story-card h2,.dp-story-roll h2{margin:3px 0;font:clamp(24px,3.5vw,40px) Georgia,serif;color:#f5e6c7}.dp-story-hero small,.dp-story-card small,.dp-story-roll small{font-size:9px;letter-spacing:.16em;color:#b8a27d;text-transform:uppercase}.dp-story-hero b{color:#f0ca7e;font:17px Georgia,serif}.dp-story-hero p,.dp-story-card p{max-width:440px;margin:6px 0 0;color:#c8b89a;font-size:12px;line-height:1.4}.dp-story-progress{display:flex;gap:6px;justify-content:center}.dp-story-progress i{width:22px;height:4px;border-radius:9px;background:#4f4536}.dp-story-progress i.on{background:#edc06d;box-shadow:0 0 9px #eebc65aa}.dp-story-card{justify-content:flex-start;padding:18px 22px;border:1px solid #b98e52;border-radius:16px;background:linear-gradient(120deg,#574027,#2a2118);box-shadow:0 12px 36px #0007;animation:dp-card-stage .55s cubic-bezier(.2,.8,.2,1) both}.dp-story-card-art{display:grid;place-items:center;width:86px;height:110px;flex:none;border:2px solid #ebd09b;border-radius:10px;background:linear-gradient(145deg,#d1a55d,#624423);color:#322719;font:48px Georgia,serif;box-shadow:inset 0 0 0 4px #fff2,0 7px 18px #0008}.dp-story-roll{gap:clamp(18px,5vw,58px)}.dp-big-cube-wrap{width:clamp(142px,23vh,214px);height:clamp(142px,23vh,214px);filter:drop-shadow(0 16px 15px #0008)}.dp-cube{display:block;width:100%;height:100%;overflow:visible}.dp-cube-top{fill:#fff0cb;stroke:#704e30;stroke-width:2}.dp-cube-front{fill:#ecd29b;stroke:#704e30;stroke-width:2}.dp-cube-side{fill:#b2854e;stroke:#704e30;stroke-width:2}.dp-cube-edge{fill:none;stroke:#fff1d1;stroke-width:1.2;opacity:.5}.dp-cube-pips{fill:#402d1e}.dp-cube.rolling .dp-cube-body{transform-origin:50% 55%;animation:dp-cube-roll .8s cubic-bezier(.16,.72,.22,1) both}.dp-die{width:52px;height:52px;display:block;background:none;box-shadow:none;transform:none}.dp-die .dp-cube{overflow:visible}.dp-story-roll-copy{display:grid;gap:6px}.dp-story-roll-copy>b{color:#b8a27d;font-size:9px;letter-spacing:.15em}.dp-story-roll-copy>b i{font:26px Georgia,serif;color:#f2cf88;padding-left:6px}.verdict-scene{min-height:270px;text-align:center;transition:background .5s}.verdict-scene.success{background:radial-gradient(ellipse at center,#435337,#211e19 72%);border-color:#92a36d}.verdict-scene.failure{background:radial-gradient(ellipse at center,#5a3028,#211a18 72%);border-color:#b26a56}.dp-story-verdict-icon{font-size:44px;color:#efcc82;text-shadow:0 0 24px #ffe08b88}.verdict-scene h2{margin:0;font:clamp(23px,4vw,38px) Georgia,serif}.dp-story-total{display:flex;align-items:baseline;gap:9px;font:clamp(36px,5vw,54px) Georgia,serif;color:#f2d18e}.dp-story-total i{font:20px Georgia,serif;color:#a28c68}.dp-story-total b{color:#c9b58f}.dp-story-meter{width:min(82%,560px);height:7px;overflow:hidden;border-radius:99px;background:#191714}.dp-story-meter i{display:block;height:100%;background:linear-gradient(90deg,#a4bc70,#f0cf88);transition:width .8s ease}.failure .dp-story-meter i{background:linear-gradient(90deg,#a94e42,#e0a16c)}.impact-scene{align-content:start}.dp-story-impact-grid{display:grid;width:100%;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:7px}.dp-impact-card{display:grid;grid-template-columns:34px 1fr auto;align-items:center;gap:5px 9px;padding:9px 11px;border:1px solid #514332;border-radius:11px;background:#29241d}.dp-impact-card>span{grid-row:span 2;color:#ebc87f;font:23px Georgia,serif}.dp-impact-card div{display:grid;gap:2px}.dp-impact-card b{font:14px Georgia,serif}.dp-impact-card small{color:#baa98a;font-size:9px}.dp-impact-card>strong{display:grid;gap:4px}.dp-impact-card p{grid-column:2/-1;margin:0;color:#cbb896;font-size:10px}.dp-boss-rewards{width:100%;display:grid;gap:7px}.dp-reward-cards{display:flex;gap:7px;overflow-x:auto}.dp-reward-card{display:flex;align-items:center;gap:8px;min-width:165px;padding:7px 9px;border:1px solid #a98753;border-radius:9px;background:linear-gradient(115deg,#4c3925,#2c251c);animation:dp-card-stage .45s ease-out both;animation-delay:calc(var(--reward-i)*50ms)}.dp-reward-card .dp-card-glyph{width:28px;height:35px;flex:none;font-size:17px}.dp-reward-card span:last-child{display:grid;gap:3px}.dp-reward-card small{color:#c3a66f;font-size:8px;text-transform:uppercase;letter-spacing:.12em}.dp-reward-card b{font:12px Georgia,serif}.dp-scene.story-rolls .dp-illustration svg{animation:dp-monster-rumble .65s ease-in-out infinite alternate}.dp-scene.story-verdict.success{box-shadow:0 18px 70px #6c8d4e30}.dp-scene.story-verdict.failure{box-shadow:0 18px 70px #a2433733}.dp-story-skip{justify-self:end;border:1px solid #796648;border-radius:999px;padding:6px 10px;background:#211d18;color:#cfb991;font-size:9px;font-weight:800;cursor:pointer}.dp-story-scene .dp-story-skip{position:absolute;bottom:14px;right:16px}",
  "@keyframes dp-cube-roll{0%{transform:translateY(-35px) rotate(-28deg) rotateX(0) scale(.75);filter:brightness(1.3)}42%{transform:translateY(6px) rotate(250deg) rotateX(170deg) scale(1.08)}72%{transform:translateY(-4px) rotate(470deg) rotateX(340deg)}100%{transform:translateY(0) rotate(690deg) rotateX(540deg) scale(1);filter:brightness(1)}}@keyframes dp-story-in{from{opacity:0;transform:translateY(13px) scale(.985)}to{opacity:1;transform:translateY(0) scale(1)}}@keyframes dp-card-stage{from{opacity:0;transform:translateX(26px) rotateY(28deg) scale(.92)}to{opacity:1;transform:translateX(0) rotateY(0) scale(1)}}@keyframes dp-monster-rumble{from{transform:scale(1) rotate(-.4deg)}to{transform:scale(1.04) rotate(.5deg)}}",
  "@media(max-width:760px){.dp-story-scene{padding:16px;min-height:190px}.dp-story-hero,.dp-story-card,.dp-story-roll{gap:15px}.dp-story-hero p{font-size:10px}.dp-big-cube-wrap{width:132px;height:132px}.dp-story-impact-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.dp-impact-card{grid-template-columns:27px 1fr auto;padding:7px}.dp-sound-button{margin-left:auto}}",
  "@media(max-width:450px){.dp-story-impact-grid{grid-template-columns:1fr}.dp-story-hero{justify-content:flex-start}.dp-story-card{padding:12px}.dp-story-card-art{width:60px;height:82px}.dp-story-roll{justify-content:flex-start}.dp-big-cube-wrap{width:108px;height:108px}.dp-story-total{font-size:36px}}",
  "@media(prefers-reduced-motion:reduce){.dp-shell *{animation-duration:.01ms!important;animation-iteration-count:1!important;scroll-behavior:auto!important;transition:none!important}}"
].join("");

export const hostGame = {
  id: dungeonPartyManifest.id,
  displayName: dungeonPartyManifest.displayName,
  mountDom(rootValue: unknown, client: HostSource) {
    const root = rootValue as HTMLElement;
    const style = document.createElement("style");
    style.textContent = styles + expansionStyles;
    document.head.append(style);
    let lastResolutionId: string | undefined;
    let latestState: HostState | null = null;
    let beat: StoryBeat | undefined;
    let soundEnabled = true;
    let timer: number | undefined;
    let beats: StoryBeat[] = [];
    const audio = new DungeonAudioRig((enabled) => {
      soundEnabled = enabled;
      render(root, latestState, beat, soundEnabled);
    });
    const enterBeat = (index: number) => {
      beat = beats[index];
      const result = ((latestState?.game?.state ?? {}) as Partial<DungeonPartyPublicState>).lastResolution;
      if (!beat || !result) return;
      render(root, latestState, beat, soundEnabled);
      if (beat.scene === "cards") audio.card();
      else if (beat.scene === "rolls") audio.dice();
      else if (beat.scene === "verdict") audio.verdict(result.success);
      else if (beat.scene === "impact" && result.heroes.some((hero) => hero.healthDelta < 0)) audio.impact();
      const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      const duration = reduceMotion ? (beat.scene === "settled" ? 0 : 160) : beat.scene === "actions" ? 540 : beat.scene === "cards" ? 640 : beat.scene === "rolls" ? 860 : beat.scene === "verdict" ? 1_450 : beat.scene === "impact" ? 1_350 : 0;
      if (duration > 0) timer = window.setTimeout(() => enterBeat(index + 1), duration);
    };
    const update = (state: HostState | null) => {
      latestState = state;
      const gameState = (state?.game?.state ?? {}) as Partial<DungeonPartyPublicState>;
      const resolution = gameState.lastResolution;
      const resolutionId = resolution?.id;
      if (resolutionId && resolutionId !== lastResolutionId && resolution) {
        if (timer !== undefined) window.clearTimeout(timer);
        lastResolutionId = resolutionId;
        beats = [
          ...resolution.heroes.map((_hero, index) => ({ resolutionId, scene: "actions" as const, index })),
          ...resolution.cards.map((_card, index) => ({ resolutionId, scene: "cards" as const, index })),
          ...resolution.heroes.map((_hero, index) => ({ resolutionId, scene: "rolls" as const, index })),
          { resolutionId, scene: "verdict", index: 0 },
          { resolutionId, scene: "impact", index: 0 },
          { resolutionId, scene: "settled", index: 0 }
        ];
        enterBeat(0);
        return;
      }
      if (!resolutionId) {
        if (timer !== undefined) window.clearTimeout(timer);
        timer = undefined;
        lastResolutionId = undefined;
        beat = undefined;
        beats = [];
      }
      render(root, state, beat, soundEnabled);
    };
    const onRootClick = (event: MouseEvent) => {
      if (!(event.target instanceof Element) || !event.target.closest("[data-dp-sound]")) return;
      audio.setEnabled(!soundEnabled);
    };
    root.addEventListener("click", onRootClick);
    const unsubscribe = client.subscribe(update);
    update(client.getState());
    return () => {
      unsubscribe();
      root.removeEventListener("click", onRootClick);
      if (timer !== undefined) window.clearTimeout(timer);
      audio.destroy();
      style.remove();
      root.replaceChildren();
    };
  }
} as const;
