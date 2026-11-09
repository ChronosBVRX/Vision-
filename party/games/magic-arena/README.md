# Magic Arena

Recommended alpha 3D hex tactics game for Open Party Lab with cards, collapsing arenas, and hidden planning.

![In-game screenshot](docs/screenshots/host.png)

![Controller screenshot](docs/screenshots/controller.png)

## Status

Recommended alpha. The core loop is playable: players plan actions on phone controllers, resolve card and base actions in slot order, and fight on a shrinking 3D hex arena. Needs more balancing, card clarity, and longer playtest coverage.

### Fantasy island presentation (2026-10-28)

The host renders a stone hex island surrounded by animated blue water, with
brass inlays, warm lighting, shadows and filmic tone mapping. The former outer
ring and columns have been removed; the overview fits the island itself into
the viewport, with a larger playfield. Original articulated
low-poly mages replace capsule markers: robes, capes, faces, armour and crystal
staves share the faceted fantasy material language of the Emberfall reference.
Player colours remain visible on robes and base rings; weak ochre slabs and
reinforced stone with brass inlays remain distinguishable.

Walking uses directional steps; melee and spells use arm, torso and staff poses.
Fireballs and meteors resolve their visual impacts after travel, with short-lived
bursts and physical debris. The camera follows actions by default. Reduced-motion
preferences disable camera travel, idle motion and articulated gestures.
The platform's phone layout uses matching dark stone / parchment / brass colours,
small mage silhouettes and original painted artwork for every action and card.
The 25-image atlas covers all 23 active actions plus two legacy motifs. Card
artwork is larger and labelled; planned slots use the same artwork. See
`docs/action-art.md` for the atlas mapping. Gameplay rules are unchanged.

Figures, unchanged hexes and labels retain their GPU resources between actions.
Unchanged network snapshots do not rebuild the scene; rotations use elapsed
time, and shadow updates are limited to 30 Hz during actions.

Verification: game typecheck/build and full platform typecheck/build passed.
In-app browser checks used the real host scene and controller with an isolated
debug-state harness: overview, action camera and fireball rendering, plus figure
selection, movement target selection and slot submission at 390 × 844.
A final 24-second in-app run at 1280×720 with 24 figures, camera travel, six
actions and HP/terrain updates measured 59.3 FPS during actions (95th-percentile
frame time 16.8 ms; no frames over 50 ms). This is a local development-renderer
sample, not a guarantee for other GPUs or a full networked match. Physical
phones and sustained GPU performance were not checked. Screenshots above
predate this visual overhaul.

## Run Through Open Party Lab

This repo is not a standalone app. Run it through the Open Party Lab platform.

Recommended layout:

```text
Open-Party-Lab/
  local-games/
    magic-arena/
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
Recommended alpha 3D hex tactics game for Open Party Lab with cards, collapsing arenas, and hidden planning.
```

Suggested topics:

```text
open-party-lab party-game browser-game threejs phaser typescript local-multiplayer tactics-game 3d-game magic-arena
```

## Package Entrypoints

- `@open-party-lab/game-magic-arena/manifest`
- `@open-party-lab/game-magic-arena/protocol`
- `@open-party-lab/game-magic-arena/server`
- `@open-party-lab/game-magic-arena/host`
- `@open-party-lab/game-magic-arena/controller`

The Platform should import only these public entrypoints.

## Host Shortcuts

- `S`: Toggles the host camera between static overview and moving mode (default; smoothly follows the acting figure and looks toward the action target). Reduced-motion preferences keep the overview stationary. The current mode is shown as a badge in the bottom-right corner of the host screen.
- `D`: Toggles the debug overlay with player info and the event log.

## Development Checks

```bash
npm install
npm run typecheck
npm run test:rules
npm run build
npm run pack:dry-run
```

For visual checks, start Open Party Lab, add virtual controllers when needed, and capture host/controller screenshots through a browser.

## License

Code is licensed under the Apache License 2.0. See [LICENSE](LICENSE).

Assets, generated media, prompts, and third-party references may need separate rights review before public store distribution.
