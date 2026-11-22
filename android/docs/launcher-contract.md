CONTRACT v1 — Fangame Launcher <-> maker game APKs (built from the MKXP-Z Android base APK)

Context: makers build their game APK with La Base de Sky's tools/crear_release.rb from the game-free MKXP-Z base APK (repo mkxp, branch plan-android-port). Every maker APK therefore contains the same engine code; anything below is implemented ONCE in the base APK. The launcher (com.fangamelauncher.app, Tauri 2 + Kotlin, NOT open source, NOT GPL) must never link engine code: it talks to game APKs only via intents and ContentProviders. On Android, "Play" order: 1) maker APK if the game publishes one, 2) NaviaXP with the Windows build (unchanged). There is NO separate mkxpz-runtime app (discarded).

A. Launching a game APK (launcher -> game)
- Launcher starts the game's launch intent (PackageManager.getLaunchIntentForPackage(pkg)) adding extras:
  "com.fangamelauncher.extra.GAME_ID"    (String, launcher game id)
  "com.fangamelauncher.extra.SESSION_ID" (String, UUID)
- The game APK forwards them to its MkxpActivity (SplashActivity -> MkxpActivity).

B. Session events (game -> launcher), reuse launcher's existing provider contract v1
- Authority: content://com.fangamelauncher.app.naviaxp, ContentResolver.call(authority, method, null, extras)
- Methods "game_started" when the engine really starts, "game_closed" when the game ends (activity finishing / process ending), extras gameId + sessionId (same values received in A). Only sent when both extras were received. Failures (launcher missing, rejected) are ignored; the game keeps running.
- Game APK manifest declares <queries><package android:name="com.fangamelauncher.app"/></queries>.
- Launcher side: its provider today only accepts the package com.naviaxp; it must also accept calls from packages of maker APKs that the launcher installed (installed.json engine "apk"), verifying the caller's signing certificate SHA-256 equals the one recorded at install time.

C. Save provider in every game APK (launcher -> game)
- Authority: "<applicationId>.fangamelauncher" (manifest placeholder ${applicationId}.fangamelauncher), exported, class in the base APK.
- Caller check on EVERY entry point: Binder.getCallingUid() must belong to package "com.fangamelauncher.app" AND that package's signing certificate SHA-256 must be in an allow-list baked into the base APK (Gradle property mkxpz.launcherCertSha256, comma-separated, uppercase hex without colons). Anything else -> SecurityException.
- call("info") -> Bundle:
    apiVersion: Int = 1
    gameRunning: Boolean (MkxpActivity alive)
    sessionId: String (SESSION_ID of the launcher session the game runs under, "" when there is none)
    saveTitle: String (Title from game/Game.ini, "" if missing)
    versionName: String, versionCode: Long (of the game APK)
- openFile(content://<authority>/saves.zip, "r") -> streamed ZIP (via pipe) of the save scope (below), entry names relative to the save root, "/" separators, no directories-only entries required.
- openFile(content://<authority>/import.zip, "w") -> launcher writes a ZIP with the same layout into a staging file; then
  call("commit_import") -> Bundle{accepted: Boolean, error: String?}. Rejected (accepted=false, error="game_running") while the game runs. Validates every entry (no absolute paths, no "..", only inside the save scope, never touching excluded paths). Before replacing, moves the current save files to files/.launcher-backup/<timestamp>/ (keep the last 3). Replaces atomically as far as practical (extract to temp dir, then swap files). call("discard_import") deletes the staging file.
- openFile(content://<authority>/achievements.jsonl, "r") -> files/launcher-achievements.jsonl if present (FileNotFoundException otherwise).
- Save scope = everything under the app's filesDir EXCEPT: game/ (extracted game data), .launcher-backup/, controls_user*.json, gamepad_map.json, *.log, mkxp.log, errorlog.txt, launcher-achievements.jsonl, and any staging files of this provider. (Verified on device: World Frontier saves files/"Partida 1.rxdata"; La Base de Sky saves files/Game.rxdata. filesDir is the Android equivalent of the PC Essentials save folder %APPDATA%/<Title>.)

D. Launcher cloud format
- The launcher wraps the saves.zip entries under data/ inside its existing DesktopSnapshot format (.fangame-snapshot.json + data/), save_location essentials (title = saveTitle), so it syncs with the PC build of the same game, exactly like NaviaXP games today. Import does the reverse. Existing CloudSaveState / conflict / restore preview flow is reused; only the file source changes (provider instead of SAF).

E. Catalog / backend
- Release gets an "android" platform like windows/macos/linux (URL + sha256, per-language builds too).
- The backend analysis job downloads the APK and extracts packageName, versionCode, versionName, signing cert SHA-256 (APK Signature Scheme v2/v3 block; v1 fallback) and whether the APK exposes contract C (provider authority "<pkg>.fangamelauncher" present in the manifest -> supportsLauncherSaves).
- The first published android build pins packageName + cert SHA-256 on the Game; later builds with a different package or cert are flagged for review (not auto-published).
- The player API exposes for android builds: url, sha256, packageName, versionCode, certSha256, sizeBytes, supportsLauncherSaves.

F. Launcher install/update/uninstall (Android)
- REQUEST_INSTALL_PACKAGES; install with PackageInstaller, streaming the download straight into the session (session.openWrite with offset for resume), verifying sha256 before commit; after install verify installed package's cert SHA-256 == catalog certSha256.
- Check free space before starting (APK size + margin). Updates: same package, higher versionCode; if cert differs, warn that Android requires uninstall and saves would be lost unless synced.
- installed.json: engine "apk", packageName, versionCode, certSha256.
- Play: launch intent with extras of A. Uninstall: ACTION_DELETE.

---

# Engine-side implementation notes (as implemented in this repo)

Code: `app/src/main/java/io/github/mkxpz/android/` — `LauncherSession` (A, B), `LauncherSaveProvider` (C), hooks in `SplashActivity` / `MkxpActivity`; manifest `<queries>` and `<provider android:authorities="${applicationId}.fangamelauncher">`.

## A / B
- `SplashActivity` copies both extras to the `MkxpActivity` intent. `MkxpActivity.onNewIntent` (singleTask) swaps the session: `game_closed` for the old one, `game_started` for the new one.
- `game_started` is sent the first time the SDL engine thread exists (polled every 100 ms from the activity), i.e. when the engine has really started, not when the activity is created.
- `game_closed` is sent from `MkxpActivity.onDestroy` **after** `SDLActivity.onDestroy` returned (engine thread joined, saves written) and after `gameRunning` turned false, so the launcher can export/import right after it. If the process is killed (swipe from recents, `am force-stop`, task removal) no callback runs and `game_closed` is lost; the launcher must treat a vanished session as closed.
- Every event is sent **at most once per session** (`gameId` + `sessionId` are the key): ending the activity and swapping the session cannot close the same session twice.
- **Relaunch** (the launcher starts the game again while it is running): the new intent reaches `SplashActivity` (which copies the extras to `MkxpActivity`) and/or `MkxpActivity.onNewIntent`. With different extras the running game closes the old session (`game_closed`) and starts the new one (`game_started`, sent at once: the engine thread already exists). With the same extras, or with no extras at all, nothing changes. The game keeps running; only the session changes, so the launcher never keeps a stale session.
- The session the game runs under lives in `LauncherSession.active`; `info` reports its id as `sessionId`, so the launcher can reconcile the sessions whose `game_closed` was lost (killed process).
- Calls: `ContentResolver.call(content://com.fangamelauncher.app.naviaxp, "game_started"|"game_closed", null, {gameId, sessionId})` on a worker thread (the `game_closed` one is awaited at most 1.5 s). Exceptions are logged (`LauncherSession`) and ignored. Nothing is sent unless both extras are non-empty strings of at most 128 characters.

## C
- Caller check on every entry point (`call`, `openFile`, `query`, `getType`, `insert`, `update`, `delete`): `Binder.getCallingUid()` must map (`getPackagesForUid`) to `com.fangamelauncher.app`, and **every** APK signer of that package (`getApkContentsSigners`, API 28+; `GET_SIGNATURES` below) must have its SHA-256 in `BuildConfig.LAUNCHER_CERT_SHA256` (Gradle `mkxpz.launcherCertSha256`, comma separated, uppercase hex without colons; default in `android/gradle.properties` is the launcher's release cert `EEAA788F…BFC57E`). Otherwise `SecurityException`.
- Debug escape hatch: `-Pmkxpz.allowShellCaller=true` also accepts uid 2000 (shell) and 0 (root) and adds `debugLauncherAllowed` to `info`. Default `false`; forced `false` for the base APK (`-Pmkxpz.baseApk=true`). Never ship a game built with it.
- `info` -> `{apiVersion: 1, gameRunning, sessionId, saveTitle, versionName, versionCode (Long)}`. `saveTitle` is `Title=` of `[Game]` in `filesDir/game/Game.ini` (falls back to `assets/game/Game.ini`, then `""`). `sessionId` is the id of the launcher session the game runs under ("" when the game runs without one, or when it is not running).
- `saves.zip` ("r" only): deflated ZIP of the save scope, entries relative to `filesDir`, `/` separators, no directory entries, symlinks skipped, streamed through a pipe. Files are read as they are on disk (no snapshot): a save written by a running game can be captured half-way; check `gameRunning` first.
- `import.zip` ("w"/"wt" only) -> staging file `files/.launcher-import/import.zip`. `commit_import` -> `{accepted, error?}`:
  - `game_running`: a `MkxpActivity` exists (rejected before touching anything).
  - `no_import`: nothing staged. `empty_import`: archive has no entry in the save scope (never wipes saves with an empty archive).
  - `invalid_zip`: not a ZIP / Android's zip path validator refused a name. `invalid_entry`: name empty, absolute, containing `\`, NUL, or a `.`/`..`/empty segment, or duplicate.
  - `too_large`: more than 50 000 entries or more than 2 GiB uncompressed. `io_error`: any other I/O failure (state rolled back).
  - Entries that fall in the excluded set are **skipped silently** (logged), not an error: a PC snapshot with e.g. a `.log` file still imports. Everything else is extracted into `files/.launcher-import/extract/` first.
  - Swap: current save-scope files are renamed into `files/.launcher-backup/<yyyyMMdd-HHmmss-SSS>/`, empty leftover directories are pruned, extracted files are renamed into place. Any failure undoes both steps. The newest 3 backups are kept. Files in the save scope that are not in the archive end up only in the backup (the import replaces the scope). Success deletes the staging dir.
  - Not guarded: a game launched *during* `commit_import` (the launcher is expected not to start it).
- `discard_import` deletes `files/.launcher-import/` and returns `{accepted: true}`.
- `achievements.jsonl` ("r" only): `files/launcher-achievements.jsonl`, `FileNotFoundException` when absent.
- Save scope exclusions (`LauncherSaveProvider.isExcluded`, matched on any path depth unless stated): top-level `game/`, `.launcher-*` (backup + staging), `.game-install*` (installer temp dirs); names `controls_user*.json`, `gamepad_map.json*`, `*.log` (covers `mkxp.log`), `errorlog.txt`, `launcher-achievements.jsonl`.

## Authority / package rewrite
The authority is `${applicationId}.fangamelauncher`; the base APK has `io.github.mkxpz.base.fangamelauncher`, which La Base de Sky's `tools/android_apk.rb` rewrites like every string that starts with `io.github.mkxpz.base.` (checked on device and covered by `tests/cases/test_android_apk.rb`). The launcher cert allow-list and the launcher callback authority live in the dex (`BuildConfig` / `LauncherSession`), not in the manifest, so repackaging does not touch them. Changing the allow-list therefore needs a new base APK.
