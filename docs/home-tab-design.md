# Home Tab Design Specification

## 1. Understanding Summary
- **Goal**: Rename the first bottom-bar tab (`R.id.nav_history`) from **History** to **Home**, keep History as the main feed, and add a compact top header strip with:
  1. **Reading Streak & Today's Reading Time pill** (tapping opens `StatsActivity`).
  2. **"Surprise me" / Random manga** button (picks randomly across Library, History, and enabled sources combined).
- **Icon**: Material Home glyph (`ic_home` outlined / `ic_home_checked` filled) with the same animated selector transition as `ic_history_selector`.
- **UI Architecture**: Hybrid Compose header (`ComposeView` inside `RecyclerView` via `HomeHeaderAD`) on top of Kuroha's existing XML/`RecyclerView` History grid and horizontal `QuickFilter` strip.
- **Explicit Non-Goals**:
  - No "Continue Reading" hero card (already covered by the dedicated Resume FAB).
  - No "Recent Updates" strip (already in Library/Updates).
  - No full-screen Compose `LazyVerticalGrid` rewrite of `HistoryListFragment`.

## 2. Assumptions & Non-Functional Requirements
- **Performance & Stability**:
  - `RecyclerView` + `AdapterDelegate` handles the manga grid/list for 120Hz scrolling and zero `LazyVerticalGrid` unbounded-constraint crashes on Waydroid/large screens.
  - `HomeHeaderModel` spans full grid width (`spanCount`) and uses `MATCH_PARENT` width / `WRAP_CONTENT` height.
  - Stats snapshot runs asynchronously on `Dispatchers.Default`.
- **Privacy & Content Filtering**:
  - If `settings.isStatsEnabled` is `false`, the streak/time pill is hidden.
  - Random manga selection respects `settings.isNsfwContentDisabled`.
- **Reliability**:
  - Random pick falls back gracefully to local Library/History if offline or if a remote source fails, and shows a snackbar error if no candidates exist.

## 3. Decision Log
| Decision | Alternatives Considered | Why Chosen |
|---|---|---|
| Hybrid Compose header inside `RecyclerView` (`Option A`) | Full-screen Compose `HomeScreen` (`Option B`) | Keeps 120Hz `RecyclerView` smoothness, original Material cover badges, and Waydroid resize stability while using M3 Expressive Compose for the new Home header. |
| Keep `R.id.nav_history` ID, rename label to **Home** | Rename ID to `nav_home` across nav/prefs/shortcuts | Avoids breaking existing start-tab preferences, shortcuts, and backstack state. |
| Material Home selector (`ic_home` / `ic_home_checked`) | Keep History clock icon | Matches the "Home" tab identity while keeping the exact animated fill transition of the other bottom-bar tabs. |
| Combined random source (Library + History + enabled sources) | Library-only, History-only, or Explore-only | Gives true discovery while still working offline via local Library/History fallback. |

## 4. Final Design
1. **Navigation & Icon**:
   - Update `nav_history` menu title to `@string/home` and icon to `@drawable/ic_home_selector` (`ic_home.xml` + `ic_home_checked.xml`).
2. **Data Layer (`PickRandomMangaUseCase` + `HistoryListViewModel`)**:
   - `HistoryListViewModel` loads `todayDurationMs` and `streakDays` from `StatsRepository.getStatsSnapshot(StatsPeriod.DAY, emptySet())` when `settings.isStatsEnabled` is `true`.
   - `PickRandomMangaUseCase` samples across Favourites, History, and a random enabled source (respecting `isNsfwContentDisabled`), returning a `Manga` to open in `Details`.
3. **UI (`HomeHeaderModel` + `HomeHeaderAD`)**:
   - Inserted at the top of `HistoryListViewModel.content` above `QuickFilter`.
   - Hosted in `ComposeView` (`DisposeOnViewTreeLifecycleDestroyed` + `KurohaTheme`), rendering the Streak/Today pill and the "Surprise me" button.
   - `HistoryListFragment`'s `SpanSizeLookup` grants `HomeHeaderModel` full `spanCount`.
