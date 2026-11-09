import type { HostGameStateSource } from "@open-party-lab/game-core";
import type {
  SchaetzoramaAnswer,
  SchaetzoramaCategoryId,
  SchaetzoramaPublicQuestion,
  SchaetzoramaPublicState
} from "../protocol.js";
import { installSchaetzoramaHostStyles } from "./schaetzoramaHostStyles.js";
import { createSchaetzoramaSounds } from "./schaetzoramaSounds.js";
import { numericComparison, renderAnswerComparison } from "./schaetzoramaAnswerComparison.js";
import { playerStaggerMs, itemMoveMs, itemStaggerMs, itemStartMs, renderVisualSolution, revealPosition, scoreStartMs, solutionItemCount, standingMovements } from "./schaetzoramaReveal.js";

interface HostAppStateLike {
  game?: { phase?: string; state?: unknown } | null;
  room?: { code?: string; language?: "de" | "en"; lifecycle?: string; pausedBy?: unknown } | null;
}

const categories: SchaetzoramaCategoryId[] = ["number", "percent", "rank", "assign"];

const labels = {
  brandLine: "El conocimiento se encuentra con el instinto",
  room: "Sala",
  noLimit: "Sin límite de tiempo",
  answering: "Estimaciones secretas",
  joker: "Fase de copia",
  locked: "Respuestas cerradas",
  intro: "Cuatro tareas. Confía en tu instinto.",
  source: "Fuente",
  correct: "Solución correcta",
  closest: "Más cerca",
  copied: "copiada",
  points: "puntos",
  roundPoints: "puntos de ronda",
  finalTitle: "Ronda terminada",
  finalSubtitle: "Así se reparten los puntos de las cuatro tareas.",
  sessionTitle: "Cifras salvajes terminó",
  sessionSubtitle: "La clasificación general después de diez rondas.",
  totalPoints: "Puntos totales",
  categories: { number: "Número", percent: "Porcentaje", rank: "Orden", assign: "Clasificación" }
} as const;

export function mountSchaetzoramaHost(rootInput: unknown, source: HostGameStateSource): () => void {
  const root = rootInput as HTMLElement;
  installSchaetzoramaHostStyles();
  root.className = "sz-host-mount";
  let viewKey = "";
  const sounds = createSchaetzoramaSounds();
  let cueKey = "";
  let cueTimers: number[] = [];
  const syncSoundButton = () => {
    const button = root.querySelector<HTMLButtonElement>("[data-sz-sound]");
    if (!button) return;
    button.textContent = sounds.muted ? "♪ ×" : "♪";
    button.setAttribute("aria-pressed", String(!sounds.muted));
  };
  const onClick = (event: Event) => {
    if ((event.target as Element).closest("[data-sz-sound]")) { sounds.toggle(); syncSoundButton(); }
  };
  root.addEventListener("click", onClick);
  const clearCues = () => { cueTimers.forEach(window.clearTimeout); cueTimers = []; };

  const render = (stateInput: unknown) => {
    const state = stateInput as HostAppStateLike;
    const language = state.room?.language === "en" ? "en" : "de";
    const gameState = state.game?.state as SchaetzoramaPublicState | undefined;
    const phase = state.game?.phase ?? state.room?.lifecycle ?? "";
    const intro = !gameState || phase === "round_intro" || phase === "countdown";
    const nextViewKey = language + ":" + (gameState?.roundContent.roundIndex ?? 0) + ":" + (intro ? "intro" : gameState.stage === "revealed" ? "reveal:" + Math.min(5, gameState.revealStep) : "play");
    const markup = renderHost(state, gameState, phase, language);
    if (viewKey !== nextViewKey) {
      root.style.setProperty("--reveal-age", (gameState?.revealElapsedMs ?? 0) + "ms");
      root.innerHTML = markup;
      viewKey = nextViewKey;
    } else {
      // Keep questions and animated results mounted when readiness changes.
      const template = document.createElement("template");
      template.innerHTML = markup;
      for (const selector of [".sz-encabezado-host", ".sz-score-rail", ".sz-reveal-ready"]) {
        const current = root.querySelector(selector);
        const next = template.content.querySelector(selector);
        if (current && next && current.outerHTML !== next.outerHTML) current.replaceWith(next);
      }
    }
    syncSoundButton();
    const paused = Boolean(state.room?.pausedBy);
    root.classList.toggle("sz-is-paused", paused);
    if (paused) { clearCues(); cueKey = ""; return; }
    if (gameState?.stage === "revealed") {
      const { step, elapsed } = revealPosition(gameState);
      const nextCueKey = gameState.revealedAt + ":" + step;
      if (cueKey !== nextCueKey) {
        clearCues(); cueKey = nextCueKey;
        const schedule = (at: number, kind: Parameters<typeof sounds.play>[0], index = 0) => {
          if (at < elapsed - 150) return;
          cueTimers.push(window.setTimeout(() => sounds.play(kind, index), Math.max(0, at - elapsed)));
        };
        if (step < categories.length) {
          const question = gameState.roundContent.questions[categories[step]];
          schedule(0, "reveal");
          if (question.kind === "number" || question.kind === "percent") schedule(itemStartMs, "sweep");
          for (let index = 0; index < solutionItemCount(question); index++) schedule(itemStartMs + index * itemStaggerMs + itemMoveMs, "land", index);
          gameState.results.forEach((_, index) => {
            schedule(scoreStartMs(question) + index * playerStaggerMs, "answer", index);
            schedule(scoreStartMs(question) + index * playerStaggerMs + 280, "points", index);
            const at = scoreStartMs(question) + index * playerStaggerMs;
            if (at >= elapsed - 150) cueTimers.push(window.setTimeout(() => {
              const row = root.querySelectorAll<HTMLElement>(".sz-answer-row, .sz-comparison tbody tr")[index];
              const board = root.querySelector<HTMLElement>(".sz-answer-board, .sz-comparison-wrap");
              if (row && board) {
                const below = row.getBoundingClientRect().bottom - board.getBoundingClientRect().bottom;
                if (below > 0) board.scrollBy({ top: below + 8, behavior: "smooth" });
              }
            }, Math.max(0, at - elapsed)));
          });
        } else if (step === categories.length) { schedule(0, "final"); schedule(1200, "points"); }
      }
    } else { clearCues(); cueKey = ""; }
  };

  const unsubscribe = source.subscribe(render);
  const initial = source.getState();
  if (initial) render(initial);

  return () => {
    clearCues();
    sounds.dispose();
    root.removeEventListener("click", onClick);
    root.style.removeProperty("--reveal-age");
    unsubscribe();
    root.className = "";
    root.replaceChildren();
  };
}

function renderHost(state: HostAppStateLike, gameState: SchaetzoramaPublicState | undefined, phase: string, language: "de" | "en"): string {
  if (!gameState || phase === "round_intro" || phase === "countdown") return renderIntro(state, gameState, language);
  if (gameState.stage === "revealed") return renderReveal(state, gameState, language);
  return renderPlay(state, gameState, language);
}

function renderIntro(state: HostAppStateLike, gameState: SchaetzoramaPublicState | undefined, language: "de" | "en"): string {
  const text = labels;
  return `<main class="sz-host sz-intro">
    <div class="sz-intro__bands" aria-hidden="true">${categories.map((category, index) => `<span class="is-${category}" style="--delay:${index * 90}ms"></span>`).join("")}</div>
    <section class="sz-intro__content">
      <p class="sz-kicker">${escapeHtml(gameState?.roundContent.roundLabel ?? "Cifras salvajes")}</p>
      <h1>Cifras salvajes</h1><p>${text.intro}</p>
      <div class="sz-intro__legend">${categories.map((category) => `<span class="is-${category}">${categoryGlyph(category)} ${text.categories[category]}</span>`).join("")}</div>
    </section>
    <p class="sz-room-code">${text.room} <strong>${escapeHtml(state.room?.code ?? "----")}</strong></p>
  </main>`;
}

function renderPlay(state: HostAppStateLike, gameState: SchaetzoramaPublicState, language: "de" | "en"): string {
  const text = labels;
  const progress = gameState.progress;
  const doneCount = progress.filter((player) => gameState.stage === "joker" ? player.jokerReady : player.answered).length;
  const stageLabel = gameState.stage === "joker" ? text.joker : text.answering;
  const timeLabel = gameState.stage === "joker" && gameState.jokerEndsAt
    ? `${Math.max(0, Math.ceil((gameState.jokerEndsAt - Date.now()) / 1_000))} s`
    : text.noLimit;
  return `<main class="sz-host sz-play">
    ${renderHeader(state, gameState, stageLabel, timeLabel, language)}
    <section class="sz-question-grid">${categories.map((category, index) => renderQuestionCard(gameState.roundContent.questions[category], language, index)).join("")}</section>
    <aside class="sz-score-rail">
      <div class="sz-score-rail__head"><span>${text.locked}</span><strong>${doneCount}/${progress.length}</strong></div>
      <div class="sz-progress-list">${progress.map((player) => {
        const done = gameState.stage === "joker" ? player.jokerReady : player.answered;
        return `<div class="sz-player-progress ${done ? "is-done" : ""}"><i style="--player:${safeColor(player.color)}"></i><span>${escapeHtml(player.name)}</span><b>${done ? "✓" : "…"}</b></div>`;
      }).join("")}</div>
      ${renderStandings(gameState, language)}
    </aside>
  </main>`;
}

function renderHeader(state: HostAppStateLike, gameState: SchaetzoramaPublicState, stage: string, meta: string, language: "de" | "en"): string {
  const text = labels;
  return `<header class="sz-host-header">
    <div class="sz-brand"><strong>Cifras salvajes</strong><span>${text.brandLine}</span></div>
    <div class="sz-round"><span>${escapeHtml(gameState.roundContent.roundLabel)}</span><strong>${escapeHtml(stage)}</strong></div>
    <div class="sz-live-meta"><div class="sz-meta-actions"><span>${escapeHtml(meta)}</span><button type="button" data-sz-sound aria-label="Efectos de sonido" title="Activar o desactivar efectos de sonido" aria-pressed="true">♪</button></div><b>${text.room} ${escapeHtml(state.room?.code ?? "----")}</b></div>
  </header>`;
}

function renderQuestionCard(question: SchaetzoramaPublicQuestion, language: "de" | "en", index: number): string {
  const text = labels;
  return `<article class="sz-question is-${question.categoryId}" style="--enter-delay:${index * 75}ms">
    <div class="sz-question__top"><span class="sz-category-mark">${categoryGlyph(question.categoryId)}</span><div><small>${text.categories[question.categoryId]}</small><strong>${escapeHtml(question.title)}</strong></div>${questionGraphic(question)}</div>
    <h2>${escapeHtml(question.prompt)}</h2><p>${escapeHtml(questionHint(question, language))}</p>
  </article>`;
}

function renderStandings(gameState: SchaetzoramaPublicState, language: "de" | "en"): string {
  const text = labels;
  return `<div class="sz-standings"><p>${text.roundPoints}</p>${gameState.standings.map((standing, index) => `<div><span><b>${index + 1}</b>${escapeHtml(standing.name)}</span><strong>${standing.projectedScore}</strong></div>`).join("")}</div>`;
}

function renderReveal(state: HostAppStateLike, gameState: SchaetzoramaPublicState, language: "de" | "en"): string {
  const { step } = revealPosition(gameState);
  if (step > categories.length) return renderFinal(state, gameState, language);
  if (step >= categories.length) return renderStandingsMovement(state, gameState, language);
  const category = categories[step];
  const text = labels;
  const question = gameState.roundContent.questions[category];
  const solution = gameState.solutions[category];
  const entries = categoryEntries(gameState, category);
  return `<main class="sz-host sz-reveal is-${category}">
    ${renderHeader(state, gameState, `${text.categories[category]} · ${step + 1}/4`, text.correct, language)}
    <section class="sz-reveal__main">
      <div class="sz-reveal__question"><div class="sz-reveal__eyebrow"><span>${categoryGlyph(category)}</span>${escapeHtml(question.prompt)}</div><p>${text.correct}</p>${renderVisualSolution(question, solution, language, escapeHtml) ?? renderNumericGauge(question, solution) + `<h1>${escapeHtml(formatAnswer(question, solution, language, true))}</h1>`}<small>${text.source}: ${escapeHtml(question.source.label)}</small></div>
      ${question.kind === "rank" || question.kind === "assign" ? renderAnswerComparison(question, solution, entries.map((entry) => entry.result), language, escapeHtml) : `<div class="sz-answer-board"><p>Tus estimaciones</p>${entries.map((entry, index) => `<div class="sz-answer-row" style="--player:${safeColor(entry.result.color)};--row-delay:${scoreStartMs(question) + index * playerStaggerMs}ms"><span class="sz-answer-row__rank">${index + 1}</span><strong>${escapeHtml(entry.result.name)}</strong><div>${numericComparison(question, entry.answer, solution, language, escapeHtml)}</div><em${entry.result.joker?.categoryId === category ? "" : " hidden"}>${text.copied}</em><b>+${entry.score}</b></div>`).join("")}</div>`}
    </section>
    <div class="sz-reveal-ready" role="status">${renderRevealReadiness(gameState, language)}</div>
    <nav class="sz-reveal-steps">${categories.map((entry, index) => `<i class="is-${entry} ${index <= step ? "is-active" : ""}"></i>`).join("")}</nav>
  </main>`;
}

function renderRevealReadiness(state: SchaetzoramaPublicState, language: "de" | "en"): string {
  if (!state.revealAnswersVisible) return "Las respuestas se revelan...";
  const count = state.progress.filter((player) => state.autoContinueByPlayerId[player.playerId] || state.revealReadyByPlayerId[player.playerId]).length;
  return count + "/" + state.progress.length + "listos · Continuar en el celular";
}

function renderNumericGauge(question: SchaetzoramaPublicQuestion, solution: SchaetzoramaAnswer | undefined): string {
  if ((question.kind !== "number" && question.kind !== "percent") || solution?.kind !== "number") return "";
  const fraction = Math.max(0, Math.min(1, (solution.value - question.min) / Math.max(1, question.max - question.min)));
  return '<div class="sz-gauge" aria-hidden="true" style="--needle-angle:' + (-90 + fraction * 180) + 'deg"><div class="sz-gauge-arc"></div><i class="sz-gauge-needle"></i><b class="sz-gauge-hub"></b><span class="sz-gauge-min">' + question.min + '</span><span class="sz-gauge-max">' + question.max + '</span></div>';
}

function renderFinal(state: HostAppStateLike, gameState: SchaetzoramaPublicState, language: "de" | "en"): string {
  const text = labels;
  if (gameState.roundContent.roundIndex === 10) {
    const standings = [...gameState.standings].sort((left, right) => right.projectedScore - left.projectedScore);
    return `<main class="sz-host sz-final">
      ${renderHeader(state, gameState, text.sessionTitle, text.totalPoints, language)}
      <section class="sz-final__title"><p>${text.sessionSubtitle}</p></section>
      <section class="sz-final-board">${standings.map((standing, index) => `<article class="sz-final-row ${index === 0 ? "is-winner" : ""}" style="--player:${safeColor(standing.color)};--row-delay:${index * 90}ms"><span class="sz-final-row__place">${index + 1}</span><i></i><strong>${escapeHtml(standing.name)}</strong><div class="sz-final-row__breakdown"><span>+${standing.roundScore} ${text.roundPoints}</span></div><b>${standing.projectedScore}<small>${text.points}</small></b></article>`).join("")}</section>
    </main>`;
  }
  const sorted = [...gameState.results].sort((left, right) => right.total - left.total);
  return `<main class="sz-host sz-final">
    ${renderHeader(state, gameState, text.finalTitle, text.roundPoints, language)}
    <section class="sz-final__title"><p>${text.finalSubtitle}</p></section>
    <section class="sz-final-board">${sorted.map((result, index) => `<article class="sz-final-row ${index === 0 ? "is-winner" : ""}" style="--player:${safeColor(result.color)};--row-delay:${index * 90}ms"><span class="sz-final-row__place">${index + 1}</span><i></i><strong>${escapeHtml(result.name)}</strong><div class="sz-final-row__breakdown">${categories.map((category) => `<span class="is-${category}">${categoryGlyph(category)} +${result.categoryScores[category]}</span>`).join("")}</div><b>${result.total}<small>${text.points}</small></b></article>`).join("")}</section>
  </main>`;
}

function renderStandingsMovement(state: HostAppStateLike, gameState: SchaetzoramaPublicState, language: "de" | "en"): string {
  const final = gameState.roundContent.roundIndex === 10;
  const title = final ? labels.sessionTitle : "Clasificación general";
  const entries = standingMovements(gameState.standings);
  return `<main class="sz-host sz-final sz-movement">
    ${renderHeader(state, gameState, title, labels.totalPoints, language)}
    <section class="sz-final__title"><p>El resultado provisional después de esta ronda</p></section>
    <section class="sz-movement-board">${entries.map((entry) => `<article class="sz-movement-row" style="--player:${safeColor(entry.color)};--from-row:${entry.fromRow}">
      <span class="sz-movement-place"><b class="sz-before">${entry.previousRank}</b><b class="sz-after">${entry.rank}</b></span>
      <i></i><strong>${escapeHtml(entry.name)}</strong>
      <span class="sz-movement-shift sz-after">${entry.shift > 0 ? `↑ ${entry.shift}` : entry.shift < 0 ? `↓ ${-entry.shift}` : "="}</span>
      <span class="sz-movement-gain">+${entry.roundScore}</span>
      <span class="sz-movement-total"><b class="sz-before">${entry.previousScore}</b><b class="sz-after">${entry.projectedScore}</b></span>
    </article>`).join("")}</section>
  </main>`;
}

function categoryEntries(gameState: SchaetzoramaPublicState, category: SchaetzoramaCategoryId) {
  const solution = gameState.solutions[category];
  return gameState.results.map((result) => {
    const answer = result.answers[category];
    const score = result.categoryScores[category];
    const distance = solution?.kind === "number" && answer?.kind === "number" ? Math.abs(solution.value - answer.value) : -score;
    return { result, answer, score, distance };
  }).sort((left, right) => left.distance - right.distance || right.score - left.score || left.result.name.localeCompare(right.result.name));
}

function formatAnswer(question: SchaetzoramaPublicQuestion, answer: SchaetzoramaAnswer | undefined, language: "de" | "en", detailed: boolean): string {
  if (!answer) return "–";
  if (answer.kind === "number") {
    const unit = "unitLabel" in question ? question.unitLabel ?? (question.kind === "percent" ? "%" : "") : "";
    return `${answer.value}${unit ? ` ${unit}` : ""}`;
  }
  if (answer.kind === "rank" && question.kind === "rank") return answer.order.map((id) => question.items.find((item) => item.id === id)?.label ?? id).join(" › ");
  if (answer.kind === "assign" && question.kind === "assign") {
    if (!detailed) return `${Object.keys(answer.assignments).length} asignados`;
    return question.terms.map((term) => {
      const zone = answer.assignments[term.id];
      const label = zone === "left" ? question.leftLabel : zone === "right" ? question.rightLabel : "ambos";
      return `${term.label}: ${label}`;
    }).join(" · ");
  }
  return "–";
}

function questionHint(question: SchaetzoramaPublicQuestion, language: "de" | "en"): string {
  if (question.kind === "number" || question.kind === "percent") {
    const unit = question.unitLabel ?? (question.kind === "percent" ? "%" : "");
    return `${question.min} — ${question.max}${unit ? ` ${unit}` : ""}`;
  }
  if (question.kind === "rank") return question.directionLabel;
  if (question.kind === "assign") {
    return `${question.leftLabel} · ambos · ${question.rightLabel}`;
  }
  return "";
}

function questionGraphic(question: SchaetzoramaPublicQuestion): string {
  if (question.kind === "number") return `<div class="sz-mini-scale"><i></i><i></i><i></i><b></b></div>`;
  if (question.kind === "percent") return `<div class="sz-mini-donut"></div>`;
  if (question.kind === "rank") return `<div class="sz-mini-rank"><i></i><i></i><i></i></div>`;
  return `<div class="sz-mini-venn"><i></i><i></i></div>`;
}

function categoryGlyph(category: SchaetzoramaCategoryId): string {
  return category === "number" ? "#" : category === "percent" ? "%" : category === "rank" ? "↕" : "◉";
}

function safeColor(value: string): string { return /^#[0-9a-f]{6}$/i.test(value) ? value : "#697178"; }
function escapeHtml(value: unknown): string {
  return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}
