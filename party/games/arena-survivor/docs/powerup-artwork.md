# Power-up PNG artwork

## Asset delivery fix (2026-10-30)

The nine additional Frostfire stat icons previously existed only under
`public/controller`. Their root-relative URLs also need matching files under
`public/host`: the combined server serves `/arena-survivor/...` from the host
root, including requests originating from a `/controller/` page. Both roots
now contain the complete upgrade set. Classic and Obsidian item artwork is
also mirrored to the host root, and their level-up bonuses now use suitable
existing item icons instead of missing icon mappings.

`scripts/shop-assets.test.mjs` checks all offered bonus IDs and the entire
weapon/item catalog across all four themes against both delivery roots,
including PNG headers/dimensions and SVG markup.

Generated with the built-in image generation tool. Final assets: `public/controller/arena-survivor/themes/frostfire-saga/upgrades/core.png` and `dodge.png`. Money uses the existing `pickups/material.png`.

## Core prompt

Create one square PNG inventory icon for an Evolution Core in Frostfire Saga, a painterly dark fantasy action RPG. A single faceted icy cyan crystal containing a small warm amber magical heart, held in a weathered dark silver rune setting. Strong readable silhouette at 64px, centered with generous padding, detailed hand-painted game art, soft cool highlights, restrained glow. Genuine transparent alpha background. No text, no letters, no border, no scenery, no extra objects.

## Dodge prompt

Create a single square PNG power-up inventory icon for Frostfire Saga, painterly dark fantasy RPG. Subject: a silver boot slipping past a red arrow, pale cyan motion trail. Centered isolated emblem, strong silhouette readable at 64px, textured metal and magical highlights, restrained glow, consistent premium hand-painted game art. Genuine transparent alpha background. Generous empty padding. No text, letters, numbers, border, scenery or characters.

## Completed power-up set

All 17 Frostfire stat bonuses now have dedicated PNGs. The nine new files are dodge.png, luck.png, harvesting.png, pickup.png, regen.png, crit-damage.png, melee-power.png, ranged-power.png and magic-power.png in the upgrades directory above. Existing eight PNGs are preserved. Frostfire no longer shares images through aliases; other themes retain their previous mappings.

### luck

Create a single square PNG power-up inventory icon for Frostfire Saga, painterly dark fantasy RPG. Subject: an emerald four-leaf clover held in a weathered silver charm. Centered isolated emblem, strong silhouette readable at 64px, textured metal and magical highlights, restrained glow, consistent premium hand-painted game art. Genuine transparent alpha background. Generous empty padding. No text, letters, numbers, border, scenery or characters.

### harvesting

Create a single square PNG power-up inventory icon for Frostfire Saga, painterly dark fantasy RPG. Subject: a small golden wheat sheaf and silver sickle. Centered isolated emblem, strong silhouette readable at 64px, textured metal and magical highlights, restrained glow, consistent premium hand-painted game art. Genuine transparent alpha background. Generous empty padding. No text, letters, numbers, border, scenery or characters.

### pickup

Create a single square PNG power-up inventory icon for Frostfire Saga, painterly dark fantasy RPG. Subject: a horseshoe magnet attracting three small golden crystals. Centered isolated emblem, strong silhouette readable at 64px, textured metal and magical highlights, restrained glow, consistent premium hand-painted game art. Genuine transparent alpha background. Generous empty padding. No text, letters, numbers, border, scenery or characters.

### regen

Create a single square PNG power-up inventory icon for Frostfire Saga, painterly dark fantasy RPG. Subject: a green healing droplet surrounded by two silver circular arrows. Centered isolated emblem, strong silhouette readable at 64px, textured metal and magical highlights, restrained glow, consistent premium hand-painted game art. Genuine transparent alpha background. Generous empty padding. No text, letters, numbers, border, scenery or characters.

### crit-damage

Create a single square PNG power-up inventory icon for Frostfire Saga, painterly dark fantasy RPG. Subject: a cracked crimson crystal struck by a sharp golden lightning slash. Centered isolated emblem, strong silhouette readable at 64px, textured metal and magical highlights, restrained glow, consistent premium hand-painted game art. Genuine transparent alpha background. Generous empty padding. No text, letters, numbers, border, scenery or characters.

### melee-power

Create a single square PNG power-up inventory icon for Frostfire Saga, painterly dark fantasy RPG. Subject: a heavy steel gauntlet gripping a short frost-covered sword. Centered isolated emblem, strong silhouette readable at 64px, textured metal and magical highlights, restrained glow, consistent premium hand-painted game art. Genuine transparent alpha background. Generous empty padding. No text, letters, numbers, border, scenery or characters.

### ranged-power

Create a single square PNG power-up inventory icon for Frostfire Saga, painterly dark fantasy RPG. Subject: a steel crossbow bolt piercing a small circular target with icy sparks. Centered isolated emblem, strong silhouette readable at 64px, textured metal and magical highlights, restrained glow, consistent premium hand-painted game art. Genuine transparent alpha background. Generous empty padding. No text, letters, numbers, border, scenery or characters.

### magic-power

Create a single square PNG power-up inventory icon for Frostfire Saga, painterly dark fantasy RPG. Subject: a violet arcane orb floating above an open silver-bound spellbook. Centered isolated emblem, strong silhouette readable at 64px, textured metal and magical highlights, restrained glow, consistent premium hand-painted game art. Genuine transparent alpha background. Generous empty padding. No text, letters, numbers, border, scenery or characters.
