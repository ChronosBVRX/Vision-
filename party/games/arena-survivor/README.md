# Arena Survivor

Co-op arena survival game for Open Party Lab with character choices, waves, and difficulty setup.

| Frostfire Saga | Obsidian Relay | Classic Arena |
| --- | --- | --- |
| ![Arena Survivor Frostfire Saga](docs/screenshots/frostfire-saga.jpg) | ![Arena Survivor Obsidian Relay](docs/screenshots/obsidian-relay.jpg) | ![Arena Survivor Classic Arena](docs/screenshots/classic.jpg) |

## Status

Beta. The survival loop, character choice, and lobby setup are already good to play locally. Still needs deeper progression, balancing, and enemy/readability passes before a stable release.

The October combat/readability pass revises weapon budgets, additive percentage
stats, armor, critical hits and life-steal. Enemy speed is bounded, party pressure
scales linearly, late level-ups grow stronger and projectiles use swept contacts
with one hit per target. Smaller sprites and wider, stable host views improve
arena readability. See [balance changes and verification limits](docs/balance-rework.md)
for details; physical-device playtesting and final balance remain open.

## Visual Themes

The host can select the complete art set during run setup:

- **Marshmallow Mayhem** combines thirteen face-safe toasted torso variants with the Motion Lab's wide procedural rig. Eyes track nearby enemies, idle/walk use the transferred warp motion, leg movement scales from 30 to 100 percent with player speed, the tank wears a helmet, and every other player receives a color-matched headband. The set also contains its own cocoa arena, seven confection enemy families, complete weapon/item catalogs, pickups, upgrades, HUD colors, and procedural music profile.
- **Frostfire Saga** is the default original hand-painted Nordic storybook set generated specifically for Arena Survivor. It covers the complete catalog and includes its own illustrated arena, HUD palette, waffenlose gameplay sprites, themed pickups, level-up icons, and procedural Nordic background music. Its armed character illustrations remain the lobby-selection portraits.
- **Obsidian Relay** is a detailed sci-fi set with its own arena map and a complete one-to-one catalog: 13 character visuals, 21 weapon visuals, 22 item icons, and 7 enemy families.
- **Classic Arena** keeps the original warm organic presentation.

Every art set is stored below `public/<surface>/arena-survivor/themes/<theme-id>`. The selected theme is synchronized to the phone lobby and the dedicated host setup lobby, so character portraits update before the run starts. The selection is otherwise presentational and does not change gameplay balance.

## Run Through Open Party Lab

This repo is not a standalone app. Run it through the Open Party Lab platform.

Recommended layout:

```text
Open-Party-Lab/
  local-games/
    arena-survivor/
```

From the Platform repo:

```bash
npm install
npm run games:sync-local
npm run dev:all
```

The Platform loads this game only when the repo exists locally and `npm run games:sync-local` links it. Missing optional games are skipped.

## GitHub Metadata

Description:

```text
Co-op arena survival game for Open Party Lab with character choices, waves, and difficulty setup.
```

Suggested topics:

```text
open-party-lab party-game browser-game phaser typescript local-multiplayer arena-survival
```

## Package Entrypoints

- `@open-party-lab/game-arena-survivor/manifest`
- `@open-party-lab/game-arena-survivor/protocol`
- `@open-party-lab/game-arena-survivor/server`
- `@open-party-lab/game-arena-survivor/host`
- `@open-party-lab/game-arena-survivor/controller`

The Platform should import only these public entrypoints.

## Development Checks

```bash
npm install
npm run typecheck
npm run build
npm run pack:dry-run
```

For visual checks, start Open Party Lab, add virtual controllers when needed, and capture host screenshots through a browser.

## Spawn Limits

Enemy pressure uses two configurable limits in `src/server/arenaSurvivorConfig.ts`:

- `baseMaxEnemiesOnScreen` is the wave- and difficulty-scaled pacing limit.
- `maxActiveEnemies` is the absolute safety cap and defaults to 100.

Active enemies never exceed the hard cap. Due spawn indicators remain queued until a slot opens. When a regular spawn burst is shortened because the pacing limit has nearly been reached, the remaining slots use the strongest currently unlocked enemy pool instead of adding more low-tier enemies.

## License

Code is licensed under the Apache License 2.0. See [LICENSE](LICENSE).

The Imagegen workflow and prompt set for Frostfire Saga are documented in [docs/generated-assets.md](docs/generated-assets.md). Other assets, generated media, word lists, prompts, and third-party references may need separate rights review before public store distribution.
