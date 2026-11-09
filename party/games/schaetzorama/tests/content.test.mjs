import assert from "node:assert/strict";
import { test } from "node:test";
import { schaetzoramaRounds } from "../dist/server/schaetzoramaContent.js";

test("Cifras salvajes has a complete Spanish bank for 100 rounds", () => {
  assert.equal(schaetzoramaRounds.length, 100);
  const ids = new Set();
  const byCategory = { number: 0, percent: 0, rank: 0, assign: 0 };

  for (const round of schaetzoramaRounds) {
    assert(round.roundLabel.trim());
    for (const category of ["number", "percent", "rank", "assign"]) {
      const question = round.questions[category];
      assert(question, `${round.roundIndex}: ${category}`);
      assert.equal(question.categoryId, category);
      assert(question.prompt.trim());
      assert(question.source?.label?.trim());
      assert.equal(new URL(question.source.url).protocol, "https:");
      assert(!ids.has(question.id), `duplicate ${question.id}`);
      ids.add(question.id);
      byCategory[category] += 1;

      if (question.kind === "number" || question.kind === "percent") {
        assert(question.answer >= question.min && question.answer <= question.max);
      }

      if (question.kind === "rank") {
        const itemIds = question.items.map((item) => item.id);
        assert.equal(new Set(itemIds).size, itemIds.length);
        assert.deepEqual([...question.answerOrder].sort(), [...itemIds].sort());
      }

      if (question.kind === "assign") {
        const termIds = question.terms.map((term) => term.id);
        assert.equal(new Set(termIds).size, termIds.length);
        assert.deepEqual(Object.keys(question.answers).sort(), [...termIds].sort());
        assert(termIds.every((id) => ["left", "right", "both"].includes(question.answers[id])));
      }
    }
  }

  assert.deepEqual(byCategory, { number: 100, percent: 100, rank: 100, assign: 100 });
});
