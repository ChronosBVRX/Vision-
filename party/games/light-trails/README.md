# Light Trails

Realtime trail-survival arena game for Open Party Lab with compact phone steering.

![In-game screenshot](docs/screenshots/host.png)

## Status

Alpha. The realtime survival loop is playable and uses patch streaming. Needs more performance checks, arena tuning, and input feel refinement.

## Run Through Open Party Lab

This repo is not a standalone app. Run it through the Open Party Lab platform.

Recommended layout:

```text
Open-Party-Lab/
  local-games/
    light-trails/
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
Realtime trail-survival arena game for Open Party Lab with compact phone steering.
```

Suggested topics:

```text
open-party-lab party-game browser-game phaser typescript local-multiplayer arcade
```

## Package Entrypoints

- `@open-party-lab/game-light-trails/manifest`
- `@open-party-lab/game-light-trails/protocol`
- `@open-party-lab/game-light-trails/server`
- `@open-party-lab/game-light-trails/host`
- `@open-party-lab/game-light-trails/controller`

The Platform should import only these public entrypoints.

## Development Checks

```bash
npm install
npm run typecheck
npm run build
npm run pack:dry-run
```

For visual checks, start Open Party Lab, add virtual controllers when needed, and capture host screenshots through a browser.

## License

Code is licensed under the Apache License 2.0. See [LICENSE](LICENSE).

Assets, generated media, word lists, prompts, and third-party references may need separate rights review before public store distribution.
