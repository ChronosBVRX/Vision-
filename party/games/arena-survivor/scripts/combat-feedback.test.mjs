import test from 'node:test';
import assert from 'node:assert/strict';
import { arenaSurvivorServerGame as game } from '../dist/server/ArenaSurvivorServerGame.js';
import { applyDamageSystem } from '../dist/server/systems/damageSystem.js';
import { applyAutoFireSystem } from '../dist/server/systems/autoFireSystem.js';
import { collectDamageEvents, recordDamageEvent } from '../dist/server/systems/damageEvents.js';
import { createArenaSurvivorEnemy } from '../dist/server/factories/createEnemy.js';
import { createArenaSurvivorProjectile } from '../dist/server/factories/createProjectile.js';
import { createLoadoutWeaponState, createArenaSurvivorWeaponRuntimeStates } from '../dist/server/loadout/arenaSurvivorLoadout.js';
for (const mode of ['wave', 'survival']) {
  function setup() {
    const context = { now: 100000, language: 'de', roomSettings: { arenaSurvivorMode: mode }, players: [{id:'p',name:'P',color:'#fff',connected:true,isReady:true,score:0}] };
    const state = game.startRound(game.createInitialState(context), context);
    state.phase='playing'; state.elapsedMs=100;
    return state;
  }
  test(`${mode}: projectile lethal hit retains actual damage and position after removal`, () => {
    const state=setup(), player=state.players[0];
    const enemy=createArenaSurvivorEnemy('slime-blob',{x:player.x+50,y:player.y},player,0);
    enemy.hp=7; state.enemies=[enemy];
    const projectile=createArenaSurvivorProjectile({originX:player.x,originY:player.y,angleRad:0,ownerId:player.playerId,ownerKind:'player',now:0,damage:50});
    state.projectiles=[projectile];
    const next=applyDamageSystem(state,{projectileHits:[{projectileId:projectile.id,enemyId:enemy.id}],playerProjectileHits:[],enemyHits:[]},100);
    assert.equal(next.enemies.length,0);
    assert.equal(next.damageEvents[0].damage,7);
    assert.equal(next.damageEvents[0].x,enemy.x);
    assert.deepEqual(game.toPublicState(next).damageEvents,next.damageEvents);
    assert.equal(game.toControllerState(next).damageEvents,undefined);
  });
  test(`${mode}: melee impact emits a damage event`, () => {
    let state=setup(); const player=state.players[0];
    player.loadout.weapons=[createLoadoutWeaponState('rust-blade',1)];
    player.weaponRuntimeStates=createArenaSurvivorWeaponRuntimeStates(player.loadout);
    const enemy=createArenaSurvivorEnemy('slime-blob',{x:player.x+35,y:player.y},player,0);
    enemy.hp=1000; enemy.maxHp=1000; state.enemies=[enemy];
    state=applyAutoFireSystem(state,16,100);
    const weapon=state.players[0].weaponRuntimeStates[0];
    assert.equal(weapon.lastFiredAt,100);
    state.elapsedMs=weapon.meleeAttackResolvesAtMs;
    state=applyAutoFireSystem(state,16,200);
    assert.ok(state.damageEvents.length>0);
    assert.ok(Math.abs(state.damageEvents[0].damage-(1000-state.enemies[0].hp))<1e-9);
  });
}
test('hit history expires and remains bounded with unique IDs under heavy combat',()=>{
  const events=[];
  for(let i=0;i<600;i++)recordDamageEvent(events,100,{id:'mob',x:1,y:2,radius:3},4);
  assert.equal(events.length,256); assert.equal(new Set(events.map(e=>e.id)).size,256);
  assert.equal(collectDamageEvents({elapsedMs:900,damageEvents:events}).length,0);
});
