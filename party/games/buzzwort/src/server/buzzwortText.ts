import type { BuzzwortMode, BuzzwortRole, BuzzwortTeamId } from "../protocol.js";

export interface BuzzwortTexts {
  modeDuel: string;
  modeTeam: string;
  roleExplainer: string;
  roleWatcher: string;
  roleGuesser: string;
  roleBench: string;
  team1: string;
  team2: string;
  unknownPlayer: string;
  preparing: string;
  getReady: (name: string) => string;
  turnStart: (name: string, team?: string) => string;
  turnStartWithWatcher: (name: string, watcher: string, team?: string) => string;
  solved: (term: string, name: string) => string;
  skipped: (term: string) => string;
  violation: (term: string, watcher: string) => string;
  turnSummary: (name: string, solved: number, violations: number) => string;
  noCards: string;
  finalTeam: (winner: string | null, a: number, b: number) => string;
  finalDuel: (name: string | null, points: number) => string;
}

const de: BuzzwortTexts = {
  modeDuel: "Todos contra todos",
  modeTeam: "Teammodus",
  roleExplainer: "Explicador",
  roleWatcher: "guardián",
  roleGuesser: "Rater",
  roleBench: "Zuschauer",
  team1: "Team 1",
  team2: "Team 2",
  unknownPlayer: "Unbekannt",
  preparing: "Se está preparando la palabra de moda.",
  getReady: (name) => `${name} macht sich bereit …`,
  turnStart: (name, team) => (team ? `${team}: ${name} erklärt jetzt!` : `${name} erklärt jetzt!`),
  turnStartWithWatcher: (name, watcher, team) =>
    team
      ? `${team}: ${name} erklärt, ${watcher} bewacht die Karte.`
      : `${name} erklärt, ${watcher} bewacht die Karte.`,
  solved: (term, name) => `„${term}" — ${name} hat es!`,
  skipped: (term) => `„${term}" übersprungen.`,
  violation: (term, watcher) => `BUZZ! ${watcher} erwischt — „${term}" ist weg.`,
  turnSummary: (name, solved, violations) =>
    violations > 0
      ? `${name}: ${solved} Begriffe, ${violations}× Buzzwort.`
      : `${name}: ${solved} Begriffe geschafft.`,
  noCards: "No hay tarjetas de palabras de moda disponibles.",
  finalTeam: (winner, a, b) =>
    winner ? `${winner} gewinnt mit ${Math.max(a, b)}:${Math.min(a, b)}!` : `Unentschieden ${a}:${b}!`,
  finalDuel: (name, points) => (name ? `${name} führt mit ${points} Punkten.` : "Redondear.")
};

const en: BuzzwortTexts = {
  modeDuel: "Free-for-all",
  modeTeam: "Team mode",
  roleExplainer: "Explainer",
  roleWatcher: "Watcher",
  roleGuesser: "Guesser",
  roleBench: "Spectator",
  team1: "Team 1",
  team2: "Team 2",
  unknownPlayer: "Unknown",
  preparing: "Preparing Buzzword.",
  getReady: (name) => `${name} is getting ready …`,
  turnStart: (name, team) => (team ? `${team}: ${name} is explaining!` : `${name} is explaining!`),
  turnStartWithWatcher: (name, watcher, team) =>
    team ? `${team}: ${name} explains, ${watcher} watches the card.` : `${name} explains, ${watcher} watches the card.`,
  solved: (term, name) => `"${term}" — ${name} got it!`,
  skipped: (term) => `"${term}" skipped.`,
  violation: (term, watcher) => `BUZZ! ${watcher} caught it — "${term}" is gone.`,
  turnSummary: (name, solved, violations) =>
    violations > 0 ? `${name}: ${solved} terms, ${violations} forbidden calls.` : `${name}: ${solved} terms solved.`,
  noCards: "No Buzzword cards available.",
  finalTeam: (winner, a, b) =>
    winner ? `${winner} wins ${Math.max(a, b)}:${Math.min(a, b)}!` : `Draw ${a}:${b}!`,
  finalDuel: (name, points) => (name ? `${name} leads with ${points} points.` : "Round over.")
};

export function resolveBuzzwortTexts(language: string): BuzzwortTexts {
  return language === "en" ? en : de;
}

export function resolveModeLabel(texts: BuzzwortTexts, mode: BuzzwortMode): string {
  return mode === "team" ? texts.modeTeam : texts.modeDuel;
}

export function resolveTeamLabel(texts: BuzzwortTexts, teamId?: BuzzwortTeamId): string | undefined {
  if (teamId === "team1") {
    return texts.team1;
  }

  if (teamId === "team2") {
    return texts.team2;
  }

  return undefined;
}

export function resolveRoleLabel(texts: BuzzwortTexts, role: BuzzwortRole): string {
  switch (role) {
    case "explainer":
      return texts.roleExplainer;
    case "watcher":
      return texts.roleWatcher;
    case "guesser":
      return texts.roleGuesser;
    default:
      return texts.roleBench;
  }
}
