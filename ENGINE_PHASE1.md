# GPU perspective phase 1

## Implemented

- Added `Viewport#perspective_set(segments, table)`, `perspective_clear`, `perspective?`, and `perspective_center_x=`. Each segment is `[y_start, y_step, count]`; the binding validates 1–4 Float segments, positive finite steps, approximate contiguity, counts totaling 64, and 192 finite Float table values. The native viewport keeps a value-only table and increments its generation on changes.
- Added the five Sprite perspective properties with no-op guards and defaults from the plan.
- Added a lazy 2.5D sprite shader. It samples up to four uniform segments, interpolates/extrapolates the 64 F/W/U samples, applies mode 1 floor projection and mode 2 standing projection, and reuses `sprite.frag` for tone, color, opacity, flash, bush, pattern and invert effects.
- Added the floor quad mesh with straight UV subdivisions, conservative visibility handling for perspective sprites, and a no-taper transform for the opt-in route.
- Added `tests/perspective/grid.rb` with a colored floor grid using four different sample steps and standing columns.
- Registered the vertex shader in Meson and the macOS Xcode asset list.
- Applied the Sonnet review fixes: explicit uniform names; viewport-local coordinates and anchor; disposed guards; table uniform caching by monotonic identity; dirty-only floor mesh uploads; shader compile failure fallback; mode/finite-value validation; nearest filtering; exact final mesh edge; direct vertex uniform indexing.
- Extended the grid with a `Viewport.new(40, 60, 300, 200)` and a nonzero `oy` case.

## Decisions and limits

- Kept the sample count at 64; segments use distinct uniform steps to capture the measured horizon/profile breaks.
- The perspective shader is allocated and compiled on first use. Legacy shaders and the legacy draw branch remain selected for mode 0 or an unset viewport table.
- Floor mode uses the existing wave subdivision when wave is active; its row count then follows the wave chunks rather than `perspective_slices`.
- Floor mesh still owns one lazily allocated VBO/VAO per sprite; it now uploads only when slices, source rect, mirror, bitmap, or hires backing changes. A shared mesh remains a later optimization.
- `perspective_slices` is clamped to 1–64 during mesh creation. Increase the cap only if measured within-tile curvature needs it.
- The prior committed version passed Linux and Windows builds. These review fixes are uncompiled and await the user's next CI run.

## Files

- `src/display/viewport.h/.cpp`, `binding/viewport-binding.cpp`
- `src/display/sprite.h/.cpp`, `binding/sprite-binding.cpp`
- `src/display/gl/shader.h/.cpp`, `src/display/gl/transform.h`
- `src/display/gl/gl-fun.h`, `src/sharedstate.cpp`
- `shader/sprite25d.vert`, `shader/meson.build`
- `macos/mkxp-z.xcodeproj/project.pbxproj`
- `tests/perspective/grid.rb`

## Manual verification

1. Run `tests/perspective/grid.rb`; check the floor curve, seams, standing columns, the displaced viewport, and the nonzero `oy` case.
2. Set mode 0 or call `perspective_clear`; compare the scene with the existing renderer.
3. Try opacity, tone/color/flash, bush depth, rotation, wave, and lift on perspective sprites.
4. Check invalid segment/table arguments raise before the viewport state changes.
5. Review the engine logs after the run.

## Build status

- Previous commit: Linux and Windows passed, per user.
- Review fixes: pending Linux and Windows CI.

## Risks / remaining work

- Runtime shader compilation fallback and rendered output still need manual validation in the engine.
- Partial viewports, high-resolution framebuffer scaling, snapshots, and extreme table ranges remain for later phases.
- Native plugins built against the old `Viewport`/`Sprite` layout need recompilation.
