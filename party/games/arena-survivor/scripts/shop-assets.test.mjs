import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { arenaSurvivorWeaponDefinitions } from '../dist/server/content/weaponDefinitions.js';
import { arenaSurvivorItemDefinitions } from '../dist/server/content/itemDefinitions.js';
import { createArenaSurvivorLevelUpShopState } from '../dist/server/progression/arenaSurvivorLevelBonuses.js';
import { resolveArenaSurvivorLevelBonusIconPath, resolveArenaSurvivorShopIconPath } from '../dist/visualThemes.js';

const bonusIds = new Set();
// Exercise the actual offer generator, including Wave-only harvesting bonuses.
for (let seed = 1; seed <= 256; seed++) {
  for (const offer of createArenaSurvivorLevelUpShopState({level:1,pendingLevelUpChoices:1}, seed, 'de').shop.offers) {
    bonusIds.add(offer.levelBonusId);
  }
}

for (const theme of ['classic','obsidian-relay','frostfire-saga','marshmallow-mayhem']) {
  test(`${theme}: every shop and level-up icon exists on both development and hosted asset roots`, () => {
    const paths = [
      ...[...bonusIds].map(id => resolveArenaSurvivorLevelBonusIconPath(id,theme)),
      ...arenaSurvivorWeaponDefinitions.map(weapon => resolveArenaSurvivorShopIconPath('weapon',weapon.id,theme)),
      ...arenaSurvivorItemDefinitions.map(item => resolveArenaSurvivorShopIconPath('item',item.id,theme))
    ];
    for (const path of paths) {
      assert.equal(typeof path, 'string', `${theme}: missing icon mapping`);
      assert.ok(path.startsWith('/arena-survivor/'));
      for (const surface of ['host','controller']) {
        // Hosted /controller/ pages request absolute icon URLs from /, which
        // the HTTP server serves from the host root. Dev uses controller root.
        const url = new URL(`../public/${surface}${path}`,import.meta.url);
        const bytes = readFileSync(url);
        if (path.endsWith('.png')) {
          assert.equal(bytes.subarray(0,8).toString('hex'),'89504e470d0a1a0a',`${surface}${path}`);
          assert.ok(bytes.readUInt32BE(16)>0 && bytes.readUInt32BE(20)>0,`${surface}${path}: invalid dimensions`);
        } else {
          assert.match(bytes.toString('utf8'),/<svg\b/,`${surface}${path}`);
        }
      }
    }
  });
}
