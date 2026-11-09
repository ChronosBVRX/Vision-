# Fantasy island visual pass — 2026-10-28

The art direction follows the supplied Emberfall project (`AI_PenAndPaper`),
particularly its faceted characters, stone architecture, brass, warm key light,
cool rim light and parchment serif captions. This is a playable hex arena in
that visual language; it is not a recreation of the film's table scene.
All scenery and character geometry is original code-native Three.js art. Card
illustrations are original Imagegen artwork, documented in `action-art.md`.
No reference assets, private episode data or raster mockups are shipped.

## Visual comparison and corrections

| Reference characteristic | Implemented result / review |
| --- | --- |
| Faceted people with cloth, faces, armour and props | Articulated mages with deterministic hair/skin and three silhouettes, capes, shoulder armour and crystal staves replace capsules. |
| Stone and subdued materials | Sage stone and fragile ochre slabs stand in open animated blue water; strong slabs carry brass inlays. The decorative outer ring and columns are removed at the user's request. |
| Warm key light and cool rim | Warm directional key, cool rim, limited brazier lights, soft shadows and filmic tone mapping. |
| Brass / parchment typography | Serif action caption, brass borders and parchment text; controller has locally scoped matching tokens. Existing gameplay copy is preserved. |
| Camera movement and character presence | Action camera is now default; gait, torso, arm and staff poses convey movement, melee and casting. Overview was pulled back after the nearest row clipped in the first browser capture. |
| Readable small-screen decisions | At 390×844, tested own-figure selection → fireball target → slot submission; state advanced from 0/4 to 1/4 with the illustrated fireball in the slot. All 23 active illustrations loaded; the reserved card became disabled. |
| Illustrated cards and base actions | All 23 active actions use distinct paintings from a 25-motif atlas. Larger art, visible names and matching slot previews replace the former symbols. Two legacy motifs remain inactive. |

The overview now calculates perspective framing from the hex radius and viewport
aspect ratio, without reserving space for the former ring. The island is larger
while its perimeter remains inside the viewport.

## Motion and resource reuse

Rotations now sample elapsed time instead of adding an angle every frame.
Models, unchanged field meshes and unchanged labels keep their GPU resources;
only changed HP/terrain/label data creates replacements. Unchanged network
snapshots skip rebuilding entirely. Shadows use a 1024 map and update at up to
30 Hz during actions; the camera and character animation continue every frame.

Final local development measurement: 1280×720, 24 figures, 24 seconds, warm-up
excluded, five unchanged snapshots per second. The six actions were movement,
fireball, meteor, chain lightning, kraken and earthquake, with camera travel and
field/HP updates. The action sequence measured 59.3 FPS, 16.8 ms p95 frame time
and zero frames over 50 ms. Per-action averages ranged from 58.7 to 60 FPS.
The idle segment measured 55.5 FPS, p95 33.3 ms and no frames over 50 ms. No
unnecessary scene rebuilds were observed. Before resource caching, the same
style of camera/spell sample measured about 43 FPS and p95 33.4 ms.
These short samples describe this browser/computer only; they do not establish
performance on other hardware, long matches or networked sessions.

Intentional differences: arena geometry and party colours preserve tactical
readability; characters are smaller in overview than the reference close-up.
Effects and HUD remain code-native. This adapts the reference's style rather
than reproducing its composition or film subtitles pixel for pixel.

## Verification boundary

- Individual game typecheck/build and complete platform typecheck/build passed;
  Vite reports its existing large-chunk warnings.
- The Codex in-app browser rendered the actual host scene and controller against
  an isolated debug-state harness. Desktop overview / action camera, melee,
  fireball and meteor states were inspected, together with 390×844 controller
  layout and slot submission. The host reported no browser errors.
- Compared saved host/controller captures and the supplied reference frame by
  image inspection. Desktop capture was 1280×720, phone viewport 390×844; the
  1920×1080 reference was reviewed at its original size.
- The temporary controller harness needed the normal React refresh preamble;
  its initial missing-preamble error was corrected. Production controller code
  and build were unaffected.
- Physical phones, sustained GPU performance, every spell variant, and a full
  networked match remain unverified. Historical tracked screenshots are older.
