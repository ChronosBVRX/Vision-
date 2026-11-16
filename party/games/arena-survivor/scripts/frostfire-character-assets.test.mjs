import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { arenaSurvivorCharacterDefinitions } from '../dist/server/content/characterDefinitions.js';

// Identity baseline: each unarmed image was visually matched to its selection
// portrait. Update deliberately when replacing artwork, never by filename order.
const expected = JSON.parse(readFileSync(new URL('./fixtures/frostfire-character-sprites.json', import.meta.url)));

test('Frostfire run artwork retains the visually verified identity of every character', () => {
  assert.deepEqual(Object.keys(expected).sort(), arenaSurvivorCharacterDefinitions.map(character => character.id).sort());
  for (const [id, hash] of Object.entries(expected)) {
    for (const surface of ['host', 'controller']) {
      const path = new URL(`../public/${surface}/arena-survivor/themes/frostfire-saga/characters-unarmed/${id}.png`, import.meta.url);
      assert.equal(createHash('sha256').update(readFileSync(path)).digest('hex'), hash, `${surface}: ${id}`);
    }
  }
});
