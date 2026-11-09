import assert from 'node:assert/strict';
import { test } from 'node:test';
import { drawingWords, drawingWordPool } from '../dist/server/words.js';
import { serverGame } from '../dist/server/index.js';
import { manifest } from '../dist/manifest.js';

function context(language, category) {
  return {
    roomCode: 'LANG', roundNumber: 1, now: 1000, deltaMs: 0,
    language, theme: 'light', selectedGame: manifest, previousRound: null,
    roomSettings: { zeichnenUndErratenWordCategory: category },
    players: ['a', 'b'].map(id => ({ id, name: id.toUpperCase(), score: 0, color: '#000000', isReady: false, connected: true }))
  };
}

test('every drawing prompt has an authored German and English pair and preserves age categories', () => {
  assert.equal(drawingWords.length, 568);
  for (const entry of drawingWords) assert(entry.de.trim() && entry.en.trim());
  for (const language of ['de', 'en']) {
    assert.equal(drawingWordPool(language, 'standard').length, 289);
    assert.equal(drawingWordPool(language, 'adult').length, 279);
    assert.equal(drawingWordPool(language, 'all').length, 568);
  }
  assert(drawingWordPool('en', 'standard').includes('Bicycle'));
  assert(!drawingWordPool('en', 'standard').includes('Fahrrad'));
  assert(drawingWordPool('en', 'adult').includes('Condom'));
  assert(!drawingWordPool('en', 'standard').includes('Condom'));
});

test('English selection reaches server secrets, validation and score descriptions without exposing the secret early', () => {
  for (const category of ['standard', 'adult', 'all']) {
    const ctx = context('en', category);
    const playing = serverGame.startRound(serverGame.createInitialState(ctx), ctx);
    assert(drawingWordPool('en', category).includes(playing.secretWord));
    assert.equal(serverGame.toPublicState(playing, ctx).revealedWord, undefined);
    const guesser = ctx.players.find(player => player.id !== playing.drawerPlayerId).id;
    const solved = serverGame.handleInput(playing, { type: 'guess:submit', playerId: guesser, guess: playing.secretWord, sentAt: ctx.now }, ctx);
    assert.equal(solved.phase, 'locked');
    assert.deepEqual(serverGame.buildScore(solved, ctx).map(entry => entry.reason), ['Correct word guess', 'Word successfully drawn']);
  }
});
