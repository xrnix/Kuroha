# Kuroha — OpenCode Agent Rules

## Build & Verification
- Always build with `JAVA_HOME=/opt/android-studio/jbr` and `--max-workers=2` so memory stays bounded.
- Fast compile check after edits (compact output, errors only):
  ```bash
  ./scripts/opencode-verify.sh
  ```
- Full APK build check (when finishing a screen):
  ```bash
  ./scripts/opencode-verify.sh --build
  ```
- Do NOT commit changes unless explicitly asked.

## Architecture & Compose Porting Boundaries
- **Migrate to Compose**: List screens, Feeds, History, Library/Favourites, Bookmarks, Explore, Details, Downloads, Dialogs, and BottomSheets.
- **Keep in Views (Do NOT rewrite in Compose)**: The Reader image/canvas surfaces (`PagerReaderFragment`, `WebtoonReaderFragment`, `DoubleReaderFragment`, `WebtoonRecyclerView`, `DoublePageScalingFrame`, `SubsamplingScaleImageView`). Reader sheets/overlays (`ReaderConfigSheet`) ARE Compose.
- **Theme**: Always wrap Compose UI in `KurohaTheme { ... }` (`org.koitharu.kotatsu.settings.compose.KurohaTheme`).
- **Material 3 only**: Use `androidx.compose.material3.*`. Never use Material 2 (`androidx.compose.material.*`).
- **Images**: Use `coil3.compose.AsyncImage`.
- **State**: Expose `StateFlow` from `ViewModel` and collect in Compose; keep Hilt DI, Room, and `AppRouter` navigation intact.
- **No Guessing**: Never invent helper functions, colors, or `R.string.*` / `R.drawable.*` names. Verify exact names with `rg` or in `docs/compose-port/COMPOSE_REFERENCE.md` before using them.
- **Cleanup**: Only remove unused XML layouts/adapters after `./scripts/opencode-verify.sh` passes with 0 errors.
