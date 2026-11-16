import test from 'node:test';
import assert from 'node:assert/strict';
import { arenaSurvivorServerGame as game } from '../dist/server/ArenaSurvivorServerGame.js';
import { updateSurvivalPause, handleSurvivalInput } from '../dist/server/survival.js';

for (const tier of [4, 5]) test(`Survival tier ${tier}: 21 active minutes including all boss boundaries`, {timeout:120000}, () => {
  const context = {now:100000,language:'de',roomSettings:{arenaSurvivorMode:'survival',arenaSurvivorDifficulty:tier},players:[0,1].map(i=>({id:`p${i}`,name:`Player ${i}`,color:'#22c55e',connected:true,isReady:true,score:0}))};
  let state=game.startRound(game.createInitialState(context),context);
  state.difficultyTier=tier;
  let peakEnemies=0, peakPickups=0, peakBytes=0, pauses=0;
  const bossBoundaries=new Set();
  const start=performance.now();
  while(state.elapsedMs<1260000) {
    context.now+=100;
    state=updateSurvivalPause(state,context.now,context.players.map(p=>p.id));
    if(state.survival.pause){
      pauses++;
      for(const p of state.players){
        const phase=state.survival.pause;
        if(!phase)break;
        const reward=state.survival.chestReward;
        const input=phase==='level_up'?{type:'shop:buy',offerId:p.shop.offers[0]?.id}:phase==='chest'?{type:'shop:buy',offerId:`${reward.id}:take`}:phase==='victory'?{type:'survival:endless',continueRun:true}:{type:'survival:ready'};
        state=handleSurvivalInput(state,{...input,playerId:p.playerId,sentAt:context.now},context.now);
      }
      continue;
    }
    // Keep the simulation alive to exercise late-game systems, not balance.
    for(const p of state.players){p.hp=p.maxHp;p.alive=true;p.invulnerableUntilMs=Infinity;p.moveInputX=Math.cos(state.elapsedMs/18000);p.moveInputY=Math.sin(state.elapsedMs/18000);}
    state=game.tick(state,100,context);
    assert.equal(state.result.outcome,'running');
    assert.ok(Number.isFinite(state.elapsedMs));
    peakEnemies=Math.max(peakEnemies,state.enemies.length);
    peakPickups=Math.max(peakPickups,state.pickups.length);
    for(const m of [5,10,15,20])if(state.elapsedMs>=m*60000)bossBoundaries.add(m);
    if(state.elapsedMs%10000===0){
      const snapshot=game.toControllerStateForPlayer(state,context,'p0');
      peakBytes=Math.max(peakBytes,JSON.stringify(snapshot).length);
      for(const entity of [...state.players,...state.enemies,...state.projectiles])assert.ok(Number.isFinite(entity.x)&&Number.isFinite(entity.y));
    }
  }
  assert.deepEqual([...bossBoundaries],[5,10,15,20]);
  console.log(JSON.stringify({tier,activeMinutes:state.elapsedMs/60000,wallSeconds:(performance.now()-start)/1000,peakEnemies,peakPickups,peakControllerBytes:peakBytes,pauses,heapMB:process.memoryUsage().heapUsed/1048576}));
});
