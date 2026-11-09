# Illustrated action artwork

`public/controller/magic-arena/action-art-atlas.png` is original artwork created
with OpenAI Imagegen for Magic Arena on 2026-10-27. No third-party game assets
were copied. It contains 25 independent illustrations in one regular 5×5 atlas.
The platform copies the atlas into the controller's public asset directory with
`npm run games:sync-local`. The UI respects Vite's deployment base path.

The artwork is decorative; names, descriptions, selection borders and disabled
states remain real UI. The same atlas is used for base actions, hand cards and
planned slots. One image is fetched and decoded for the whole illustrated set.

| Row | Column 1 | Column 2 | Column 3 | Column 4 | Column 5 |
| --- | --- | --- | --- | --- | --- |
| 1 | move | punch | dig | sprint | charge_run |
| 2 | enemy_swap | lightning_strike | fireball | hammer_blow | bomb |
| 3 | meteor_strike | rift | earthquake | platform_break | stealth |
| 4 | shockwave | chain_lightning | pitfall | teleport | kraken |
| 5 | time_rift | magic_mine | storm_cloud | ally_swap | magnet_pull |

`ally_swap` and `magnet_pull` are legacy motifs; they are not added back to the
current playable deck. All 20 current cards and all three base actions have
their own illustration. If the atlas cannot load, the existing glyph fallback
keeps controls identifiable.
