---
name: android-ui-craft
description: Android UI/UX & Motion Engineering Specialist. Deep expertise in Jetpack Compose, Material 3 / Material You dynamic theming, custom Android Views and ViewGroup layout measurement, 120Hz smooth animations, gesture detectors, Canvas 2D matrix transformations, Coil/Glide image loading pipelines, and edge-to-edge window insets. Use when designing, building, optimizing, or debugging Android user interfaces, custom reader canvases, overlays, or micro-interactions.
---

# Android UI Craft & Interaction Engineering

Guidelines for building pixel-perfect, 120Hz-fluid, touch-ergonomic Android interfaces following modern Material You and Apple-grade micro-interaction standards.

## 1. Edge-to-Edge & System Chrome Integration
- **Window Insets Invariants**:
  - Always enable edge-to-edge layout (`enableEdgeToEdge()` in Activities).
  - Consume and propagate `WindowInsetsCompat` cleanly using `ViewCompat.setOnApplyWindowInsetsListener` or Compose `WindowInsets.systemBars`, `ime`, and `displayCutout`.
  - Distinguish between **padding** insets (for touchable controls, toolbars, and floating docks) and **drawing** insets (allowing wallpapers, scrollable canvases, and images to bleed seamlessly behind translucent bars).
  - In reader or immersive views, use `WindowInsetsControllerCompat` to toggle `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` without jarring layout jumps.

## 2. Touch Ergonomics & Custom View Canvas Rendering
- **Custom Canvas Architecture**:
  - For image viewers, manga readers, or translation overlays: never allocate objects (e.g., `Paint()`, `RectF()`, `Path()`, `Matrix()`) inside `onDraw()` or `draw()` passes. Allocate once in initialization or size change callbacks.
  - Implement double buffering or hardware-accelerated layers (`setLayerType(LAYER_TYPE_HARDWARE, ...)`) where appropriate.
  - Use `Matrix` transforms (scale, translate, rotate) combined with `GestureDetector` and `ScaleGestureDetector` for pinch-to-zoom with pivot-point preservation.
- **Touch Targets & Gestures**:
  - Minimum touch target is 48x48dp with sensible touch slop.
  - Support fluid interruptible gesture physics: allow user touches to cancel mid-flight animations immediately without UI freeze.

## 3. Jetpack Compose & View Hybrid Interoperability
- **Compose Inside Views**:
  - Wrap Compose roots in `ComposeView` with `setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)` to prevent memory leaks in Fragments and RecyclerViews.
- **Views Inside Compose**:
  - Use `AndroidView` for high-performance legacy custom views (like complex surface views, pinch-to-zoom image decoders, or translation overlays).
  - Pass state down via `update` lambda; emit events up via callbacks.
- **Recomposition Optimization**:
  - Use `@Immutable` / `@Stable` annotations on domain models used in Compose.
  - Avoid passing unstable Lambdas; use function references (`::onAction`) or remember closures.
  - Use `derivedStateOf` when observing rapidly changing states (e.g., scroll offsets) to prevent re-evaluating the entire composition tree every frame.

## 4. Material You & Dynamic Theming
- **Color Roles**:
  - Support Dynamic Color (`dynamicColorEnabled`) on Android 12+ (API 31+) while providing curated fallback palettes for older OS versions.
  - Always provide an **OLED Pure Black** mode option for reader apps (`#000000` background with subdued surface colors to save battery and reduce eye fatigue).
  - Ensure WCAG AA contrast (minimum 4.5:1 for body text, 3:1 for large display text and key glyphs).
- **Typography & Font Scaling**:
  - Always respect system font scale and display density settings: use `sp` for text and `dp` for dimensions.
  - Handle large accessibility font scales gracefully without text truncation or clipping in buttons and headers.

## 5. Image Loading Pipelines (Coil & Bitmaps)
- **Memory Management**:
  - Always downsample bitmaps to view target dimensions using `Precision.INEXACT` or explicit `size(Size.ORIGINAL)` only when zooming.
  - Configure disk and memory LRU caches based on device available RAM (`ActivityManager.getMemoryClass()`).
  - Use `Bitmap.Config.HARDWARE` for static display to save CPU memory, falling back to `ARGB_8888` only when pixel manipulation, OCR, or canvas mutation is needed.
