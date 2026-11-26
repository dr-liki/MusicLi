# Shapes (`utils/shapes`)

## Overview
Custom Jetpack Compose `Shape` implementations used by UI components in MusicLi to render stylized geometric outlines.

## Interface
- [`RoundedStarShape`](RoundedStarShape.kt): `RoundedStarShape(sides: Int, curve: Double = 0.09, rotation: Float = 0f, iterations: Int = 360) : Shape` (`RoundedStarShape.kt:RoundedStarShape`) — generates a star-shaped `Outline.Generic` path using polar coordinates sampled across `2 * PI`, with an inner helper `createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline` (`RoundedStarShape.kt:createOutline`).

## Invariants
- Bounding envelope is square-constrained by `min(size.height, size.width)` so the star does not stretch asymmetrically when placed inside non-square bounds (`RoundedStarShape.kt:createOutline`).
- Sampling resolution is capped at `360` iterations via `min(iterations, 360)` to bound path vertex count (`RoundedStarShape.kt:RoundedStarShape`).
- `curve` maps from `[1.0, 0.0]` onto `[0.5, 1.0]` when scaling the base radius `r` (`RoundedStarShape.kt:createOutline`, `RoundedStarShape.kt:mapRange`).

## Side Effects
- None.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
