# Kuroha Compose Reference Sheet (For Porting)

Use only verified symbols from this sheet or verify directly with `rg`.

## 1. Hosting Compose in an Existing Fragment
```kotlin
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.settings.compose.KurohaTheme

@AndroidEntryPoint
class ExampleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            KurohaTheme {
                // Screen composable here
            }
        }
    }
}
```

## 2. Hosting Compose in an Activity
See `app/src/main/kotlin/org/koitharu/kotatsu/tracker/ui/calendar/ReleaseCalendarActivity.kt`:
- Extend `ComponentActivity` with `@AndroidEntryPoint`
- Call `enableEdgeToEdge()` before `setContent { KurohaTheme { ... } }`

## 3. Existing Shared Compose Building Blocks
Located in `org.koitharu.kotatsu.settings.compose`:
- `KurohaTheme` (`SettingsTheme.kt`)
- `SettingsScaffold` (`SettingsScaffold.kt`)
- `SettingsGroup` (`SettingsGroup.kt`)
- `SwitchSettingsItem`, `SliderSettingsItem`, `SettingsItem` (`SettingsItem.kt`)
- `SegmentedSettingsItem` (`SegmentedSettingsItem.kt`)
- `InlineSliderSettingsRow` (`InlineSliderSettingsRow.kt`)

## 4. Images & Navigation
- Images: `coil3.compose.AsyncImage` (`model = manga.coverUrl`, `contentScale = ContentScale.Crop`)
- Navigation from Fragment: `router.openDetails(manga)` (`org.koitharu.kotatsu.core.nav.router`)
- Navigation from Activity: `startActivity(AppRouter.detailsIntent(this, manga))`

## 5. Edge-to-Edge & Lists
- Use `Scaffold(...) { padding -> LazyColumn(Modifier.padding(padding)) { ... } }` or `WindowInsets.systemBars` / `WindowInsets.navigationBars`.
- Always provide stable `key = { ... }` in `LazyColumn` / `LazyVerticalGrid` `items(...)`.
- Avoid allocating objects or calling `prefs` synchronously inside composable bodies; use `ViewModel` `StateFlow` + `collectAsState()`.
