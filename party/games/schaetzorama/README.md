# Schaetzorama

Supports 1–16 players. Host progress and standings scroll when needed so all
players remain accessible in large groups.

Colorful estimation quiz for Open Party Lab with numeric, ranking, and assignment questions.

![In-game screenshot](docs/screenshots/host.png)

## Status

Beta. The game uses a resolution-independent DOM host with animated reveals and
a focused, one-question-at-a-time phone controller. A 10-round session contains
40 distinct questions with no repeats and ends on an overall ranking. The active
pool contains 147 prompts: 87 reviewed numeric, percentage and ranking questions,
plus 60 bilingual assignment tasks. Every assignment contains left-only,
right-only and overlapping examples, with varying overlap counts. The old 40
assignment tasks remain archived in the original data but are no longer selected.
Fresh numeric and percentage answers start at zero; all their ranges include zero.
Submitted answers are preserved when reconnecting. See [assignment review](docs/assignment-review.md). The
answer and copying phases have no time limit. Copying waits for every player to decide. Calm selection music and a separate reveal track
crossfade smoothly, with original synthesized cues for solutions, gauge movement, each player answer and points. Player answers appear 1.4 seconds apart with larger typography. Each result category waits for all phones to confirm Continue after the final answer is visible. An optional “Automatically continue and ready up” checkbox confirms each category and the next round for that player; mixed groups wait only for manual confirmations. This preference persists across rounds, and the final tenth round never starts another round.

## Run Through Open Party Lab

This repo is not a standalone app. Run it through the Open Party Lab platform.

Recommended layout:

```text
Open-Party-Lab/
  local-games/
    schaetzorama/
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
Colorful estimation quiz for Open Party Lab with numeric, ranking, and assignment questions.
```

Suggested topics:

```text
open-party-lab party-game browser-game phaser typescript local-multiplayer quiz-game
```

## Package Entrypoints

- `@open-party-lab/game-schaetzorama/manifest`
- `@open-party-lab/game-schaetzorama/protocol`
- `@open-party-lab/game-schaetzorama/server`
- `@open-party-lab/game-schaetzorama/host`
- `@open-party-lab/game-schaetzorama/controller`

The Platform should import only these public entrypoints.

## Development Checks

```bash
npm install
npm run typecheck
npm run build
npm test
npm run pack:dry-run
```

For visual checks, start Open Party Lab, add virtual controllers when needed, and capture host screenshots through a browser.

## License

Code is licensed under the Apache License 2.0. See [LICENSE](LICENSE).

Assets, generated media, word lists, prompts, and third-party references may need separate rights review before public store distribution.
