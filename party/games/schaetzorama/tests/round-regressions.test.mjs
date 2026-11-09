import assert from "node:assert/strict";
import { test } from "node:test";
import { serverGame } from "../dist/server/index.js";
import { schaetzoramaRounds } from "../dist/server/schaetzoramaContent.js";
import { scoreSchaetzoramaAnswer } from "../dist/server/schaetzoramaScoring.js";
import { revealPosition, standingMovements } from "../dist/host/schaetzoramaReveal.js";

const players = ["a", "b"].map((id) => ({ id, name: id, color: "#336699", score: 0, isReady: false, connected: true }));
const context = { roomCode: "TEST", roundNumber: 1, players, now: 1000, deltaMs: 0, language: "de", theme: "light", selectedGame: serverGame.manifest, previousRound: null, roomSettings: {} };

test("zero is accepted for estimates that start at zero", () => {
  const original = schaetzoramaRounds.find((round) => round.questions.number.id === "mexico-oros-olimpicos").questions.number;
  let state = serverGame.startRound(serverGame.createInitialState(context), context);
  state = { ...state, roundContent: { ...state.roundContent, questions: { ...state.roundContent.questions, number: original } } };
  assert.equal(state.roundContent.questions.number.min, 0);
  const phone = serverGame.toControllerStateForPlayer(state, context, "a");
  assert.equal(phone.roundContent.questions.number.min, 0);
  assert(!("answer" in phone.roundContent.questions.number));
  state = serverGame.handleInput(state, { type: "submit_answers", playerId: "a", answers: { number: { kind: "number", value: 0 } } }, context);
  assert.equal(serverGame.toControllerStateForPlayer(state, context, "a").ownAnswers.number.value, 0);
  assert.equal(scoreSchaetzoramaAnswer(original, { kind: "number", value: 0 }), 0);
});

test("ten-round sessions shuffle once, preserve solutions, and never repeat questions", () => {
  for (let session = 0; session < 30; session++) {
    let previousRound = null;
    const seen = new Set();
    for (let roundNumber = 1; roundNumber <= 10; roundNumber++) {
      const state = serverGame.createInitialState({ ...context, roundNumber, previousRound });
      const questions = state.roundContent.questions;
      const publicQuestions = serverGame.toPublicState(state, context).roundContent.questions;
      assert.equal(publicQuestions.number.min, 0);
      assert.equal(publicQuestions.percent.min, 0);
      assert.match(questions.assign.id, /^[a-z0-9-]+$/);
      for (const question of Object.values(questions)) {
        assert(!seen.has(question.id));
        seen.add(question.id);
      }
      assert.notDeepEqual(questions.rank.items.map((item) => item.id), questions.rank.answerOrder);
      assert.deepEqual(questions.rank.items.map((item) => item.id).sort(), [...questions.rank.answerOrder].sort());
      const zones = questions.assign.terms.map((term) => ({ left: 0, both: 1, right: 2 })[questions.assign.answers[term.id]]);
      if (new Set(zones).size > 1) assert(zones.some((zone, index) => index > 0 && zone < zones[index - 1]));
      assert.deepEqual(serverGame.toPublicState(state, context).roundContent, serverGame.toPublicState(state, context).roundContent);
      previousRound = { gameId: "schaetzorama", roundNumber, phase: "finished", state, updatedAt: context.now };
    }
    assert.equal(seen.size, 40);
  }
});

test("the second paid preview is readable at zero inventory and remains copyable", () => {
  let previousRound = null;
  for (let roundNumber = 1; roundNumber <= 2; roundNumber++) {
    const ctx = { ...context, roundNumber, previousRound };
    let state = serverGame.startRound(serverGame.createInitialState(ctx), ctx);
    const numberValue = 0;
    assert.equal(numberValue, 0);
    for (const player of players) state = serverGame.handleInput(state, { type: "submit_answers", playerId: player.id, answers: { number: { kind: "number", value: numberValue } } }, ctx);
    assert.equal(state.stage, "joker");
    const joker = { kind: "copy", categoryId: "number", targetPlayerId: "b" };
    state = serverGame.handleInput(state, { type: "preview_joker", playerId: "a", joker }, ctx);
    const phone = serverGame.toControllerStateForPlayer(state, ctx, "a");
    assert.equal(phone.ownInventory.copy, 2 - roundNumber);
    assert.deepEqual(phone.copyTargets.find((player) => player.playerId === "b").answers.number, { kind: "number", value: numberValue });
    assert.deepEqual(phone.ownJokerPreview, joker);
    assert.equal(serverGame.handleInput(state, { type: "preview_joker", playerId: "a", joker }, ctx), state);
    state = serverGame.handleInput(state, { type: "choose_joker", playerId: "a", joker }, ctx);
    state = serverGame.handleInput(state, { type: "choose_joker", playerId: "b", joker: null }, ctx);
    assert.deepEqual(state.jokerByPlayerId.a, joker);
    assert.equal(state.stage, "revealed");
    const revealedPhone = serverGame.toControllerStateForPlayer(state, ctx, "a");
    assert.deepEqual(revealedPhone.results, []);
    assert.deepEqual(revealedPhone.solutions, {});
    assert.deepEqual(revealedPhone.standings, []);
    assert.equal(serverGame.toPublicState(state, ctx).results.length, 2);
    previousRound = { gameId: "schaetzorama", roundNumber, phase: "finished", state, updatedAt: ctx.now };
  }
});

test("standings calculate movement from pre-round scores with shared tie ranks", () => {
  const standings = [
    { playerId: "a", name: "A", color: "#336699", score: 100, projectedScore: 110, roundScore: 10 },
    { playerId: "b", name: "B", color: "#336699", score: 50, projectedScore: 150, roundScore: 100 },
    { playerId: "c", name: "C", color: "#336699", score: 50, projectedScore: 110, roundScore: 60 }
  ];
  const movements = standingMovements(standings);
  assert.deepEqual(movements.map(({ playerId, previousRank, rank, shift }) => [playerId, previousRank, rank, shift]), [["b", 2, 1, 1], ["a", 1, 2, -1], ["c", 2, 2, 0]]);
  assert.deepEqual(movements.map(({ playerId, previousScore, previousRank, rank, shift }) => [playerId, previousScore, previousRank, rank, shift]), [["b", 50, 2, 1, 1], ["a", 100, 1, 2, -1], ["c", 50, 2, 2, 0]]);
  const state = serverGame.toPublicState(serverGame.createInitialState(context), context);
  state.revealStep = 4;
  state.revealElapsedMs = 1200;
  assert.deepEqual(revealPosition(state), { step: 4, elapsed: 1200, remaining: 0 });
});
