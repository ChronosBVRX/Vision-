import assert from "node:assert/strict";
import { test } from "node:test";
import { arenaSurvivorCharacterDefinitions } from "../dist/server/content/characterDefinitions.js";
import { arenaSurvivorWeaponDefinitions } from "../dist/server/content/weaponDefinitions.js";
import { arenaSurvivorItemDefinitions } from "../dist/server/content/itemDefinitions.js";
import { arenaSurvivorEnglishContent, getArenaSurvivorContentText, localizeArenaSurvivorPlayer, localizeArenaSurvivorPresentation } from "../dist/contentText.js";
import { createArenaSurvivorPlayer } from "../dist/server/factories/createPlayer.js";
import { createLoadoutWeaponState } from "../dist/server/loadout/arenaSurvivorLoadout.js";
import { serverGame } from "../dist/server/index.js";
import { buildArenaSurvivorControllerModel } from "../dist/controller/ArenaSurvivorController.js";

test("every character, weapon tier and item tier has game-owned English copy", () => {
  for (const character of arenaSurvivorCharacterDefinitions) {
    for (const value of [character.name, character.title, character.description]) assert.ok(arenaSurvivorEnglishContent[value], value);
  }
  for (const definition of [...arenaSurvivorWeaponDefinitions, ...arenaSurvivorItemDefinitions]) {
    for (const value of [definition.name, definition.baseDescription ?? definition.description, ...definition.levels.map(level => level.description)]) {
      assert.ok(arenaSurvivorEnglishContent[value], value);
    }
  }
});

test("loadout and shop translation preserves player names, identifiers, values and runtime state", () => {
  const player = createArenaSurvivorPlayer({ id: "p1", name: "Rostklinge", color: "#ffffff", score: 0, isReady: true, connected: true }, { x: 10, y: 20 }, 100, undefined,
    { weapons: [createLoadoutWeaponState("rust-blade", 1)], items: [] }, "schrotto-scharfschuss");
  player.shop.offers = [{ id: "offer-rust", kind: "weapon", weaponId: "rust-blade", title: "Rostklinge Lv. 2", description: "Etwas mehr Schaden und minimal flotter.", targetLevel: 2, cost: 12, affordable: true, purchased: false,
    summary: "Lv. 2 | Direkt kombinierbar zu Lv. 3", detailLines: [{ label: "Projektile", value: "2" }] }];
  const before = structuredClone(player);
  const english = localizeArenaSurvivorPlayer(player, "en");
  assert.equal(english.name, "Rostklinge");
  assert.equal(english.character.name, "Scrappy");
  assert.equal(english.loadout.weapons[0].displayName, "Rust Blade");
  assert.equal(english.loadout.weapons[0].weaponId, "rust-blade");
  assert.equal(english.shop.offers[0].title, "Rust Blade Lv. 2");
  assert.equal(english.shop.offers[0].summary, "Lv. 2 | Combine immediately to Lv. 3");
  assert.equal(english.shop.offers[0].detailLines[0].label, "Projectiles");
  assert.equal(english.shop.offers[0].cost, 12);
  assert.deepEqual(english.stats, before.stats);
  assert.deepEqual(player, before);
  assert.equal(localizeArenaSurvivorPlayer(player, "de").character.name, "Schrotto");
});

test("chest offer presentation is translated without altering its recipient or selection key", () => {
  const reward = { id: "chest-1", playerId: "Rostklinge", core: false, salvageGold: 12, offer: { weaponId: "rust-blade", title: "Rostklinge", description: "Solider Grundschlag ohne klare Schwache." } };
  const result = localizeArenaSurvivorPresentation(reward, "en");
  assert.equal(result.offer.title, "Rust Blade");
  assert.equal(result.playerId, reward.playerId);
  assert.equal(result.offer.weaponId, reward.offer.weaponId);
  assert.equal(result.salvageGold, 12);
  assert.equal(getArenaSurvivorContentText("✦ Rostklinge", "en"), "✦ Rust Blade");
});

test("public and private serialization use the round language without widening private views", () => {
  const context = { now: 1000, language: "en", roomSettings: {}, players: [
    { id: "p1", name: "Schrotto", color: "#fff", connected: true, isReady: true, score: 0, selectedCharacterId: "schrotto-scharfschuss" },
    { id: "p2", name: "Other player", color: "#000", connected: true, isReady: true, score: 0, selectedCharacterId: "doktor-knolle" }
  ] };
  const state = serverGame.createInitialState(context);
  const host = serverGame.toPublicState(state, context);
  const phone = serverGame.toControllerStateForPlayer(state, context, "p1");
  assert.equal(host.players[0].character.name, "Scrappy");
  assert.equal(host.players[1].character.name, "Doctor Spud");
  assert.equal(phone.players.length, 1);
  assert.equal(phone.players[0].name, "Schrotto");
  assert.equal(phone.players[0].character.name, "Scrappy");
  assert.equal(state.players[0].character.name, "Schrotto");

  const controller = (language) => buildArenaSurvivorControllerModel({ state: {
    room: { language, players: context.players }, player: { id: "p1" }, game: { phase: "result", state: host }
  }, onInput() {} });
  assert.ok(controller("de").playerStats.some(stat => stat.label === "Bewegungstempo"));
  assert.ok(controller("de").playerStats.some(stat => stat.label === "Lebensraub"));
  assert.ok(controller("en").playerStats.some(stat => stat.label === "Move Speed"));
});
