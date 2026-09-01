# Changelog

All notable changes to HiLight Studio are documented here.

## [Unreleased]

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
