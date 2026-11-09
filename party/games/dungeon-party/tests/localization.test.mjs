import assert from "node:assert/strict";
import { test } from "node:test";
import { serverGame as game } from "../dist/server/index.js";
import { bossPhaseName } from "../dist/text.js";

const context = {
  roomCode: "TEST", roundNumber: 1, now: 1_000, deltaMs: 0,
  language: "en", theme: "light", roomSettings: {}, previousRound: null,
  selectedGame: game.manifest,
  players: ["Ada", "Bo", "Cy"].map((name, index) => ({ id: `p${index}`, name, color: "#2288ff", score: 0, isReady: false, connected: true }))
};

test("English rooms keep response instructions and failed-room outcomes in English", () => {
  let state = game.createInitialState(context);
  assert.equal(state.message, "The dungeon awaits.");
  state = game.startRound(state, context);
  state = { ...state, encounters: state.encounters.map((encounter, index) => index ? encounter : { ...encounter, difficulty: 999 }) };
  for (const player of context.players) state = game.handleInput(state, { type: "dungeon_action", playerId: player.id, action: "fight" }, context);
  state = game.tick(state, 16, context);
  assert.equal(state.message, "Actions revealed. You may now play response cards.");
  for (const player of context.players) state = game.handleInput(state, { type: "dungeon_card_response", playerId: player.id }, context);
  state = game.tick(state, 16, context);
  assert.equal(state.stage, "reveal");
  assert.match(state.message, /failed\.$/);
  for (const hero of state.heroes) assert.match(hero.lastOutcome, /Dungeon damage:/);
  assert.equal(bossPhaseName("en", 2), "The Final Clause");
});
