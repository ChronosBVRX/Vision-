import test from 'node:test';
import assert from 'node:assert/strict';
import { arenaSurvivorServerGame as game } from '../dist/server/ArenaSurvivorServerGame.js';
import { openSurvivalPause, handleSurvivalInput, resolveSurvivalCombat, advanceSurvivalWorld, updateSurvivalPause } from '../dist/server/survival.js';
import { applyMovementSystem } from '../dist/server/systems/movementSystem.js';
import { applyDamageSystem } from '../dist/server/systems/damageSystem.js';
import { createArenaSurvivorEnemy } from '../dist/server/factories/createEnemy.js';
import { resolveArenaSurvivorWeaponLevel } from '../dist/server/loadout/arenaSurvivorLoadout.js';

function setup(mode = 'survival', count = 2) {
  const context = { now: 100000, language: 'de', roomSettings: { arenaSurvivorMode: mode }, players: Array.from({length:count}, (_,i) => ({id:`p${i}`, name:`Player ${i}`, color:'#22c55e', connected:true, isReady:true, score:0})) };
  let state = game.startRound(game.createInitialState(context), context);
  if (state.survival) state = updateSurvivalPause(state, context.now, context.players.map(p => p.id));
  return { state, context };
}
const input = (type, playerId = 'p0', extra = {}) => ({type, playerId, sentAt:0, ...extra});
test('A simultaneous Survival wipe transitions to a visible defeated result', () => {
  const { state: initial, context } = setup();
  const state = structuredClone(initial);
  state.survival.participantIds = context.players.map(player => player.id);
  state.players.forEach(player => { player.alive = false; player.hp = 0; });
  const ended = game.tick(state, 16, context);
  assert.equal(ended.phase, 'locked');
  assert.equal(ended.result.outcome, 'defeated');
  assert.equal(ended.result.title, 'Survival · Game Over');
});
function kill(state, definitionId = 'slime-blob') {
  const enemy = createArenaSurvivorEnemy(definitionId, {x:0,y:0}, state.players[0], state.elapsedMs);
  const before = {...state, enemies:[...state.enemies,enemy]};
  return resolveSurvivalCombat(state, before, 100000);
}

test('Wave remains a 45 second round, Survival is continuous and uses Frostfire', () => {
  let {state,context} = setup('wave');
  assert.equal(state.survival,undefined);
  state = game.tick(state,45000,context); assert.equal(state.phase,'locked'); assert.equal(state.result.outcome,'survived');
  ({state,context}=setup()); state = game.tick(state,45000,context);
  assert.equal(state.phase,'playing'); assert.equal(state.visualTheme,'frostfire-saga'); assert.equal(state.arenaWidth,4800);
});
test('Shared XP pauses and produces three independently rolled offers per player', () => {
  let {state}=setup();
  while (!state.survival.pause) state=kill(state);
  assert.equal(state.survival.pause,'level_up'); assert.equal(state.players[0].level,state.players[1].level);
  state.players.forEach(p=>{ assert.equal(p.shop.offers.length,3); assert.ok(p.shop.offers.every(o=>o.kind==='upgrade' && o.levelBonusModifiers)); });
  assert.notDeepEqual(state.players[0].shop.offers.map(o=>o.id),state.players[1].shop.offers.map(o=>o.id));
  state=handleSurvivalInput(state,input('shop:buy','p0',{offerId:state.players[0].shop.offers[0].id}),100000);
  assert.equal(state.survival.pause,'level_up');
  state=handleSurvivalInput(state,input('shop:buy','p1',{offerId:state.players[1].shop.offers[0].id}),100000);
  assert.equal(state.survival.pause,null);
  const snapshot=JSON.stringify(state.players);
  state=handleSurvivalInput(state,input('shop:buy','p0',{offerId:'invalid'}),100001);
  assert.equal(JSON.stringify(state.players),snapshot);
});
test('Paused snapshots keep synchronizing while simulation freezes, then resume immediately', () => {
  let {state,context}=setup(); state=game.tick(state,1000,context); openSurvivalPause(state,'shop');
  const snapshot=structuredClone(state); context.now+=600000;
  const previous=state;
  state=game.tick(state,600000,context);
  assert.notEqual(state,previous);
  assert.equal(state.updatedAt,context.now);
  assert.deepEqual({...state,updatedAt:snapshot.updatedAt},snapshot);
  for (const p of state.players) state=handleSurvivalInput(state,input('survival:ready',p.playerId),context.now);
  assert.equal(state.survival.pause,null);
  state=game.tick(state,16,{...context,now:context.now+16}); assert.equal(state.elapsedMs,snapshot.elapsedMs+16);
});
test('Shops coexist, entering one consumes only that shop, and forge has no timer', () => {
  let {state}=setup(); state.elapsedMs=180000; advanceSurvivalWorld(state); state.elapsedMs=360000; advanceSurvivalWorld(state);
  assert.equal(state.survival.objectives.filter(o=>o.kind==='shop').length,2);
  const shop=state.survival.objectives.find(o=>o.kind==='shop'); Object.assign(state.players[0],{x:shop.x,y:shop.y});
  state=resolveSurvivalCombat(state,{...state,enemies:[...state.enemies]},100000);
  assert.equal(state.survival.pause,'shop'); assert.equal(state.survival.objectives.filter(o=>o.kind==='shop').length,1);
});
test('Cores are personal, Forge-only, and each evolution spends one core once', () => {
  let {state}=setup(); const w=state.players[0].loadout.weapons[0]; w.level=4;
  state.players[1].evolutionCores=2;
  state=handleSurvivalInput(state,input('survival:evolve','p0',{weaponInstanceId:w.weaponInstanceId}),100000); assert.equal(w.evolved,undefined);
  openSurvivalPause(state,'forge');
  state=handleSurvivalInput(state,input('survival:evolve','p0',{weaponInstanceId:w.weaponInstanceId}),100000); assert.equal(w.evolved,undefined);
  state.players[0].evolutionCores=1;
  state=handleSurvivalInput(state,input('survival:evolve','p0',{weaponInstanceId:w.weaponInstanceId}),100000);
  assert.equal(w.evolved,true); assert.equal(state.players[0].evolutionCores,0); assert.equal(state.players[1].evolutionCores,2);
  const normal=resolveArenaSurvivorWeaponLevel(w.weaponId,4).levelDefinition;
  assert.equal(resolveArenaSurvivorWeaponLevel(w.weaponId,4,true).levelDefinition.damage,normal.damage*2);
});
test('Rare chests pause for the recipient to keep or salvage the core', () => {
  const recipients=new Set();
  for(let seed=1;seed<=30;seed++) {
    let {state}=setup(); state.seed=seed*991;
    state.survival.objectives=[{id:'rare',kind:'rare_chest',x:state.players[0].x,y:state.players[0].y}];
    state=resolveSurvivalCombat(state,{...state,enemies:[]},100000);
    assert.equal(state.survival.pause,'chest');
    const reward=state.survival.chestReward;
    recipients.add(reward.playerId);
    const salvage=seed%2===0;
    const owner=state.players.find(p=>p.playerId===reward.playerId);
    const gold=owner.materials;
    state=handleSurvivalInput(state,input('shop:buy',reward.playerId,{offerId:`${reward.id}:${salvage?'salvage':'take'}`}),100000);
    assert.equal(state.survival.pause,null);
    assert.equal(state.players.reduce((sum,p)=>sum+p.evolutionCores,0),salvage?0:1);
    assert.equal(state.players.find(p=>p.playerId===reward.playerId).materials,gold+(salvage?reward.salvageGold:0));
  }
  assert.equal(recipients.size,2);
});
test('Mini-boss death awards exactly one personal core', () => {
  let {state}=setup(); state.elapsedMs=300000; advanceSurvivalWorld(state);
  const boss=state.enemies[0], before={...state,enemies:[...state.enemies]}; state.enemies=[];
  state=resolveSurvivalCombat(state,before,100000);
  assert.ok(boss); assert.equal(state.players.reduce((sum,p)=>sum+p.evolutionCores,0),1);
});
test('Respawn grows 5/10/15/20/25 seconds, restores 25% HP, and grants 3 seconds immunity', () => {
  let {state}=setup();
  for(let death=1;death<=7;death++) {
    state.players[0].hp=0; state.players[0].alive=false;
    state=resolveSurvivalCombat(state,{...state,enemies:[]},100000);
    const deadline=state.players[0].respawnAtMs;
    assert.equal(deadline-state.elapsedMs,Math.min(25,death*5)*1000);
    state.elapsedMs=deadline;
    state=resolveSurvivalCombat(state,{...state,enemies:[]},100000);
    const p=state.players[0]; assert.equal(p.alive,true); assert.equal(p.hp,p.maxHp*.25); assert.equal(p.invulnerableUntilMs,deadline+3000);
    const enemy=createArenaSurvivorEnemy('slime-blob',p,p,deadline); state.enemies=[enemy];
    const hp=p.hp;
    state=applyDamageSystem(state,{projectileHits:[],playerProjectileHits:[],enemyHits:[{enemyId:enemy.id,playerId:p.playerId}]},deadline+2999);
    assert.equal(state.players[0].hp,hp); state.enemies=[];
  }
});
test('Single-player death and simultaneous team wipe end immediately', () => {
  for(const count of [1,2]) { let {state}=setup('survival',count); state.players.forEach(p=>p.alive=false); state=resolveSurvivalCombat(state,{...state,enemies:[]},100000); assert.equal(state.phase,'locked'); }
});
test('Group constraint allows return and tangent movement while blocking outward travel', () => {
  let {state}=setup(); Object.assign(state.players[0],{x:1000,y:1000,moveInputX:-1,moveInputY:0}); Object.assign(state.players[1],{x:1620,y:1000});
  state=applyMovementSystem(state,16); assert.ok(state.players[0].x>=1000);
  state.players[0].moveInputX=0; state.players[0].moveInputY=1; state=applyMovementSystem(state,16); assert.ok(state.players[0].y>1000);
  state.players[0].moveInputX=1; state.players[0].moveInputY=0; const x=state.players[0].x; state=applyMovementSystem(state,16); assert.ok(state.players[0].x>x);
  assert.ok(Math.hypot(state.players[0].x-state.players[1].x,state.players[0].y-state.players[1].y)<=620.001);
});
test('Final boss appears at 20 active minutes; victory keeps build and can continue Endless', () => {
  let {state}=setup(); state.elapsedMs=1199999; advanceSurvivalWorld(state); assert.equal(state.survival.finalBossId,undefined);
  state.elapsedMs=1200000; advanceSurvivalWorld(state); assert.ok(state.survival.finalBossId);
  const before={...state,enemies:[...state.enemies]}; state.enemies=state.enemies.filter(e=>e.id!==state.survival.finalBossId);
  state=resolveSurvivalCombat(state,before,100000); assert.equal(state.survival.pause,'victory'); assert.equal(state.survival.won,true);
  const loadout=structuredClone(state.players[0].loadout);
  for(const p of state.players) state=handleSurvivalInput(state,input('survival:endless',p.playerId,{continueRun:true}),100000);
  assert.equal(state.survival.endless,true); assert.deepEqual(state.players[0].loadout,loadout);
});
test('Disconnected participants do not block ready check', () => {
  let {state}=setup(); openSurvivalPause(state,'shop'); state=handleSurvivalInput(state,input('survival:ready'),100000);
  state=updateSurvivalPause(state,100000,['p0']); assert.equal(state.survival.pause,null);
});
