import test from 'node:test';
import assert from 'node:assert/strict';
import { arenaSurvivorServerGame as game } from '../dist/server/ArenaSurvivorServerGame.js';
import { resolveArmorDamage, resolveCriticalMultiplier, resolveWeaponCooldown } from '../dist/server/arenaSurvivorBalance.js';
import { resolveArenaSurvivorDifficulty, resolveArenaSurvivorSurvivalDifficulty } from '../dist/server/difficulty/arenaSurvivorDifficulty.js';
import { resolveArenaSurvivorPlayerStats, createArenaSurvivorStarterLoadout } from '../dist/server/loadout/arenaSurvivorLoadout.js';
import { arenaSurvivorWeaponDefinitions as weapons } from '../dist/server/content/weaponDefinitions.js';
import { createArenaSurvivorLevelUpShopState } from '../dist/server/progression/arenaSurvivorLevelBonuses.js';
import { applyLifeSteal } from '../dist/server/systems/lifeSteal.js';
import { applyMovementSystem } from '../dist/server/systems/movementSystem.js';
import { applyEnemyAiSystem } from '../dist/server/systems/enemyAiSystem.js';
import { applyProjectileSystem } from '../dist/server/systems/projectileSystem.js';
import { resolveCollisionSystem } from '../dist/server/systems/collisionSystem.js';
import { applyDamageSystem } from '../dist/server/systems/damageSystem.js';
import { createArenaSurvivorProjectile } from '../dist/server/factories/createProjectile.js';
import { createArenaSurvivorEnemy } from '../dist/server/factories/createEnemy.js';

function setup(mode = 'wave', count = 1) {
  const context = { now: 100000, language: 'de', roomSettings: { arenaSurvivorMode: mode },
    players: Array.from({length: count}, (_,i) => ({ id: `p${i}`, name: `P${i}`, color: '#fff', connected: true, isReady: true, score: 0 })) };
  const state = game.startRound(game.createInitialState(context), context);
  state.phase = 'playing'; state.elapsedMs = 1000;
  return state;
}
function shot(state, extra = {}) {
  return createArenaSurvivorProjectile({ originX: 100, originY: 100, angleRad: 0,
    ownerId: state.players[0].playerId, ownerKind: 'player', now: 0, speed: 1000, damage: 5,
    maxRange: 500, ...extra });
}
function enemy(state, x, hp = 100) {
  const result = createArenaSurvivorEnemy('slime-blob', {x,y:100}, state.players[0], 0);
  result.hp = hp; result.maxHp = hp;
  return result;
}

test('percentage builds add bonuses and are independent of modifier order', () => {
  const loadout = createArenaSurvivorStarterLoadout('rundling-allround');
  const base = resolveArenaSurvivorPlayerStats(loadout, 'rundling-allround');
  const bonuses = [{ damagePct: 50 }, { damagePct: 50 }, { damagePct: -20 }];
  const stats = resolveArenaSurvivorPlayerStats(loadout, 'rundling-allround', bonuses);
  assert.ok(Math.abs(stats.damageMultiplier - (base.damageMultiplier + 0.8)) < 1e-9);
  const reversed = resolveArenaSurvivorPlayerStats(loadout, 'rundling-allround', bonuses.toReversed());
  for (const key of Object.keys(stats)) assert.ok(Math.abs(stats[key] - reversed[key]) < 1e-9, key);
  assert.equal(resolveArenaSurvivorPlayerStats(loadout, 'rundling-allround', [{dodgePct:200}]).dodgePct, 60);
});

test('armor gives predictable effective HP and crit is not multiplied twice', () => {
  assert.equal(resolveArmorDamage(30, 0), 30);
  assert.equal(resolveArmorDamage(30, 15), 15);
  assert.equal(resolveArmorDamage(30, -15), 60);
  assert.equal(resolveCriticalMultiplier(2, 1.5), 2);
  assert.equal(resolveCriticalMultiplier(2, 1.8), 2.3);
  assert.ok(resolveWeaponCooldown(100, 100, false) >= 1000/12);
  assert.ok(resolveWeaponCooldown(100, 100, true) >= 180);
});

test('lifesteal heals one HP per proc and shares a 100 ms cap between weapons', () => {
  let player = setup().players[0]; player.hp = 10; player.stats.lifeStealPct = 100;
  let seed = 1;
  for (let time = 0; time < 1000; time += 10) {
    const result = applyLifeSteal(player, time, seed); player = result.player; seed = result.seed;
  }
  assert.equal(player.hp, 20);
  player.stats.lifeStealPct = 0;
  assert.equal(applyLifeSteal(player, 2000, seed).player.hp, 20);
});

test('weapon tiers improve expected single-target DPS while preserving roles', () => {
  for (const weapon of weapons) {
    let previous = 0;
    for (const level of weapon.levels) {
      const dps = level.damage * (level.projectileCount ?? 1) * 1000 / level.cooldownMs;
      assert.ok(dps >= previous, `${weapon.id}: tier ${level.level} regresses`);
      assert.ok(Number.isFinite(dps) && dps > 0 && dps <= 150, `${weapon.id}: exceeds tier budget`);
      previous = dps;
    }
  }
  const pistol = weapons.find(w => w.id === 'survivor-pistol');
  assert.equal(pistol.levels[0].pierce, 0);
  assert.ok(weapons.find(w => w.id === 'hunter-bow').levels[0].range > pistol.levels[0].range);
});

test('level-up tiers scale the applied bonus and displayed value together', () => {
  const player = setup().players[0];
  const low = createArenaSurvivorLevelUpShopState({...player,level:1}, 123, 'en', true).shop.offers;
  const high = createArenaSurvivorLevelUpShopState({...player,level:16}, 123, 'en', true).shop.offers;
  for (let i = 0; i < low.length; i++) {
    assert.equal(low[i].levelBonusId, high[i].levelBonusId);
    const [key,value] = Object.entries(low[i].levelBonusModifiers)[0];
    assert.equal(high[i].levelBonusModifiers[key], value * 4);
    const displayed = Number(high[i].description.match(/[\d.]+/)[0]);
    assert.ok(Math.abs(displayed - value * 4) < 1e-9);
  }
});

test('all five difficulties and party sizes have monotonic, bounded pressure', () => {
  for (const count of [1,2,4]) for (const tier of [1,2,3,4,5]) {
    let old = resolveArenaSurvivorDifficulty(1,count,tier);
    for (const wave of [5,10,20,40]) {
      const next = resolveArenaSurvivorDifficulty(wave,count,tier);
      assert.ok(next.enemyHpMultiplier >= old.enemyHpMultiplier);
      assert.ok(next.enemyDamageMultiplier >= old.enemyDamageMultiplier);
      assert.ok(next.enemySpeedMultiplier <= resolveArenaSurvivorDifficulty(1,count,tier).enemySpeedMultiplier*1.18+1e-9);
      old = next;
    }
    for (const minutes of [0,5,10,20,40]) {
      const next = resolveArenaSurvivorSurvivalDifficulty(minutes*60000,count,tier);
      assert.ok(next.maxEnemiesOnScreen <= 160 && next.enemySpawnIntervalMs >= 200);
    }
  }
  assert.ok(resolveArenaSurvivorDifficulty(1,4,3).playerCountMultiplier < 2.5);
});

test('analog movement has equal cardinal/diagonal speed, immediate stop and no zero-tick drift', () => {
  const state = setup(); const p = state.players[0]; Object.assign(p,{x:400,y:300,moveInputX:1,moveInputY:1});
  const next = applyMovementSystem(state,100).players[0];
  assert.ok(Math.abs(Math.hypot(next.x-p.x,next.y-p.y)-p.moveSpeed*0.1)<1e-9);
  assert.equal(applyMovementSystem(state,0),state);
  const stop = applyMovementSystem({...state,players:[{...next,moveInputX:0,moveInputY:0}]},100).players[0];
  assert.equal(stop.x,next.x); assert.equal(stop.y,next.y); assert.equal(stop.vx,0);
});

test('Survival tether does not depend on player array order', () => {
  const state = setup('survival',2);
  const a = state.players[0], b = state.players[1];
  Object.assign(a,{x:2400,y:1800,moveInputX:0.2,moveInputY:1});
  Object.assign(b,{x:2480,y:1800,moveInputX:-0.3,moveInputY:1});
  const normal = applyMovementSystem(state,16).players;
  const reversed = applyMovementSystem({...state,players:[b,a]},16).players.toReversed();
  normal.forEach((player,i) => { assert.equal(player.x,reversed[i].x); assert.equal(player.y,reversed[i].y); });
});

test('overlapping enemies separate without exceeding their movement speed', () => {
  const state = setup(); state.enemies = [enemy(state,200),enemy(state,200)];
  const next = applyEnemyAiSystem(state,100);
  assert.ok(Math.hypot(next.enemies[0].x-next.enemies[1].x,next.enemies[0].y-next.enemies[1].y)>0);
  for(let i=0;i<2;i++)assert.ok(Math.hypot(next.enemies[i].x-state.enemies[i].x,next.enemies[i].y-state.enemies[i].y)<=state.enemies[i].moveSpeed*0.1+1e-9);
});

for (const mode of ['wave','survival']) {
  test(`${mode}: swept projectiles hit in travel order and never hit the same enemy twice`, () => {
    let state = setup(mode);
    const near = enemy(state,130), far = enemy(state,170);
    state.enemies = [far,near]; state.projectiles = [shot(state,{pierce:3})];
    state = applyProjectileSystem(state,100);
    let report = resolveCollisionSystem(state);
    assert.deepEqual(report.projectileHits.map(hit=>hit.enemyId),[near.id,far.id]);
    state = applyDamageSystem(state,report,1000);
    state.enemies.forEach(enemy=>assert.equal(enemy.hp,95));
    // Move back through both targets: a projectile may damage each target once.
    state.projectiles[0].vx = -1000;
    state = applyProjectileSystem(state,100);
    assert.equal(resolveCollisionSystem(state).projectileHits.length,0);
  });
  test(`${mode}: a non-piercing shot damages only the first contact`, () => {
    let state = setup(mode); const near = enemy(state,130), far = enemy(state,170);
    state.enemies=[far,near]; state.projectiles=[shot(state,{pierce:1})];
    state=applyProjectileSystem(state,100); state=applyDamageSystem(state,resolveCollisionSystem(state),1000);
    assert.equal(state.enemies.find(e=>e.id===near.id).hp,95);
    assert.equal(state.enemies.find(e=>e.id===far.id).hp,100);
    assert.equal(state.projectiles.length,0);
  });
}

test('a projectile resolves its final range segment without hitting beyond range', () => {
  let state=setup(); const near=enemy(state,140),far=enemy(state,200);
  state.enemies=[far,near]; state.projectiles=[shot(state,{maxRange:50,pierce:5})];
  state=applyProjectileSystem(state,100);
  assert.equal(state.projectiles[0].x,150);
  state=applyDamageSystem(state,resolveCollisionSystem(state),1000);
  assert.equal(state.enemies.find(e=>e.id===near.id).hp,95);
  assert.equal(state.enemies.find(e=>e.id===far.id).hp,100);
  assert.equal(state.projectiles.length,0);
});
