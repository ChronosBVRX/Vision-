import assert from "node:assert/strict";
import { test } from "node:test";
import { arenaText } from "../dist/text.js";
import { magicArenaServerGame as game } from "../dist/server/MagicArenaServerGame.js";

const context = {
  roomCode: "TEST", roundNumber: 1, now: 1_000, deltaMs: 0,
  language: "en", theme: "light", roomSettings: {}, previousRound: null,
  selectedGame: game.manifest,
  players: ["Bombe", "Ada"].map((name, index) => ({ id: `p${index}`, name, color: "#2288ff", score: 0, isReady: false, connected: true }))
};

test("English snapshots localize cards, events and generated messages while preserving IDs and names", () => {
  const state = game.startRound(game.createInitialState(context), context);
  const snapshot = game.toPublicState(state, context);
  assert.equal(snapshot.players[0].name, "Bombe");
  assert.equal(snapshot.cardDefinitions.find(card => card.cardId === "fireball").name, "Fireball");
  assert.ok(snapshot.cardDefinitions.every(card => !/[äöüß]|ueber|erhaelt|naechst|Figur|Feldschaden/.test(card.description)));
  assert.equal(arenaText("Bombe wirkt Feuerball", "en"), "Bombe casts Fireball");
  assert.equal(arenaText("Feld 1,2 erhaelt 3 Schaden (Riss, Arkane Ueberladung).", "en"), "Tile 1,2 takes 3 damage (Rift, Arcane Overload).");
  assert.equal(arenaText("Rundenereignis: Flutwelle.", "en"), "Round event: Tidal Wave.");
  assert.equal(arenaText("Bombe ist bereit.", "de"), "Bombe ist bereit.");
});

test("localization preserves no-op planning tick identity", () => {
  const state = game.startRound(game.createInitialState(context), context);
  assert.equal(game.tick(state, 16, { ...context, now: 1_016 }), state);
});
