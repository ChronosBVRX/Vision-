import assert from "node:assert/strict";
import { test } from "node:test";
import { buildSchaetzoramaControllerModel } from "../dist/controller/index.js";
import { serverGame } from "../dist/server/index.js";
import { answersVisibleMs, autoContinueDelayMs } from "../dist/revealTiming.js";

const players = ["a", "b", "c"].map((id) => ({ id, name: id, color: "#336699", score: 0, isReady: false, connected: true }));
const ctx = (now = 1000) => ({ roomCode: "TEST", roundNumber: 1, players, now, deltaMs: 0, language: "de", theme: "light", selectedGame: serverGame.manifest, previousRound: null, roomSettings: {} });
const input = (state, playerId, type, fields = {}, now = 1000) => serverGame.handleInput(state, { playerId, type, ...fields }, ctx(now));
const tick = (state, now) => serverGame.tick(state, 100, ctx(now));
function copyStage() {
  let state = serverGame.startRound(serverGame.createInitialState(ctx()), ctx());
  for (const player of players) state = input(state, player.id, "submit_answers", { answers: {} });
  return state;
}
function revealed() {
  let state = copyStage();
  for (const player of players) state = input(state, player.id, "choose_joker", { joker: null });
  return state;
}

test("copy phase stays open indefinitely and waits for every decision", () => {
  let state = copyStage();
  assert.equal(state.jokerEndsAt, null);
  assert.equal(tick(state, 1_000_000), state);
  state = input(state, "a", "choose_joker", { joker: null });
  state = input(state, "b", "choose_joker", { joker: null });
  assert.equal(tick(state, 1_000_000), state);
  assert.equal(input(state, "c", "choose_joker", { joker: null }).stage, "revealed");
});

test("all manual confirmations are required; early and stale inputs cannot advance", () => {
  let state = revealed();
  assert.equal(state.revealReadyAt, 1000 + answersVisibleMs(state.roundContent.questions.number, 3));
  assert.equal(input(state, "a", "reveal_ready", { step: 0 }), state);
  const now = state.revealReadyAt;
  state = tick(state, now);
  assert.equal(state.revealAnswersVisible, true);
  state = input(state, "a", "reveal_ready", { step: 0 }, now);
  state = input(state, "b", "reveal_ready", { step: 0 }, now);
  assert.equal(tick(state, now + 100_000).revealStep, 0);
  state = input(state, "c", "reveal_ready", { step: 0 }, now);
  assert.equal(tick(state, now + autoContinueDelayMs - 1).revealStep, 0);
  state = tick(state, now + autoContinueDelayMs);
  assert.equal(state.revealStep, 1);
  assert.deepEqual(state.revealReadyByPlayerId, {});
  assert.equal(input(state, "a", "reveal_ready", { step: 0 }, now + 100_000), state);
  assert.equal(serverGame.isRoundFinished(state), false);
});

test("mixed automatic/manual readiness waits only for manual players and can be withdrawn", () => {
  let state = revealed();
  state = input(state, "a", "set_auto_continue", { enabled: true });
  state = input(state, "b", "set_auto_continue", { enabled: true });
  const now = state.revealReadyAt;
  state = tick(state, now);
  assert.equal(state.revealAdvanceAt, null);
  state = input(state, "c", "reveal_ready", { step: 0 }, now);
  assert.equal(state.revealAdvanceAt, now + autoContinueDelayMs);
  state = input(state, "a", "set_auto_continue", { enabled: false }, now + 1);
  assert.equal(state.revealAdvanceAt, null);
  state = input(state, "a", "set_auto_continue", { enabled: true }, now + 2);
  state = tick(state, state.revealAdvanceAt);
  assert.equal(state.revealStep, 1);
  assert.equal(state.autoContinueByPlayerId.a, true);
  assert.deepEqual(state.revealReadyByPlayerId, {});
});

test("all automatic players progress after the final answer, preferences survive rounds", () => {
  let state = revealed();
  for (const player of players) state = input(state, player.id, "set_auto_continue", { enabled: true });
  for (let step = 0; step < 6; step++) {
    assert.equal(state.revealStep, step);
    assert.equal(serverGame.isRoundFinished(state), false);
    state = tick(state, state.revealStepStartedAt);
    state = tick(state, state.revealAdvanceAt);
  }
  assert.equal(serverGame.isRoundFinished(state), true);
  const next = serverGame.createInitialState({ ...ctx(), previousRound: { gameId: "schaetzorama", state, roundNumber: 1, phase: "finished", updatedAt: 1000 } });
  assert.equal(next.autoContinueByPlayerId.a, true);
  assert.deepEqual(next.revealReadyByPlayerId, {});
});


test("automatic readiness uses an explicit true and is unavailable after round ten", () => {
  let state = revealed();
  state = input(state, "a", "set_auto_continue", { enabled: true });
  state = { ...state, phase: "finished", revealStep: 6 };
  const readyCalls = [];
  const modelContext = { state: { room: { language: "de", selectedGameId: "schaetzorama", availableGames: [serverGame.manifest], players }, player: players[0], game: { phase: "finished", roundNumber: 1, state: serverGame.toControllerStateForPlayer(state, ctx(), "a") } }, onInput() {}, onSetReady: (ready) => readyCalls.push(ready) };
  const model = buildSchaetzoramaControllerModel(modelContext);
  assert(model.ready);
  assert.equal(model.autoContinue, true);
  model.onAutoReady();
  assert.deepEqual(readyCalls, [true]);
  modelContext.state.game.state.roundContent.roundIndex = 10;
  assert.equal(buildSchaetzoramaControllerModel(modelContext).ready, undefined);
});
