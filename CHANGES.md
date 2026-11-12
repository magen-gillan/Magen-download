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

5. Custom-command tasks had the same eager-launch race (a fast command could finish, then
   "Running" overwrote its final state, leaving it stuck). Now LAZY-started too.
6. The legacy `Downloader` and `DownloaderV2` each stopped the shared foreground service when
   *they* became idle, even if the other was still working. Each now checks the other first.
7. The queue backup serialised the whole queue (all VideoInfo + formats) on every progress tick;
   now sampled to once per 2 s.

## New welcome screen (replaces ModeSelectionDialog + old hero/badge)
- `ui/page/WelcomeScreen.kt` (new): logo on top, chibi Alisha + welcome message, app explanation,
  mode choice (nothing pre-selected), permission rows. Enters the app automatically once a mode
  is chosen AND all permissions are granted. "Continue without permissions" appears after a mode is chosen.
- `OnboardingDesign.kt`: navy + gold palette from the logo, new logo badge; also used by Settings > Permissions.
- Deleted: `ModeSelectionDialog.kt`, `onboarding_badge.jpg`, `onboarding_hero.jpg`.
- Added: `drawable-nodpi/magen_logo_mark.webp` (from your 570505.png), 12 new strings (EN + AR).

## Build 4 (diagnostics for the "stalls until the app is opened" report)
- `DownloadService`: partial WakeLock (6 h cap, released in onDestroy) + `WAKE_LOCK` permission.
- `util/DiagLog.kt` (new): writes task state transitions, first yt-dlp output lines, failures and
  UI started/stopped events with timestamps to `Download/Magen download/diag.log`.

## Build 5 — single share target
- `QuickDownloadActivity` no longer has intent filters and is not exported: the share sheet now
  lists ONE entry (the one that opens the app, `MainActivity`).
- A link shared before the welcome screen is finished is kept (`PendingShare`) and its download
  sheet opens when the welcome screen completes, instead of being dropped.
