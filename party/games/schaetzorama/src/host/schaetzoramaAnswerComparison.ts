import type { SchaetzoramaAnswer, SchaetzoramaCategoryId, SchaetzoramaPublicQuestion, SchaetzoramaPlayerRoundResult } from "../protocol.js";
import { scoreStartMs, playerStaggerMs } from "./schaetzoramaReveal.js";

type Escape = (value: unknown) => string;

export function numericComparison(question: SchaetzoramaPublicQuestion, answer: SchaetzoramaAnswer | undefined, solution: SchaetzoramaAnswer | undefined, _language: "de" | "en", escape: Escape): string {
  if (answer?.kind !== "number" || solution?.kind !== "number") return escape("Sin respuesta");
  const delta = answer.value - solution.value;
  const unit = question.kind === "percent" ? "%" : "unitLabel" in question ? question.unitLabel ?? "" : "";
  const differenceUnit = question.kind === "percent" ? (Math.abs(delta) === 1 ? "punto porcentual" : "puntos porcentuales") : unit;
  const note = delta === 0 ? "Exactamente correcto" : `${Math.abs(delta)} ${differenceUnit} ${delta > 0 ? "demasiado alto" : "demasiado bajo"}`;
  return `<span class="sz-numeric-answer"><strong>${escape(`${answer.value} ${unit}`.trim())}</strong><small>${delta === 0 ? "✓" : "≠"} ${escape(note)}</small></span>`;
}

export function renderAnswerComparison(question: SchaetzoramaPublicQuestion, solution: SchaetzoramaAnswer | undefined, results: SchaetzoramaPlayerRoundResult[], _language: "de" | "en", escape: Escape): string {
  const category = question.categoryId as SchaetzoramaCategoryId;
  const missing = "Sin respuesta";
  const zoneLabel = (zone: string | undefined) => question.kind !== "assign" ? missing : zone === "left" ? `← ${question.leftLabel}` : zone === "right" ? `${question.rightLabel} →` : zone === "both" ? "∩ ambos" : missing;
  const rankLabel = (id: string | undefined) => question.kind === "rank" ? question.items.find((item) => item.id === id)?.label ?? missing : missing;
  const columns = question.kind === "rank" && solution?.kind === "rank"
    ? solution.order.map((id, index) => ({ heading: `Lugar ${index + 1}`, correct: rankLabel(id), choice: (answer: SchaetzoramaAnswer | undefined) => ({ label: rankLabel(answer?.kind === "rank" ? answer.order[index] : undefined), correct: answer?.kind === "rank" && answer.order[index] === id }) }))
    : question.kind === "assign" && solution?.kind === "assign"
      ? question.terms.map((term) => ({ heading: term.label, correct: zoneLabel(solution.assignments[term.id]), choice: (answer: SchaetzoramaAnswer | undefined) => ({ label: zoneLabel(answer?.kind === "assign" ? answer.assignments[term.id] : undefined), correct: answer?.kind === "assign" && answer.assignments[term.id] === solution.assignments[term.id] }) }))
      : [];
  if (!columns.length) return "";
  return `<div class="sz-comparison-wrap"><table class="sz-comparison"><caption>Tus respuestas</caption><thead><tr><th scope="col">Jugador</th>${columns.map((column) => `<th scope="col">${escape(column.heading)}</th>`).join("")}<th scope="col">Puntos</th></tr><tr class="sz-comparison-solution"><th scope="row">Solución</th>${columns.map((column) => `<td>${escape(column.correct)}</td>`).join("")}<td></td></tr></thead><tbody>${results.map((result, index) => `<tr style="--row-delay:${scoreStartMs(question) + index * playerStaggerMs}ms"><th scope="row">${escape(result.name)}${result.joker?.categoryId === category ? `<small>copiada</small>` : ""}</th>${columns.map((column) => {
    const selected = column.choice(result.answers[category]);
    return `<td class="${selected.correct ? "is-correct" : "is-wrong"}"><span aria-label="${selected.correct ? "Correcto" : "incorrecto"}">${selected.correct ? "✓" : "×"}</span> ${escape(selected.label)}</td>`;
  }).join("")}<td class="sz-comparison-points">+${result.categoryScores[category]}</td></tr>`).join("")}</tbody></table></div>`;
}
