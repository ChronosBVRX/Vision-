import { test } from "node:test";
import assert from "node:assert/strict";
import { numericComparison, renderAnswerComparison } from "../dist/host/schaetzoramaAnswerComparison.js";
const escape = (value) => String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll('"', "&quot;");
const result = (category, answer) => ({ name: "Alex <script>", answers: { [category]: answer }, categoryScores: { [category]: 50 }, joker: { categoryId: category } });
test("percentage deviations use percentage points and retain the submitted estimate", () => {
  const html = numericComparison({ kind: "percent" }, { kind: "number", value: 25 }, { kind: "number", value: 30 }, "de", escape);
  assert.match(html, /25 %/);
  assert.match(html, /5 puntos porcentuales demasiado bajo/);
  assert.match(numericComparison({ kind: "percent" }, { kind: "number", value: 31 }, { kind: "number", value: 30 }, "de", escape), /1 punto porcentual demasiado alto/);
  assert.match(numericComparison({ kind: "number" }, undefined, { kind: "number", value: 30 }, "en", escape), /Sin respuesta/);
});
test("ranking retains each selected position and marks only matching positions correct", () => {
  const question = { kind: "rank", categoryId: "rank", items: [{ id: "a", label: "Alpha" }, { id: "b", label: "Beta" }, { id: "c", label: "Gamma" }] };
  const html = renderAnswerComparison(question, { kind: "rank", order: ["a", "b", "c"] }, [result("rank", { kind: "rank", order: ["b", "a", "c"] })], "de", escape);
  assert.equal((html.match(/class="is-wrong"/g) ?? []).length, 2);
  assert.equal((html.match(/class="is-correct"/g) ?? []).length, 1);
  assert.match(html, /Alex &lt;script>/);
  assert.match(html, /copiada/);
});
test("assignment shows every selected zone, the solution, and missing answers", () => {
  const question = { kind: "assign", categoryId: "assign", leftLabel: "Izquierda", rightLabel: "Arriba", terms: [{ id: "a", label: "Alpha" }, { id: "b", label: "Beta" }, { id: "c", label: "Gamma" }] };
  const html = renderAnswerComparison(question, { kind: "assign", assignments: { a: "left", b: "both", c: "right" } }, [result("assign", { kind: "assign", assignments: { a: "right", b: "both" } })], "de", escape);
  assert.match(html, /Arriba →/);
  assert.match(html, /∩ ambos/);
  assert.match(html, /Sin respuesta/);
  assert.equal((html.match(/class="is-wrong"/g) ?? []).length, 2);
});
