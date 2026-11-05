Objective: migrate MKXP-Z from Ruby 3.1 to Ruby 4.0.9 following PLAN_RUBY4.md (Phases 1-4).
Branches: mkxp `feat/ruby4-migration`; game `feat/ruby4-compat`; Ruby fork local repo `/home/diego/Documents/GitHub/ruby-mkxp` branch `mkxp-z-4.0.x`.
Delivery: ask-on-risk. No test runner in mkxp; Ruby fork verified with a plain Ruby script; game changes verified manually by the user.

## Specs

- S1: "Podemos comenzar con la implementacion del plan?" — implement PLAN_RUBY4.md Phases 1-4. Phase 0 (profiling) and Phase 5 (measurement) need the game running and are done by the user.
- S2: "si necesitas hacer cambios en la base hazlos en una branch aparte" — game changes only on branch `feat/ruby4-compat` of `/home/diego/Documents/GitHub/La-Base-de-Sky`.
- S3: "delega el codigo complejo a agentes de Sonnet y el simple a agentes de Haiku, vos sos solo un orchestrator".
- S4: Ruby fork `mkxp-z-4.0.x` based on tag `v4.0.9` with every mkxp-z patch from PLAN_RUBY4.md ported, one commit per patch, plus a patch test passing with and without YJIT.
- S5: Build files point to a configurable fork (`RUBY_REPO`/`RUBY_BRANCH`), library names updated to 4.0, `--enable-yjit` on Linux/macOS only.
- S6: Binding: MJIT branch guarded for Ruby < 3.3; `YJITEnable` docs updated.
- S7: Game: `Dir.exists?` fixed; vendored `Set` only loaded on Ruby < 4.0; `Ruby Library 4.0.0` added (after S4 build).

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S4 | delegated (Sonnet) | done | ruby-mkxp 22c7a474..e13681b2 |
| T2 | S5 | delegated (Haiku) | done | 849fde33 |
| T3 | S6 | delegated (Haiku) | done | 679e007b |
| T4 | S7 (Dir.exists?, Set) | delegated (Haiku) | done | game 174bf6d4 |
| T5 | S7 (Ruby Library 4.0.0) | delegated (Haiku), after T1 | done | game (see log) |

Route evidence: user explicitly requested delegation (S3); T1-T4 have disjoint edit surfaces and run in parallel.

## Log

- L1 (2026-11-05): "Podemos comenzar con la implementacion del plan? si necesitas hacer cambios en la base hazlos en una branch aparte, delega el codigo complejo a agentes de Sonnet y el simple a agentes de Haiku, vos sos solo un orchestrator"
- L2: Phase 0 gate not run yet (requires the user to play); YJIT stays off by default until Phase 5. Pushing the fork to GitHub (`dpertierra/ruby`) needs the user's explicit OK.
- L3: T1 verified by parent: patches_test.rb prints "patches OK" on 4.0.9 with and without `--yjit --yjit-call-threshold=1`. YJIT exits to the interpreter for private/protected calls (yjit/src/codegen.rs ~9134-9156), so no YJIT patch. btest: 5/2044 failures, all expected (they assert the errors the patches remove). Correction e13681b2 matches the original `goto override_class` in rb_define_class. Windows/mingw patches not compiled (Linux-only build).
- L4: T5 verified on 4.0.9: `require "set"` => false (core), `json` and `tsort` load from `Ruby Library 4.0.0`. Fork created at github.com/dpertierra/ruby (user approved); push via SSH after unshallow.
- L5: Branch mkxp-z-4.0.x pushed to git@github.com:dpertierra/ruby.git (e13681b2). Pending (user): first real engine build with `make everything` + meson, Windows/macOS builds, Phase 0/5 profiling, manual game smoke test.
- L6 (2026-11-05, user test on Pokémon World Frontier v1.2.1, Linux): game boots, saves from 3.1 load/save, gameplay OK on Ruby 4.0.9. Game needed `"preloadScript": ["preload.rb"]` in mkxp.json and `MKXP.puts` -> `System.puts` in preload.rb (backups *.bak in game dir). User: "Noto mejora con el modo 2.5D, sin el YJIT dropeaba incluso a 30FPS con el YJIT está estable a 60".
