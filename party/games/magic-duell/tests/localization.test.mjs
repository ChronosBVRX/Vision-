import assert from "node:assert/strict";
import { test } from "node:test";
import { mageDuelSpellCatalog } from "../dist/protocol.js";
import { localizeSpell, localizeGestureReason } from "../dist/text.js";

test("every spell has English presentation copy without changing its game mechanics", () => {
  for (const spell of mageDuelSpellCatalog) {
    const translated = localizeSpell(spell, "en");
    assert.notEqual(translated.displayName, spell.displayName, spell.id);
    assert.notEqual(translated.description, spell.description, spell.id);
    const { displayName, description, ...mechanics } = spell;
    const { displayName: _name, description: _description, ...translatedMechanics } = translated;
    assert.deepEqual(translatedMechanics, mechanics);
    assert.equal(localizeSpell(spell, "de"), spell);
  }
  assert.equal(localizeGestureReason("Dreieck mit Strich erkannt", "en"), "Slashed triangle recognised");
  assert.equal(localizeGestureReason("Zauber misslungen", "en"), "Spell failed");
});
