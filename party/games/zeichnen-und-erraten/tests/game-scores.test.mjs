import assert from 'node:assert/strict';
import { test } from 'node:test';
import { serverGame } from '../dist/server/index.js';
import { manifest } from '../dist/manifest.js';

const context = (previousRound = null) => ({
  roomCode: 'TEST', roundNumber: previousRound ? 2 : 1, now: 1000, deltaMs: 0,
  language: 'de', theme: 'light', selectedGame: manifest, previousRound, roomSettings: {},
  players: ['a', 'b', 'c'].map(id => ({ id, name: id.toUpperCase(), score: 900, color: '#000000', isReady: false, connected: true }))
});
function win(ctx = context()) {
  const initial = serverGame.createInitialState(ctx);
  const playing = serverGame.startRound(initial, ctx);
  const guesser = ctx.players.find(player => player.id !== playing.drawerPlayerId).id;
  const input = { type: 'guess:submit', playerId: guesser, guess: playing.secretWord, sentAt: ctx.now };
  return { state: serverGame.handleInput(playing, input, ctx), input, ctx };
}

test('local totals ignore global room scores and credit drawer and guesser immediately once', () => {
  const { state, input, ctx } = win();
  assert.equal(state.phase, 'locked');
  assert.deepEqual(state.gameScores.map(entry => entry.total).sort(), [0, 1, 1]);
  assert.equal(serverGame.handleInput(state, input, ctx), state);
  assert.deepEqual(serverGame.buildScore(state, ctx).map(entry => entry.delta), [1, 1]);
  assert.equal(serverGame.toPublicState(state, ctx).revealedWord, state.secretWord);
  assert.deepEqual(serverGame.toPublicState(state, ctx).gameScores, state.gameScores);
});

test('consecutive rounds carry only game totals and reset round gains', () => {
  const { state } = win();
  const ctx = context({ gameId: manifest.id, phase: 'finished', roundNumber: 1, state: { ...state, phase: 'finished' }, updatedAt: 1000 });
  ctx.players[0].name = 'Renamed';
  ctx.players.push({ ...ctx.players[0], id: 'new', name: 'New' });
  const next = serverGame.createInitialState(ctx);
  assert.deepEqual(next.gameScores.slice(0, 3).map(entry => entry.total), state.gameScores.map(entry => entry.total));
  assert.ok(next.gameScores.every(entry => entry.delta === 0));
  assert.equal(next.gameScores[0].playerName, 'Renamed');
  assert.equal(next.gameScores.at(-1).total, 0);
});

test('new session or previous different game starts at zero', () => {
  const { state } = win();
  for (const previous of [null, { gameId: 'other-game', phase: 'finished', state }]) {
    assert.ok(serverGame.createInitialState(context(previous)).gameScores.every(entry => entry.total === 0));
  }
});

test('timeout reveals the word without awarding points', () => {
  const ctx = context();
  const initial = serverGame.createInitialState(ctx);
  const playing = serverGame.startRound(initial, ctx);
  assert.equal(serverGame.toPublicState(playing, ctx).revealedWord, undefined);
  const expired = serverGame.tick(playing, 0, { ...ctx, now: playing.finishAt });
  assert.equal(expired.phase, 'locked');
  assert.equal(serverGame.toPublicState(expired, ctx).revealedWord, playing.secretWord);
  assert.ok(expired.gameScores.every(entry => entry.total === 0 && entry.delta === 0));
  assert.deepEqual(serverGame.buildScore(expired, ctx), []);
});

test('late joiners appear at zero even when nobody guessed the word', () => {
  const ctx = context();
  const playing = serverGame.startRound(serverGame.createInitialState(ctx), ctx);
  ctx.players.push({ ...ctx.players[0], id: 'late', name: 'Late joiner', score: 1500 });
  const expired = serverGame.tick(playing, 0, { ...ctx, now: playing.finishAt });
  assert.deepEqual(serverGame.toPublicState(expired, ctx).gameScores.at(-1), {
    playerId: 'late', playerName: 'Late joiner', total: 0, delta: 0
  });
});

test('drawing echoes a bounded client correlation ID while the server owns the stroke', () => {
  const ctx = context();
  const playing = serverGame.startRound(serverGame.createInitialState(ctx), ctx);
  const input = { type: 'draw:start', playerId: playing.drawerPlayerId, x: 0.2, y: 0.3, clientStrokeId: 'local-preview-123', sentAt: ctx.now };
  const drawn = serverGame.handleInput(playing, input, ctx);
  assert.equal(drawn.strokes[0].clientStrokeId, input.clientStrokeId);
  assert.notEqual(drawn.strokes[0].id, input.clientStrokeId);
  assert.equal(serverGame.toControllerStateForPlayer(drawn, ctx, input.playerId).strokes[0].clientStrokeId, input.clientStrokeId);
  const outsider = ctx.players.find(player => player.id !== input.playerId).id;
  assert.equal(serverGame.handleInput(playing, { ...input, playerId: outsider }, ctx), playing);
  const invalid = serverGame.handleInput(playing, { ...input, clientStrokeId: 'x'.repeat(81) }, ctx);
  assert.equal(invalid.strokes[0].clientStrokeId, undefined);
});
