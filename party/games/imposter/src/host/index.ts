import type { HostGame, HostGameStateSource } from "@open-party-lab/game-core";
import { imposterManifest } from "../manifest.js";
import type { ImposterPublicState } from "../protocol.js";
import { hostStyles } from "./styles.js";
interface HostState {
  game?: { phase?: string; roundNumber?: number; state?: unknown } | null;
  room?: { language?: "de" | "en"; pausedBy?: unknown; players?: Array<{ id: string; isReady?: boolean; connected?: boolean }> } | null;
}
const el = (tag: string, className?: string, value?: string): HTMLElement => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (value !== undefined) node.textContent = value;
  return node;
};
export function mountImposterHost(input: unknown, source: HostGameStateSource): () => void {
  const root = input as HTMLElement;
  const style = el("style"); style.textContent = hostStyles;
  root.classList.add("imposter-mount");
  let lastKey = "";
  let latest: HostState | null = null;
  let timerMs: number | null = null;
  let timerAt = performance.now();
  let previousGame: HostState["game"];
  let paused = false;
  const updateTimer = () => {
    const clock = root.querySelector<HTMLElement>(".imp-timer");
    if (!clock || timerMs === null) return;
    const left = Math.max(0, timerMs - (paused ? 0 : performance.now() - timerAt));
    clock.textContent = `${Math.ceil(left / 1000)} s`;
  };
  const draw = (app: HostState) => {
    latest = app;
    const game = app.game?.state as ImposterPublicState | undefined;
    const nextPaused = Boolean(app.room?.pausedBy);
    if (app.game !== previousGame) {
      timerMs = game?.remainingMs ?? null;
      timerAt = performance.now(); previousGame = app.game;
    } else if (paused !== nextPaused) {
      if (!paused && timerMs !== null) timerMs = Math.max(0, timerMs - (performance.now() - timerAt));
      timerAt = performance.now();
    }
    paused = nextPaused;
    const phase = app.game?.phase;
    const en = app.room?.language === "en";
    const connected = (app.room?.players ?? []).filter(({ connected }) => connected !== false);
    const ready = connected.filter(({ isReady }) => isReady).length;
    const key = JSON.stringify([game, phase, en, ready]);
    if (key === lastKey) { updateTimer(); return; }
    lastKey = key;
    const shell = el("main", `imp-host${game?.stage === "resolved" ? " is-result" : ""}`);
    const top = el("header", "imp-top");
    top.append(el("strong", "imp-brand", "El Infiltrado"), el("span", undefined, `${en ? "ROUND" : "RONDA"} ${app.game?.roundNumber ?? 1}`));
    const body = el("section", "imp-body");
    const heading = el("div", "imp-heading");
    const intro = phase === "round_intro" || phase === "countdown";
    let kicker = en ? "ONE OF YOU IS BLUFFING" : "UNO DE USTEDES MIENTE";
    let title = en ? "Trust your instinct." : "Confía en tu instinto.";
    let helper = en ? "Watch your phone. Keep your role secret." : "Mira tu celular y guarda tu papel en secreto.";
    if (intro) {
      title = en ? "Who is the Infiltrator?" : "¿Quién es el Infiltrado?";
      helper = game?.confirmClues === false
        ? (en ? "Share clues, discuss and vote on your phone." : "Da pistas, discute y vota desde tu móvil.")
        : (en ? "Two clue rounds. One secret vote. Find the bluff." : "Dos rondas de pistas. Un voto secreto. Encuentra al topo.");
    } else if (game?.stage === "avatar") {
      kicker = en ? "YOUR PLAYER PHOTOS" : "FOTOS DE JUGADORES";
      title = en ? "Give the bluff a face." : "Ponle cara al Infiltrado.";
      helper = en ? "Take a selfie on your phone or continue without a photo." : "Tómate una selfie en el celular o sigue sin foto.";
    } else if (game?.stage === "clues") {
      kicker = `${en ? "CLUE ROUND" : "RONDA DE PISTAS"} ${Math.floor(game.currentTurnIndex / game.clueOrder.length) + 1} / 2`;
      const name = game.players.find(({ id }) => id === game.currentTurnPlayerId)?.name ?? "…";
      title = en ? `${name}, your turn.` : `${name}, es tu turno.`;
      helper = en ? "Say one related word. Do not reveal the secret word. Confirm on your phone." : "Di una pista relacionada sin revelar la palabra. Confirma en tu celular.";
    } else if (game?.stage === "voting") {
      kicker = en ? "SECRET VOTE" : "VOTO SECRETO";
      title = en ? "Who is bluffing?" : "¿Quién está mintiendo?";
      helper = en ? "Discuss, then vote on your phone. The results stay hidden until everyone has voted." : "Discutan y voten desde el celular. Los votos se revelan cuando todos hayan votado.";
    } else if (game?.stage === "vote_reveal") {
      kicker = en ? "THE VOTE" : "EL VOTO";
      title = game.voteTied ? (en ? "A tie." : "Empate.")
        : `${game.players.find(({ id }) => id === game.voteResultPlayerId)?.name ?? "…"}${en ? " is suspected." : " está bajo sospecha."}`;
      helper = game.voteTied ? (en ? "Nobody is exposed. The Infiltrator escapes." : "Nadie está expuesto. El Infiltrado escapa.")
        : (en ? "Votes are in. Did your instinct nail it?" : "Votos contados. ¿Tu instinto acertó?");
    } else if (game?.stage === "imposter_guess") {
      kicker = en ? "THE LAST CHANCE" : "LA ÚLTIMA OPORTUNIDAD";
      const name = game.players.find(({ id }) => id === game.imposterRevealId)?.name ?? "…";
      title = en ? `${name} is the Infiltrator.` : `${name} es el Infiltrado.`;
      helper = en ? "One word guess on the phone. Keep the answer secret until then." : "Una palabra en el celular. No reveles la respuesta todavía.";
    } else if (game?.stage === "resolved") {
      kicker = game.imposterWon ? (en ? "THE INFILTRATOR WINS" : "EL INFILTRADO GANA") : (en ? "THE TEAM WINS" : "EL EQUIPO GANA");
      title = game.secretWord ?? "";
      const name = game.players.find(({ id }) => id === game.imposterRevealId)?.name ?? "…";
      helper = `${en ? "Infiltrator" : "Infiltrado"}: ${name}. ${game.resolvedReason ?? ""}`;
    }
    heading.append(el("p", "imp-kicker", kicker), el("h1", "imp-title", title), el("p", "imp-helper", helper));
    if (!intro && (game?.stage === "avatar" || game?.stage === "voting")) heading.append(el("p", "imp-progress", `${game.submittedCount} / ${game.playerCount} ${en ? "submitted" : "listos"}`));
    if (game?.stage === "imposter_guess" || game?.stage === "vote_reveal") heading.append(el("strong", "imp-timer", ""));
    if (game?.stage === "resolved" && game.imposterGuess) heading.append(el("p", "imp-guess", `${en ? "Word guess" : "Palabra"}: ${game.imposterGuess}`));
    body.append(heading);
    const grid = el("section", "imp-board");
    grid.setAttribute("aria-label", en ? "Players and game points" : "Jugadores y puntos del juego.");
    const players = game?.players ?? [];
    const count = players.length;
    // Fit a bounded grid to the actual host surface instead of a fixed canvas.
    const width = root.clientWidth || 1280;
    const height = root.clientHeight || 720;
    const availableHeight = Math.max(140, height * .59);
    let cols = 1, best = 0;
    for (let candidate = 1; candidate <= Math.min(10, count); candidate++) {
      const rows = Math.ceil(count / candidate);
      const size = Math.min((width - 96 - (candidate - 1) * 18) / candidate, (availableHeight - (rows - 1) * 18) / rows * .8);
      if (size > best) { best = size; cols = candidate; }
    }
    const rows = Math.max(1, Math.ceil(count / cols));
    grid.style.setProperty("--cols", String(cols)); grid.style.setProperty("--rows", String(rows));
    grid.style.setProperty("--card-width", `${Math.max(1, Math.min(290, best))}px`);
    grid.classList.toggle("is-dense", count > 10);
    players.forEach((player, index) => {
      const active = !intro && game?.stage === "clues" && player.id === game.currentTurnPlayerId;
      const exposed = player.id === game?.imposterRevealId;
      const suspect = player.id === game?.voteResultPlayerId;
      const card = el("article", `imp-photo${active ? " is-active" : ""}${exposed ? " is-imposter" : ""}${suspect ? " is-suspect" : ""}`);
      card.style.setProperty("--tilt", `${[-2, 1.5, -.8, 2, -1.4][index % 5]}deg`);
      card.style.setProperty("--i", String(index));
      if (player.avatar) {
        const photo = document.createElement("img"); photo.className = "imp-portrait"; photo.src = player.avatar; photo.alt = player.name;
        card.append(photo);
      } else card.append(el("div", "imp-portrait imp-placeholder", player.name.slice(0, 1).toLocaleUpperCase()));
      const votes = game?.voteCounts.find(({ playerId }) => playerId === player.id)?.votes ?? 0;
      if (game && ["vote_reveal", "imposter_guess", "resolved"].includes(game.stage)) card.append(el("span", "imp-ballot", `${votes} ${en ? (votes === 1 ? "vote" : "votes") : (votes === 1 ? "voto" : "votos")}`));
      if (active || exposed) card.append(el("span", "imp-role", exposed ? "El Infiltrado" : en ? "Your turn" : "Es tu turno"));
      const caption = el("footer", "imp-caption");
      caption.append(el("strong", "imp-name", player.name), el("b", "imp-points", `${player.points} ${en ? "pt" : "pts"}`));
      card.append(caption);
      if (game?.stage === "resolved" && player.delta) card.append(el("span", "imp-delta", `+${player.delta}`));
      grid.append(card);
    });
    body.append(grid);
    const bottom = el("footer", "imp-footer");
    bottom.append(el("span", undefined, en ? "Game points · Imposter +2 / Crew +1" : "Puntos · Infiltrado +2 / Equipo +1"),
      el("span", undefined, phase === "finished" ? `${ready} / ${connected.length} ${en ? "ready for the next word" : "listos para la siguiente palabra"}`
        : game?.stage === "resolved" ? (en ? "Next: ready up on your phone" : "Siguiente: marquen listo en el celular") : en ? "Keep your role secret." : "Guarden su papel en secreto."));
    shell.append(top, body, bottom); root.replaceChildren(style, shell); updateTimer();
  };
  const unsubscribe = (source as HostGameStateSource<HostState>).subscribe(draw);
  const initial = (source as HostGameStateSource<HostState>).getState(); if (initial) draw(initial);
  const resize = new ResizeObserver(() => { if (latest) { lastKey = ""; draw(latest); } }); resize.observe(root);
  const ticker = window.setInterval(updateTimer, 200);
  return () => { unsubscribe(); resize.disconnect(); window.clearInterval(ticker); root.replaceChildren(); root.classList.remove("imposter-mount"); };
}
export const hostGame = { id: imposterManifest.id, displayName: imposterManifest.displayName, mountDom: mountImposterHost } satisfies HostGame;
