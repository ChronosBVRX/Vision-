import type { SchaetzoramaAnswer, SchaetzoramaPublicQuestion, SchaetzoramaPublicState, SchaetzoramaStanding } from "../protocol.js";

export { revealCategories, itemStartMs, itemStaggerMs, itemMoveMs, playerStaggerMs, scoreStartMs, solutionItemCount } from "../revealTiming.js";
import { itemStartMs, itemStaggerMs } from "../revealTiming.js";

export function revealPosition(state: SchaetzoramaPublicState) {
  return { step: state.revealStep, elapsed: state.revealElapsedMs, remaining: 0 };
}

export function standingMovements(standings: SchaetzoramaStanding[]) {
  const before = [...standings].sort((a, b) => (b.projectedScore - b.roundScore) - (a.projectedScore - a.roundScore) || a.playerId.localeCompare(b.playerId));
  const after = [...standings].sort((a, b) => b.projectedScore - a.projectedScore || a.playerId.localeCompare(b.playerId));
  return after.map((standing, index) => {
    const previousScore = standing.projectedScore - standing.roundScore;
    const previousRank = 1 + before.filter((other) => other.projectedScore - other.roundScore > previousScore).length;
    const rank = 1 + after.filter((other) => other.projectedScore > standing.projectedScore).length;
    return { ...standing, previousScore, previousRank, rank, shift: previousRank - rank,
      fromRow: before.findIndex((other) => other.playerId === standing.playerId) - index };
  });
}

export function renderVisualSolution(question: SchaetzoramaPublicQuestion, answer: SchaetzoramaAnswer | undefined, _language: "de" | "en", escape: (value: unknown) => string): string | null {
  if (question.kind === "rank" && answer?.kind === "rank") {
    return `<div class="sz-order-solution"><p>${escape(question.directionLabel)}</p><div class="sz-order-list">${answer.order.map((id, index) => {
      const original = question.items.findIndex((item) => item.id === id);
      return `<div class="sz-order-item" style="--from-row:${original - index};--item-delay:${itemStartMs + index * itemStaggerMs}ms"><b>${index + 1}</b><strong>${escape(question.items[original]?.label ?? id)}</strong><span aria-hidden="true">✓</span></div>`;
    }).join("")}</div></div>`;
  }
  if (question.kind === "assign" && answer?.kind === "assign") {
    const zones = ["left", "both", "right"];
    return `<div class="sz-zone-solution"><div class="sz-zone-labels"><strong>← ${escape(question.leftLabel)}</strong><strong>∩ ambos</strong><strong>${escape(question.rightLabel)} →</strong></div>${question.terms.map((term, index) => {
      const zone = answer.assignments[term.id];
      return `<div class="sz-zone-rail"><div class="sz-zone-item" data-solution-zone="${escape(zone)}" style="--zone:${zones.indexOf(zone)};--item-delay:${itemStartMs + index * itemStaggerMs}ms"><strong>${escape(term.label)}</strong><span aria-hidden="true">✓</span></div></div>`;
    }).join("")}</div>`;
  }
  return null;
}
