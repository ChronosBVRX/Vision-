# Arena Survivor: combat and readability pass

The target is Brotato-like responsiveness, clear silhouettes, six-weapon builds
and predictable stat investment. This is an adaptation for the shared host and
phone controllers, with the existing Wave and cooperative Survival modes.
It is not a claim of identical Brotato balance. The 100-HP baseline, 45-second
Wave rounds, survival world, team tether and evolution system remain game-specific.

## Rules shared by both modes

| Area | Previous behavior | Revised behavior |
| --- | --- | --- |
| Percentage stats | Every item multiplied the previous result | Percentage points add within each stat; global damage and weapon power still combine |
| Armor | 15 armor reduced damage by about 73% | 15 armor halves incoming damage; negative armor increases damage on the same scale |
| Dodge | Maximum 70% | Maximum 60% |
| Critical hits | Weapon multiplier multiplied character multiplier again | Weapon multiplier plus the character's bonus above the default 1.5 |
| Life steal | A fraction of damage, favoring oversized hits | Chance per hit to heal 1 HP; at most once every 100 ms across the player's weapons |
| Attack speed | Unbounded attack cadence | At most 12 ranged attacks/s; melee cooldown at least 180 ms |
| Player motion | Tether read positions already updated in player order | All players use the same start-of-tick positions; zero-duration updates do not move |
| Hordes | Enemies stacked at identical coordinates | Bounded local separation using spatial buckets; speed cannot exceed the enemy's configured speed |
| Projectiles | Endpoint overlap only; a piercing shot could repeatedly hit one target | Swept segment contacts sorted along travel; each enemy can be hit once per projectile |
| Projectile expiry | Last segment disappeared before collision checks | Last segment is checked, clipped to remaining range/lifetime, then retired |
| Experience | Exponential thresholds, large rewards for late unlocks | Next level requires 12 + 8 per completed level; unlock reward grows once per four waves |
| Level bonuses | Same values throughout the run | Tiers 1–4, rising every five levels; localized text uses the applied value |

Wave and Survival range bonuses now share an 8% base value, scaled by level-up
tier. The existing 20% Survival ranged-range reduction and 35% Survival shop
premium are retained. These are separate pacing choices, not hidden weapon stats.

## Weapons

`src/server/content/weaponBalance.ts` contains explicit damage/cooldown budgets
for every weapon and all four tiers. Both shop cards and combat read these
definitions. Fast weapons trade damage per hit for cadence; multi-shot weapons
pay for extra projectiles with lower per-projectile damage. Long-range bow and
rifle attacks retain a slower cadence. Starter pistol penetration is deferred
to tier 4. Every tier must improve direct damage per second (before critical
hits, penetration, character stats or evolutions).

## Enemy progression

- Wave HP scales by 12% of baseline per wave, damage by 4.5%.
- Speed grows by 0.8% per wave and stops growing at +18%; difficulty-tier speed
  modifiers still apply. Late enemies cannot gain unlimited speed just by aging.
- Enemy spawning uses linear party pressure: 1 + 0.45 per additional player.
  Enemy HP separately gains 30% per additional player.
- Survival uses the same combat curves with one minute corresponding to one
  wave. Its regular-enemy budget grows continuously, up to 160, and the spawn
  interval cannot fall below 200 ms. Survival objective bosses retain their own
  HP/damage tuning and are included in the existing encounter tests.
- Every difficulty tier influences Survival spawning, including easier tiers.

## Host presentation

Player sprite diameter is 1.25 times the collision diameter (formerly 1.65),
enemy diameter 1.15 (formerly 1.38). Standing characters do not bob, and entity
size no longer pulses. Existing weapon-size reductions remain; Marshmallow no
longer enlarges weapons by an additional 22%.

Wave keeps the whole arena visible with a fixed scale. Survival keeps at least
900 world units visible vertically, with group fitting when necessary. Switching
between one living player and a tightly grouped team does not magnify the action.

## Verification and limits

Run the game build before executing the Node tests:

```
npm run build
node --test scripts/balance.test.mjs scripts/combat-feedback.test.mjs scripts/survival.test.mjs scripts/frostfire-character-assets.test.mjs
node --test scripts/survival-soak.test.mjs
```

The balance suite covers weapon-tier progression, stat stacking, armor/crit,
life-steal cadence, difficulty and party-size bounds, movement, enemy separation,
and projectile contact order, penetration and expiry in both modes. The soak
suite exercises 21 active Survival minutes through all boss boundaries with
immortal bots; it validates stability, not human difficulty or survival rates.
Browser/device playability and subjective feel require a later manual playtest.

Verification on 2026-10-27: game TypeScript build, 33 regression tests and both
21-minute Survival soak cases passed. Peak enemy counts were 160 (tier 4) and
161 (tier 5, including an objective boss). Server, host and controller typechecks
and production builds passed when invoked directly by workspace. The root
typecheck command could not acquire the sync lock because another live
`local-games.mjs sync-local` process owned it; that process was left intact.
Vite emitted its existing large-chunk warnings. No browser tests were run.

Reference for the intended stat semantics:
[Brotato stats](https://brotato.wiki.spellsandguns.com/Stats).
