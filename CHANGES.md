# Changes in this build (not yet compiled — please build and test)

## Bug fixes
0. **Download only started after opening the app (root cause #2)** — `DownloaderV2.kt`
   The scheduler observes a `SnapshotStateMap` through `snapshotFlow`. Compose only delivers
   those changes while a composition exists. After a share the sheet activity finishes, so the
   info fetch ran but the next step (start the download) was never triggered until the app UI
   was opened. Every write to the state map now calls `Snapshot.sendApplyNotifications()`
   (`notifyStateChanged()`), so downloads run with no UI at all.
1. **Share-from-Facebook freeze (root cause)** — `download/DownloaderV2.kt`
   `fetchInfo()` / `download()` launched their coroutine eagerly and set the task state
   (`FetchingInfo` / `Running`) only afterwards in `.also {}`. When the IO thread ran first,
   the body's `if (downloadState !is FetchingInfo) return@launch` guard saw `Idle` and exited,
   leaving a task stuck in "fetching info" with a dead job: notification shown, nothing running,
   no video data. Fixed with `CoroutineStart.LAZY` (state is set first, then `job.start()`).
2. Ghost tasks could not be cancelled (`destroyProcessById` returned false -> nothing happened).
   `cancelImpl()` now always cancels the job and moves the task to Canceled.
3. Tasks now wait (max 2 min) for `YoutubeDL.init()` (`App.ytdlpReady`) and for any running
   yt-dlp self-update (`UpdateUtil.awaitUpdateFinished()`) before starting yt-dlp.
4. Task state read-modify-write setters are synchronized (progress thread vs UI/scheduler).

## New welcome screen (replaces ModeSelectionDialog + old hero/badge)
- `ui/page/WelcomeScreen.kt` (new): logo on top, chibi Alisha + welcome message, app explanation,
  mode choice (nothing pre-selected), permission rows. Enters the app automatically once a mode
  is chosen AND all permissions are granted. "Continue without permissions" appears after a mode is chosen.
- `OnboardingDesign.kt`: navy + gold palette from the logo, new logo badge; also used by Settings > Permissions.
- Deleted: `ModeSelectionDialog.kt`, `onboarding_badge.jpg`, `onboarding_hero.jpg`.
- Added: `drawable-nodpi/magen_logo_mark.webp` (from your 570505.png), 12 new strings (EN + AR).
