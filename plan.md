# MotionX — AR body-motion games (Android / Kotlin)

Package: `com.nightwise.motionx` · Min SDK 24 · Jetpack Compose UI · OpenGL ES 3.0 game rendering · MediaPipe Pose Landmarker body tracking.

First game: **Neon Dash** — a 3-lane endless runner you control with your whole body
(hop left/right = change lane, jump = jump, squat/duck = slide). Later games (Pose Match,
Bubble Pop, …) plug into the same tracking engine and app shell.

## Design direction

The reference screenshots define the *mechanics* (3 lanes, jump line, duck line, hurdles/bars/walls,
coins/stars, speed dial, constant vs increasing speed, presets, training run, tutorial).
The visual design is our own, following 2026 mobile trends for dark, high-contrast,
motion-heavy UIs:

- **Dark "night city" base** (#070912 → #12162B) with soft animated aurora orbs (violet / cyan / magenta) behind content.
- **Neon accents**: cyan `#22E1FF`, magenta `#FF3EA5`, lime `#B6FF3B`, amber `#FFC23D`. Each obstacle keeps a single strong colour so it can be read at a glance from 2–3 m away.
- **Glass used sparingly**: only for overlays (HUD chips, tracking panel, dialogs) — never stacked.
- **Big type, chunky shapes**: the player stands 2–3 m from the phone, so in-game text is large and high contrast.
- **Motion as feedback**: animated buttons, pulsing lane indicators, particle bursts on pickups, screen shake + red flash on hits, lit-up JUMP / DUCK chips the instant the body move is recognised.

## Screen layout during play (landscape)

```
┌──────────────────────────────────────────────┬──────────────────┐
│ ♥♥♥   SCORE 12 340   ● 54   SPD 6.2    ⏸     │  LIVE TRACKING   │
│                                              │ ┌──────────────┐ │
│          3D runner (OpenGL ES)               │ │ camera frame │ │
│     neon track · 3 lanes · obstacles         │ │ + skeleton   │ │
│                                              │ │ + L|C|R zones│ │
│                                              │ │ + jump/duck  │ │
│                                              │ └──────────────┘ │
│                                              │ [L][C][R] JUMP DUCK│
└──────────────────────────────────────────────┴──────────────────┘
          ~68%                                         ~32%
```

## Architecture

```
com.nightwise.motionx
├── MainActivity            single activity, Compose, orientation per screen
├── ui/theme                colours, type, shapes (MotionX design system)
├── ui/components           GlowButton, GlassCard, AuroraBackground, chips, SpeedDial …
├── ui/screens              Home hub, Runner lobby, Difficulty, How-to-play, Game, Results
├── tracking                CameraX + MediaPipe PoseLandmarker (LIVE_STREAM), OneEuro filter,
│                           PoseFrame, BodyController (calibration + lane/jump/duck detection),
│                           SkeletonOverlay (draws camera frame + skeleton + zones)
├── games/runner            RunnerGame (pure Kotlin game logic), RunnerConfig (presets),
│                           RunnerRenderer (GLES 3.0), meshes, shaders
├── gl                      tiny GL toolkit: Mesh, ShaderProgram, MeshBuilder, Mat4 helpers
├── audio                   synthesized SFX (no asset files)
└── data                    SharedPreferences store: best score, totals, settings
```

Tracking runs on a background executor; the game loop runs on the GL thread; UI state flows via `StateFlow`.

## Phases

### Phase 0 — Project setup ✅
- Rename package/applicationId to `com.nightwise.motionx`, app name "MotionX".
- Add CameraX, MediaPipe tasks-vision; camera permission; `configChanges` so rotation never recreates the activity.
- Bundle `pose_landmarker_full.task` in `app/src/main/assets`.
- Update `app.py` runner to the new package.

### Phase 1 — Design system + app shell ✅
- MotionX theme (colours, typography, shapes), aurora background, glass cards, glow buttons.
- Home hub: hero card for Neon Dash + "coming soon" cards for Pose Match & Bubble Pop.
- Simple state-based navigation with back handling.

### Phase 2 — Body tracking engine ✅
- CameraX front camera → `ImageAnalysis` (640×480, keep-latest) → MediaPipe PoseLandmarker LIVE_STREAM (GPU, CPU fallback).
- Frame shown in the panel is the *same* bitmap the landmarks came from → skeleton always lines up.
- One Euro filter on key points (low jitter at rest, low lag when moving fast).
- Calibration: stand in the centre with full body visible → baseline hip-x, shoulder width, head height, torso length.
- Lane = hip centre vs calibrated zones (with hysteresis). Jump = head crosses JUMP line and hips rise. Duck = head drops below DUCK line. Baseline slowly adapts while standing still (handles drifting closer/farther).
- Tracking-lost detection → auto-pause.
- Touch/keyboard fallback (swipes, arrow keys) for emulator testing.

### Phase 3 — 3D renderer ✅
- GLES 3.0 renderer, 4× MSAA when available, gradient sky, exponential fog, hemisphere + rim lighting, emissive neon materials.
- Procedural meshes: boxes, cylinders, spheres, star prism. Scrolling neon track, lane dividers, side pylons + skyline blocks for speed.
- Stylised robot runner with run cycle, jump arc, slide pose, lane-change lean.
- Obstacles: yellow hurdle (jump), blue bar (duck), red wall (change lane); coins & stars spin and bob.
- Particle bursts, hit flash, camera shake.

### Phase 4 — Runner gameplay ✅
- Pure Kotlin `RunnerGame`: spawning patterns that are always solvable, reaction-time-aware spacing, collisions, lives, invulnerability frames, score/coins/stars/distance, speed curve (constant / increasing), allowed-move filters.
- Presets: Kids, Easy, Normal, Hard, Athlete + custom speed dial 1–10.

### Phase 5 — Game screen ✅
- Landscape split: 3D game + live tracking panel, HUD, calibration flow, 3-2-1 countdown, pause, game over results.

### Phase 6 — Lobby, tutorial, persistence ✅
- Runner lobby: best run, current setup, PLAY, Difficulty, How to play, Training run, lifetime totals.
- Difficulty sheet: presets, speed dial, constant/increasing, lives, moves.
- How-to-play pager with animated skeleton illustrations.
- Training run = constant speed, infinite lives, no score saved.

### Phase 6b — Playtest feedback ✅
- Difficulty reworked into **Easy / Medium / Hard** (slower speed scale, 1.6–3.2 s between obstacles, 5/4/3 lives).
- **Voice assist** toggle: loud-speaker callouts ("Hands up, jump", "Duck", "Move left/right") + big on-screen arrow before every obstacle; toggle in Difficulty and the pause menu.
- Camera panel is **full height** (head to feet), sides cropped around the centre lane; L/C/R + jump/duck indicators moved onto the game view.
- **Whole app runs in landscape**, menus redesigned as two-column layouts.
- **Jump needs both hands raised** above the shoulders + a body rise; sideways hops (which also lift head/hips) can no longer trigger a jump. Extra guard right after a lane change.
- GPU→CPU automatic fallback when the MediaPipe GPU delegate fails at runtime.

### Phase 7 — Polish (next)
- Synthesized SFX + haptics, voice-style cues ("Jump!") via TextToSpeech option.
- Performance pass (frame pacing, GC-free render loop), low-end device mode (lite model).
- Accessibility: reduced motion, colour-blind safe obstacle shapes (already shape-coded).

### Phase 8 — More games (future)
- `MotionGame` interface so new games reuse tracking + shell.
- Pose Match (30 levels, hold pose, joint-angle similarity scoring).
- Bubble Pop (hands/feet hit-testing against floating bubbles, bombs to dodge).
- Workout stats, streaks, achievements.
