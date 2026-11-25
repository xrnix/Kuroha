---
name: android-architect
description: Senior Android Systems and Architecture Engineer. Master of modern Android application architecture (MVVM/MVI, Kotlin Coroutines, StateFlow, Jetpack Compose, Room DB migrations, Hilt/Dagger DI, WorkManager, Gradle build-logic/TOML version catalogs, ProGuard/R8, and API 23-36 Android lifecycle management). Use when architecting, refactoring, debugging, or implementing Android core systems, databases, dependency injection, and concurrency.
---

# Android Architect

Senior-level engineering standards for high-performance, robust, and clean Android applications.

## 1. Concurrency & Asynchronous Programming (Kotlin Coroutines & Flow)
- **Structured Concurrency**: Never launch coroutines in `GlobalScope`. Always bind jobs to appropriate lifecycles: `viewModelScope`, `lifecycleScope`, or managed `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
- **Dispatcher Discipline**:
  - `Dispatchers.Main.immediate` for immediate UI dispatch without re-posting.
  - `Dispatchers.IO` strictly for disk I/O, database access, and network requests.
  - `Dispatchers.Default` for CPU-intensive tasks (image decoding, parsing, crypto, sorting).
- **Flow Invariants**:
  - Expose read-only `StateFlow<T>` or `SharedFlow<T>` from ViewModels (`asStateFlow()`).
  - Use `SharingStarted.WhileSubscribed(5_000)` to stop upstream collection when the UI is in the background without canceling on transient configuration changes.
  - Always collect flows in UI safely with `repeatOnLifecycle(Lifecycle.State.STARTED)` or `flowWithLifecycle`.
  - Handle exceptions inside Flow pipelines using `catch { ... }` or explicit `Result<T>` envelopes.

## 2. Clean Architecture & Separation of Concerns
- **Layer Boundaries**:
  - **Data Layer**: Repositories, Data Sources (local Room DB, remote OkHttp/Retrofit APIs, SharedPreferences/DataStore). Expose pure Kotlin domain models, mapping away DTOs/Entities.
  - **Domain Layer**: UseCases / Interactors for business logic that spans multiple repositories. UseCases must be single-responsibility and expose `operator fun invoke(...)`.
  - **Presentation Layer**: ViewModels holding UI state, Activities/Fragments/Composables rendering state and emitting user intents.
- **Dependency Inversion**:
  - Modules must depend on interfaces, not implementations.
  - Hilt / Dagger modules must use `@Binds` wherever possible (avoids generating unnecessary factory code compared to `@Provides`).
  - Use `@ApplicationContext` and `@ActivityContext` qualifiers appropriately to prevent memory leaks.

## 3. Data Persistence & Room ORM
- **Database Migrations**:
  - Never allow destructive fallback migrations in production. Always write explicit, tested `Migration(start, end)` SQL scripts.
  - Enable and maintain Room schema export (`exportSchema = true`) and track versioned schema JSONs.
- **Query Optimization**:
  - Use `@Transaction` on DAO methods returning relation objects or performing multi-statement operations.
  - Return `Flow<T>` or `PagingSource<Int, T>` from DAOs for reactive UI updates; avoid blocking synchronous calls.
  - Define targeted indexes on foreign keys, frequently filtered columns, and sort columns.

## 4. Modern Gradle & Build Engineering
- **Version Catalogs**: Centralize all dependencies, plugins, and version pins in `gradle/libs.versions.toml`.
- **Build Logic**: Extract repeated convention plugins into `build-logic` composite builds (`includeBuild("build-logic")`) instead of copy-pasting Gradle blocks.
- **R8 / ProGuard Optimization**:
  - Keep consumer rules explicit (`consumer-rules.pro`).
  - Keep serialized model classes (`@Keep` or `@Serializable`) and reflection targets safe from obfuscation.
  - Run minified debug/release builds locally to catch missing keep rules before deployment.

## 5. Modern Android Platform & API Compliance (Android 14 - 16 / API 34-36)
- **Scoped Storage**: Use Storage Access Framework (SAF), `MediaStore`, or app-specific private storage (`context.getExternalFilesDir(...)`). Never assume raw file path access outside private sandboxes.
- **Foreground Services & Background Work**:
  - Explicitly declare `foregroundServiceType` in manifest for every Foreground Service (e.g., `dataSync`, `mediaPlayback`, `shortService`).
  - Use `WorkManager` with exponential backoff for deferrable, guaranteed background work instead of raw Services or Alarms.
- **Edge-to-Edge by Default**:
  - Target modern WindowInsets (`WindowInsetsCompat`, `ViewCompat.setOnApplyWindowInsetsListener`).
  - Handle display cutouts, navigation bars, and status bars seamlessly across API 23 to 36.
