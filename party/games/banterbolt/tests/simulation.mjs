import assert from "node:assert/strict";
import { serverGame } from "../dist/server/index.js";
import { banterboltManifest } from "../dist/manifest.js";

const now = 1_000_000;
const players = ["ana", "beto", "carla", "dani"].map((id) => ({
  id,
  name: id[0].toUpperCase() + id.slice(1),
  avatar: null,
  color: "#2D7DF6",
  score: 0,
  isReady: true,
  connected: true,
  setupSelections: {},
}));

const context = {
  roomCode: "TEST",
  roundNumber: 1,
  players,
  now,
  deltaMs: 0,
  language: "es",
  theme: "dark",
  selectedGame: banterboltManifest,
  previousRound: null,
  roomSettings: {
    banterboltPack: "mexico",
    banterboltRounds: 2,
    banterboltWriteSeconds: 30,
    banterboltVoteSeconds: 10,
    banterboltLightning: "on",
  },
};

let state = serverGame.startRound(serverGame.createInitialState(context), context);
assert.equal(state.stage, "write");

for (const player of players) {
  const prompts = state.promptsByPlayer[player.id];
  state = serverGame.handleInput(state, {
    playerId: player.id,
    roundId: state.roundId,
    type: "submit_answers",
    values: prompts.map((prompt) => `respuesta de ${player.id} ${prompt.index}`),
    submit: true,
    sentAt: now,
  }, context);
}
assert.equal(state.stage, "vote");

let guard = 0;
while (state.stage !== "finished" && guard++ < 100) {
  if (state.stage === "vote") {
    const matchup = state.matchups[state.matchupIndex];
    for (const voter of matchup.voters) {
      if (matchup.votes[voter] !== undefined) continue;
      state = serverGame.handleInput(state, {
        playerId: voter,
        roundId: state.roundId,
        type: "vote",
        answerIndex: guard % 2,
        sentAt: now,
      }, context);
    }
  } else if (state.stage === "finale_write") {
    for (const player of players) {
      state = serverGame.handleInput(state, {
        playerId: player.id,
        roundId: state.roundId,
        type: "finale_submit",
        text: `finale de ${player.id}`,
        sentAt: now,
      }, context);
    }
  } else if (state.stage === "finale_vote") {
    for (const player of players) {
      const target = players.find((entry) => entry.id !== player.id).id;
      state = serverGame.handleInput(state, {
        playerId: player.id,
        roundId: state.roundId,
        type: "finale_vote",
        targetPlayerId: target,
        sentAt: now,
      }, context);
    }
  } else {
    context.now += 10_000;
    state = serverGame.tick(state, 0, context);
  }
}

assert.equal(state.stage, "finished");
assert.ok(Object.values(state.totals).some((points) => points > 0));
assert.equal(serverGame.buildScore(state).length, players.length);
console.log("Banterbolt simulation OK", state.totals);
