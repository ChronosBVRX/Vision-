# Survival mode

Select **Survival · Frostfire Saga** in Run Setup. **Wave** remains the default and keeps its existing 45-second combat/shop rounds. Survival shares characters, equipment, stats, combat, pickups, shop transactions and the controller layout with Wave.

## Run flow

Explore the fixed 4800 × 3600 Frostfire field, collect loot and survive continuous combat. A new quiet, repeating frost-speckled ground texture covers the freely traversable field. There are no prescribed roads or corridor-based arenas. Eighteen separate rock/crystal and dead-tree islands have server-authoritative collision for players and enemies; enemies steer around these islands. Projectiles fly over the low rocks. The camera follows living players. A maximum pair distance of 620 world units keeps the group on screen; outward motion is constrained while return and tangent movement remain possible. A gold link indicates players nearing the boundary. Dead players do not constrain movement or the camera.

The merchant stall, wooden chest, rare crystal chest and evolution shrine now have their own generated transparent sprites, with subtle ground markers and magical glow. Objectives are randomly placed on clear ground with separation from other objectives. Two additional ground textures form softly blended frost and ash patches across the field. The minimap shows players, objectives and the camera area; scenery obstacles are omitted. Asset provenance and generation prompts: [survival-artwork.md](survival-artwork.md).

All melee and projectile kills contribute to one XP pool. On each level, combat stops and each connected participant receives three independently generated free choices from 16 small stat bonuses only, including dodge, luck, regeneration, pickup radius, critical damage and category-specific power. Choices can coincide by chance. Every participant selects one; combat resumes immediately after the last choice. Excess XP carries into the next level.

Shops appear every three active minutes and coexist until visited. Touching one opens everyone's individual shop, with buy, sell, reroll, combine and build inspection. Each player confirms completion without a timer. Only the visited shop is consumed.

Chests first appear after 45 seconds and then every 60 seconds, up to six unopened chests. Opening one pauses combat for everyone. One connected recipient chooses to upgrade an already owned weapon by one level (up to IV) or salvage it for gold (50% base value, minimum 8). Rare chests offer one Evolution Core or 35 gold. When all owned weapons are maxed out (or none are owned), normal chests also offer a core. No automatic money or healing bundle is granted. Only the recipient decides; combat resumes immediately. If the recipient disconnects, the reward is salvaged and credited to them automatically.

Mini-bosses appear at minutes 5, 10 and 15. Each grants one randomly assigned personal core. A Forge first appears at minute 6 and then every four minutes. Touching it pauses combat for everyone. Each player's Weapon IV can be evolved once for one of their own cores, without passive-item requirements. Multiple evolutions are possible during one visit. The Forge disappears after use; everyone confirms completion before resuming.

Evolutions retain the weapon and its slot: double base damage, 30 percent shorter cooldown, 30 percent more range and one additional projectile for projectile attacks. Evolved melee weapons instead orbit at radius 100, hitting enemies along the swept arc, at most once per enemy every 500ms. Shared orbit geometry aligns host artwork with server hits. Violet weapon art remains; the character aura is removed. Normal weapon upgrades stop at IV; six active weapon slots apply. Passive items have their existing individual maximum levels and no global slot cap.

In multiplayer, personal death counts set active-time respawn delays of 5, 10, 15, 20 and then at most 25 seconds. Respawn happens beside a living teammate with 25 percent maximum HP and three seconds of damage immunity; moving, attacking and collecting continue normally. A simultaneous wipe ends the run immediately before any respawn. Solo death ends the run.

At twenty active minutes the final boss appears. Defeating it records the normal run as won and offers the group **Continue Endless**. Every connected participant must opt in; choosing to finish ends with the victory. Endless keeps the build and increases combat pressure, with mini-bosses every two minutes. A later wipe does not remove the normal-run victory.

## Clocks and participation

Survival uses `elapsedMs` for all combat timestamps, including damage immunity and contact cooldowns. No combat systems run during a level choice, shop, Forge or victory decision or chest decision. Spawn, projectile, attack and respawn timers therefore freeze together. Paused ticks publish fresh snapshots so throttled or dropped controller packets cannot leave the selection screen missing. Combat resumes without a countdown.

The run roster is fixed at start. Disconnected participants do not block a team confirmation or receive randomly distributed loot; reconnecting roster members participate again. No connected participants freezes the run. New arrivals join the next run.

## Navigation and balancing

The host shows team level, a shared XP bar, active time, player resources and respawn timers. The minimap marks players, shops, chests, Forges and bosses. Shops, Forges and bosses outside the camera have directional markers.

Enemy content unlocks through the existing difficulty director using active minutes as progression. Horde capacity grows in three-minute bands from 18 toward 180, with a multiplayer adjustment. Spawn intervals decrease from 1100 ms toward 140 ms. Enemy health, speed, damage and composition reuse the existing progression and selected danger tier. These are first-pass alpha values, not a measured twenty-minute balance pass.

Implementation: `src/server/survival.ts`, integration in `ArenaSurvivorServerGame.ts`, shared movement/spawn systems, `src/host/survivalOverlay.ts` and the existing controller shop builder. The platform's modern shop layout displays Survival context and core counts.

Verification: the earlier twelve focused server checks passed. The final changes are build-verified only, as requested; no completed browser or physical-phone QA is claimed.

Survival shop purchases and rerolls cost 35% more (rounded up). Ranged weapon reach is reduced by 20%; melee and magic retain their range. Normal enemies spawn about 650 world units around the living team center. Normal enemies and pending spawns farther than 1800 units from every living participant are removed before combat accounting, without kills, loot or XP. Objective bosses remain so progression cannot be lost. Host enemy sprites outside the camera plus a safety margin are pooled instead of updated/rendered.

Balance update: XP thresholds are ceil(1.5 times the original threshold), starting at 23. Life steal heals one quarter of its previous damage-based amount in every mode. Found weapons have a nonzero base-price resale value. Normal enemy HP/damage scale by +12%/+7% per active minute, with faster scaling and spawn pressure at tiers 4 (+25%) and 5 (+50%). Speed growth is capped at +40%. Wave boss damage is doubled; Survival bosses use 40/70 base contact damage (mini/final), multiplied by 1+0.2*(tier-1), and matching projectile scaling. Gold and Core choice cards have explicit SVG icons.
