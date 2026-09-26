# Changelog

All notable changes to HiLight Studio are documented here.

## 1.0.15-rc.1 — 2026-09-20

- Release candidate for GitHub, version code 17; not a confirmed fix for the KernelSU reports.
- Root cleanup uses Android’s platform shell explicitly, retaining exact process checks and cleanup deadlines. The faster process scan from the targeted test is retained.
- Copy LED diagnostics now includes fixed root startup/failure fields and handoff state, without raw logs or notification content.
- Setup shows retry guidance and the previous error when root is available but the renderer is disconnected.
- Existing rules, preferences and permission requirements are unchanged. Affected-device confirmation is still required before general rollout.

## [1.0.14-experimental] - 2026-09-18

- Fixed command-output and stdin handling that could stall root startup; startup errors now identify the failed stage. The reported KernelSU case still needs affected-device confirmation.
- Added selectable 1–3 second pending-notification reminders with safe minimum gaps and overdue reconciliation when Android wakes the app. Deep sleep can still delay reminders.
- Added private rule export/import with validation, duplicate detection and conflict protection.
- Added optional app-icon colors for notification rules, with background extraction and manual-color fallback.
- Added expandable groups, independent identities and ordering controls for multiple rules per app. Existing rule cards may display in storage/priority order rather than the previous automatic visual sort.
- Added an optional static two-second battery-level gauge on plug-in and at the configured charged threshold. Existing light restrictions still apply.
- Retained all renderer safety limits. Recording-only camera detection remains unavailable. Physical LED behavior is not proven by emulator tests.

## [1.0.13-experimental] - 2026-09-12

- Fixed two root-recovery blockers: a departed renderer's PID being reused by an unrelated process,
  and AUTO remaining on an ADB fallback after the root renderer has safely exited and root is available
  again. Exact process ownership and successor cleanup remain required. The overnight Magisk report
  still needs confirmation on the affected phone.
- Added an optional **Notify when Shizuku stops** setting. After a previously working connection is
  lost, a notification opens Setup. An opted-in user can also receive a recovery notice after reboot;
  this does not start a renderer or request root permission at boot. Notification permission is required.
- Added Meter, Strobe, Heartbeat, Bounce, Radar, Converge and Glitch animations, adapted from
  [PR #39](https://github.com/DhananjayBhosale/hilight-studio/pull/39) by @saboooor. Existing duration,
  brightness, rest and cleanup limits still apply. Renderer implementation revision is now 8.
- Preserved existing rules, saved looks, quiet hours and notification behavior. See the
  [September 12 feedback audit](docs/FEEDBACK-2026-09-12.md) for reviewed requests and remaining limits.

## [1.0.12-experimental] - 2026-09-07

- Fixed helper heartbeat scheduling and freshness across wall-clock corrections, and added safe
  retry for a disconnected root renderer. Renderer implementation revision is now 7.
- Fixed automatic Shizuku routing after replacing an older renderer during an app update.
- Added complete saved presets and gradient endpoints to app rules, preserving existing rule looks.
- Added silent-notification filtering and app exclusions for the Any app rule.
- Added optional brief reminders for pending notifications, with dismissal/unlock cleanup and
  existing quiet/battery guards. Device sleep may delay reminders.
- Added optional day-specific quiet hours while retaining the existing daily schedule.
- Added optional charging indicators and DND activation signals, plus incoming-call indication for
  apps that supply Android's explicit incoming-call notification marker.
- Made delayed notification tests report known blockers before their countdown, including DND.
- Clarified that camera/microphone activity does not identify video recording. The one-minute
  activity limit remains unchanged. See [the complete feedback audit](docs/FEEDBACK-2026-09.md).

## [1.0.11-experimental] - 2026-09-02

- Kept an ordinary notification alert running when the notification itself wakes the screen, while
  still stopping it on unlock and still stopping per-rule **Only while the screen is off** alerts as
  soon as the screen wakes. This addresses the lifecycle sequence reported for Discord's first alert
  and was confirmed working by the reporter on September 6.
- Added a clear message when an in-app preview or end-to-end notification test is currently blocked
  by quiet hours, Battery Saver, low battery, or the applicable face-down guard.
- Split the former **When to stay dark** settings into **When HiLight can glow** and **When HiLight
  should pause**, with each live suppression reason shown beside the section that controls it.
- Added a compact Dhananjay Tech attribution link at the top of Settings.

## [1.0.10-experimental] - 2026-08-29

- Bumped the privileged renderer implementation revision to 5 so Shizuku, ADB, and root restart an
  older revision instead of silently reusing the released v1.0.9 renderer.
- Made cleanup readback treat nonzero RGB as shadowing, RGB-dark but inexact ARGB as Binder-accepted
  and unverified, and only an exact ARGB match as framework-effective. This changes diagnostics only;
  LED writes, cleanup attempts, session release, and timing remain unchanged.
- Ignored late state deliveries after renderer shutdown so they cannot mutate release revisions,
  re-arm cleanup without a render thread, or restart the privacy observer.
- Made the app picker include installed packages remembered from named notifications, even when they
  have no launcher activity. Forgetting remembered chats removes these extra picker entries.
- Made notification and while-open rules discoverably coexist for the same app, and added a safe
  **Copy settings to another app** action for whole-app rules without carrying chat or keyword data.
- Added exact typed entry to time-slider value pills. Typed seconds reject non-finite values and are
  clamped to the slider's currently unlocked range, so duration safety confirmations still apply.
- Added a five-second delayed self-test notification so the screen can be locked before it posts.
- Added **Only while the phone is face down** as both a global guard and an option on individual
  notification rules. The first use requires a shared **Needs testing — use with care** acknowledgement.
  A foreground service watches the gravity/accelerometer signal with hysteresis and a settling window;
  missing, stale, or unavailable readings fail closed and leave the LED array fully off. Lifting the
  phone cancels a gated alert through the normal renderer release path.

## [1.0.9-experimental] - 2026-08-29

- Added a bounded stuck-LED mitigation. After the existing pre-release black sequence, the renderer
  closes its normal session and repeats alpha-black → canonical black through three fresh,
  low-priority sessions. The same three-pass recovery runs before a new renderer reports ready.
- Added a hard renderer compatibility fence: the 1.0.9 candidate uses renderer revision 4, status
  schema 6, and Shizuku service version 1004. Visible/current state is never replayed to an unknown
  or mismatched daemon. A minimal disabled state may be sent to hold output dark while Shizuku removes
  and rebinds a mismatch once, then fails closed if ownership cannot be resolved.
- Split state receipt, render-thread settlement, and full release into separate revisions. Handoffs
  now wait for a closed session and terminal cleanup, while ADB and root helpers use cooperative
  shutdown cleanup before their process exits.
- Required every ADB/root helper launch to carry a valid explicit instance ID and added a singleton
  process lock. After the old process or binder is proven gone, the app makes the new renderer finish
  one disabled cleanup request before replaying desired output. A newly observed bridge instance is
  retargeted and receives the saved state once with `arm=false`, so a restarted helper does not stay
  dark or restart the auto-off clock.
- Added truthful cleanup outcomes for Binder acceptance, framework readback, shadowing, I/O failure,
  and exhaustion. **Copy LED diagnostics** exports only allowlisted lifecycle data, and **Retry LED
  cleanup** runs the black-only three-pass recovery while HiLight is off and idle.
- Enforced the five-minute ambient ceiling in the renderer itself, including raw bridge documents.
- Kept temporary dark frames inside an animation separate from terminal shutdown: a Breathe trough
  closes only the normal visible session, while expiry, master-off, source changes, and a configuration
  that never produced visible output run the full recovery cycle once.
- Implemented Android 17 QPR2 native flashlight UI with dynamic expanding light-beam geometry.
- Added Material 3 Color Spectrum Wheel with live color preview disc and elevated thumb selector.
- Introduced compound Quick Settings tile with angled flashlight torch and adjacent Material color wheel.
- Elevated default ambient and torch power to full 100% saturation across all 8 LEDs / 24 RGB dies.
- Extended safety taper limits to maintain full continuous drive current during flashlight mode.
- Added Material You dynamic wallpaper theme integration to the dialog container shell.
- Updated GitHub release workflow using Blacksmith runners.

## [1.0.8-experimental] - 2026-08-25

- Released the light session whenever a rendered frame is dark, so an idle or temporarily dark
  HiLight effect no longer masks Android's own effects such as Gemini lighting.
- Restored saved **While open** rules when Android reconnects HiLight after a reboot or the app
  process returns, and replaced the ten-second event snapshot with retained activity lifecycle
  tracking. Already-open apps and temporary system overlays are now handled reliably. Usage access
  remains required.
- Forced a second hardware-level black update before releasing an animated alert, preventing the
  Pixel 11 light driver from leaving one LED latched in the alert's last colour.
- Cleared and released privacy-activity output as soon as microphone or camera use ends, including
  when the array was already idle before that activity started.

## [1.0.7-experimental] - 2026-08-23

- Enabled reproducible, developer-signed builds for the initial F-Droid submission. This release
  contains no app behavior changes from 1.0.6.

## [1.0.6-experimental] - 2026-08-23

- Added a manual **Check for updates** action under Setup. It includes experimental GitHub
  prereleases, reports whether the installed version is current, and opens the matching release page
  when an update is available. It never checks in the background.
- Added **privacy activity rules** for microphone and camera use. A rule can cover any app or one
  selected app, and applies when the camera or microphone is in use.
