import assert from 'node:assert/strict';
import { serverGame } from '../dist/server/index.js';
import { listDetectiveOptions } from '../dist/server/rules.js';
import { buildSchattenjagdControllerModel } from '../dist/controller/index.js';

const context = {
  roomCode: 'ORDER', roundNumber: 1, now: 1000, deltaMs: 0, language: 'de', theme: 'light',
  selectedGame: serverGame.manifest, previousRound: null,
  roomSettings: { 'schattenjagd.shadowMode': 'ai' },
  players: ['Ada', 'Ben', 'Cora'].map((name, i) => ({ id: name, name, color: ['#ef4444', '#38bdf8', '#f59e0b'][i], connected: true, isReady: true, score: 0 }))
};
let state = serverGame.startRound(serverGame.createInitialState(context), context);
while (state.stage === 'shadow_move') {
  context.now += 2000;
  state = serverGame.tick(state, 2000, context);
}
assert.equal(state.activeDetectivePlayerId, 'Ada');
assert.equal(serverGame.toControllerStateForPlayer(state, context, 'Ben').isMyTurn, false);
assert.deepEqual(serverGame.toControllerStateForPlayer(state, context, 'Ben').options, []);
const benOption = listDetectiveOptions(state, state.detectives[1]).find(o => !o.blocked);
assert.equal(serverGame.handleInput(state, { type: 'move', playerId: 'Ben', ...benOption }, context), state);
const start = state;
const option = listDetectiveOptions(state, state.detectives[0]).find(o => !o.blocked && o.stationId !== state.shadowStationId);
context.now += 1000;
state = serverGame.handleInput(state, { type: 'move', playerId: 'Ada', ...option }, context);
assert.equal(state.activeDetectivePlayerId, 'Ben');
assert.equal(state.turnEndsAt, context.now + state.turnDurationMs);
assert.equal(serverGame.handleInput(state, { type: 'move', playerId: 'Ada', ...option }, context), state);
context.now = state.turnEndsAt;
state = serverGame.tick(state, 0, context);
assert.equal(state.activeDetectivePlayerId, 'Cora');
assert.equal(state.detectives[1].stationId, start.detectives[1].stationId);
assert.equal(state.turnEndsAt, context.now + state.turnDurationMs);
context.players[2].connected = false;
state = serverGame.tick(state, 0, context);
assert.equal(state.stage, 'shadow_move');
assert.equal(state.turn, start.turn + 1);
context.players[0].connected = false;
const skipped = serverGame.tick({ ...start, turnDurationMs: null, turnEndsAt: null }, 0, context);
assert.equal(skipped.activeDetectivePlayerId, 'Ben');
assert.equal(skipped.turnEndsAt, null);
const publicState = serverGame.toPublicState(skipped, context);
assert.equal(publicState.shadow.currentStationId, null);
assert.ok(publicState.reachableOptions.every(o => !o.blocked));
assert.equal(serverGame.tick(skipped, 0, context), skipped, 'idle ticks must not rebroadcast');
const waitingModel = buildSchattenjagdControllerModel({ state: { room: { language: 'de', players: context.players }, player: context.players[0], game: { phase: 'playing', state: serverGame.toControllerStateForPlayer(skipped, context, 'Ada') } }, onInput() {} });
assert.equal(waitingModel.disabled, true);
assert.match(waitingModel.helperText, /Ben ist am Zug/);
const activeModel = buildSchattenjagdControllerModel({ state: { room: { language: 'de', players: context.players }, player: context.players[1], game: { phase: 'playing', state: serverGame.toControllerStateForPlayer(skipped, context, 'Ben') } }, onInput() {} });
assert.equal(activeModel.disabled, false);
assert.ok(activeModel.choices.some(choice => !choice.disabled));
const trapped = { ...start, detectives: start.detectives.map((entry, index) => index === 0 ? { ...entry, tickets: { taxi: 0, bus: 0, metro: 0 } } : entry) };
context.players.forEach(player => { player.connected = true; });
assert.equal(serverGame.tick(trapped, 0, { ...context, now: 1000 }).activeDetectivePlayerId, 'Ben');
const caught = serverGame.handleInput({ ...start, shadowStationId: option.stationId }, { type: 'move', playerId: 'Ada', ...option }, context);
assert.equal(caught.outcome, 'caught');
assert.equal(serverGame.toPublicState(caught, context).activeDetectivePlayerId, null);
console.log('PASS: order, unauthorized/repeated moves, individual timers, disconnects, unlimited turns, public secrecy, idle ticks');
