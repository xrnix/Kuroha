# OpenCode Compose Porting Guide & Prompt Template

## Recommended Migration Order (One Screen Per Session)
1. **Simple / Self-contained**:
   - `history/ui/HistoryListFragment.kt`
   - `tracker/ui/updates/UpdatesFragment.kt`
   - `bookmarks/ui/AllBookmarksFragment.kt`
2. **Medium**:
   - `tracker/ui/feed/FeedFragment.kt`
   - `download/ui/list/DownloadsActivity.kt`
   - `search/ui/MangaSuggestionsProvider` / `SearchActivity.kt`
3. **Complex**:
   - `favourites/ui/list/FavouritesListFragment.kt` + `FavouritesContainerFragment.kt`
   - `explore/ui/ExploreFragment.kt`
   - `details/ui/pager/...` (keep `DetailsExpressiveActivity` structure, migrate sub-tabs)

> Do NOT port `reader/ui/pager/*` (`PagerReaderFragment`, `WebtoonReaderFragment`, `DoubleReaderFragment`) to Compose.

---

## Per-Screen Prompt Template (Copy & Paste into OpenCode)

```text
Port `<SCREEN_NAME>` to Jetpack Compose.

Context files to read ONLY:
1. `docs/compose-port/COMPOSE_REFERENCE.md`
2. `<FRAGMENT_OR_ACTIVITY_PATH>`
3. `<VIEWMODEL_PATH>`
4. `<XML_LAYOUT_PATH>` (if applicable)

Rules:
- Keep all existing ViewModel logic, Hilt injection, and AppRouter navigation intact.
- Host Compose inside the existing Fragment via `ComposeView` (`DisposeOnViewTreeLifecycleDestroyed`) + `KurohaTheme { ... }`, or `ComponentActivity.setContent { KurohaTheme { ... } }`.
- Use Material 3 (`androidx.compose.material3.*`) and `coil3.compose.AsyncImage`.
- Handle edge-to-edge insets (`Scaffold` padding or `WindowInsets`).
- Put private UI composables in the same file (or a dedicated `*Screen.kt` in the same package if >250 lines).
- Do NOT invent `R.string.*`, `R.drawable.*`, or helper functions — verify with `rg` first.
- After editing, run `./scripts/opencode-verify.sh` and fix all `e:` errors until exit code is 0.
```
