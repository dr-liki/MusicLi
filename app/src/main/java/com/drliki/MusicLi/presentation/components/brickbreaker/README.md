# BrickBreaker Component

## Overview
Provides the interactive Brick Breaker easter-egg game overlay (`com.drliki.MusicLi.presentation.components.brickbreaker`). It renders a Material 3 Compose physics-driven brick-breaking minigame with level progression, particle effects, haptic feedback, high-score persistence, and a random-playback CTA when the mini-player is hidden.

## Interface
- `[BrickBreakerOverlay.kt:BrickType]` - Public enum class defining brick durability tiers (`Normal`, `Hard`, `Solid`).
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - Public `@Composable` entry point `fun BrickBreakerOverlay(modifier: Modifier = Modifier, isMiniPlayerVisible: Boolean = false, onPlayRandom: () -> Unit, onClose: () -> Unit)`.

## Invariants
- `[BrickBreakerOverlay.kt:FixedPhysicsStepSeconds]` - Fixed physics step is `1f / 180f` seconds, capped at 8 steps per frame (`[BrickBreakerOverlay.kt:MaxPhysicsStepsPerFrame]`), with frame `deltaTime` clamped to `[0f, 0.05f]`, `physicsAccumulator` capped at `0.2f` (reset to `0f` when `!ballLaunched`), and up to 8 collision sub-steps per physics step sized by `max(ballRadius * 0.45f, 1f)`.
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - Full game reset initializes `score = 0`, `lives = 3`, `level = 1`, `hasGameStarted = false`, `isGameOver = false`, `hasWon = false`, and `ballLaunched = false`.
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - Level scaling sets `currentSpeedMult = 1f + (lvl - 1) * 0.1f` (base ball velocity `800f`), paddle width factor `(0.25f - (lvl * 0.02f)).coerceAtLeast(0.10f)`, `rows = min(5 + lvl, 10)`, `cols = 8` mirrored across `cols / 2`, and `brickWidth >= 10f`; `BrickType.Solid` spawns only when `lvl > 2 && row > 1`, and `BrickType.Hard` starts with 2 hits vs. 1 for `BrickType.Normal`.
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - `paddleX` is clamped to `[0f, areaSize.width - paddleWidthPx]` (or centered `>= 0f`).
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - Wall collisions clamp `ballPosition` within left/right/top bounds and invert velocity; exiting below `areaSize.height` decrements `lives` by 1, sets `ballLaunched = false`, sets `isGameOver = true` if `lives <= 0`, and re-attaches the ball to the paddle.
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - Paddle reflection triggers only when `nextVelocity.y > 0f`, clamps `hitPoint` to `[-1f, 1f]`, scales speed by `1.015f` within `[max(currentSpeed, baseBallVelocity * 0.85f), baseBallVelocity * 3f]`, and bounds `|vx| <= 0.82f * newSpeed` and `vy <= -0.35f * newSpeed`.
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - At most one brick collision resolves per fixed physics step (`brickResolvedThisStep`); `BrickType.Solid` bricks are indestructible; destroying `Normal`/`Hard` bricks awards 50/100 points, scales velocity by `1.01f`, and sets `hasWon = true` and `ballLaunched = false` when no non-`Solid` bricks remain with `hitsRemaining > 0`.
- `[BrickBreakerOverlay.kt:enforceMinimumVerticalSpeed]` - Preserves ball speed magnitude while enforcing `|velocity.y| >= speed * ratio` (default `0.22f`) when `speed > 0f`.
- `[BrickBreakerOverlay.kt:reflectVelocity]` - Returns input velocity unchanged if normal length `<= 0.0001f` (`[BrickBreakerOverlay.kt:normalizedOrNull]`) or `dot(velocity, normalizedNormal) >= 0f`.
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - High score updates are monotonically non-decreasing (`value > highScore`).

## Side Effects
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - Reads and writes `SharedPreferences` (`"pixelplayer_easter_egg"`, key `"brick_breaker_high_score"`) via `edit().putInt(...).apply()` when `score` exceeds `highScore`.
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - Triggers haptic feedback via `view.performAppHapticFeedback(appHapticsConfig, ...)` using `KEYBOARD_TAP` (paddle hit), `CLOCK_TICK` (brick hit), and `LONG_PRESS` (brick destruction).
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - Runs `LaunchedEffect` frame loops via `withFrameNanos` and `LaunchedEffect(areaSize)` blocks mutating Compose game state (`particles`, `bricks`, `ballPosition`, `ballVelocity`, `physicsAccumulator`, `score`, `highScore`, `lives`, `ballLaunched`, `hasWon`, `isGameOver`, `paddleWidthPx`, `paddleX`).
- `[BrickBreakerOverlay.kt:BrickBreakerOverlay]` - Consumes Canvas drag pointer events and invokes external callbacks `onClose()` and `onPlayRandom()` (when `!isMiniPlayerVisible`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
