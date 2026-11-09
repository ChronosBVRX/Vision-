import type { SchaetzoramaPublicQuestion } from "./protocol.js";

// Shared by the authoritative server and the host animation/sound timeline.
export const revealCategories = ["number", "percent", "rank", "assign"] as const;
export const itemStartMs = 650;
export const itemStaggerMs = 800;
export const itemMoveMs = 1400;
export const playerStaggerMs = 1400;
export const playerMoveMs = 650;
export const autoContinueDelayMs = 2500;

export function solutionItemCount(question: SchaetzoramaPublicQuestion): number {
  return question.kind === "rank" ? question.items.length : question.kind === "assign" ? question.terms.length : 1;
}

export function scoreStartMs(question: SchaetzoramaPublicQuestion): number {
  return itemStartMs + (solutionItemCount(question) - 1) * itemStaggerMs + itemMoveMs + 450;
}

export function answersVisibleMs(question: SchaetzoramaPublicQuestion, playerCount: number): number {
  return scoreStartMs(question) + Math.max(0, playerCount - 1) * playerStaggerMs + playerMoveMs;
}
