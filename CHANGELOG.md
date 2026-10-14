# Changelog

## [1.0.0] - 2026-10-14

### Milestone 1: Cleaner Engine
- Implemented pure Kotlin `:cleaner` module with zero Android dependencies.
- Added `rules.json` bundled rules for global parameter removals and domain-specific rules (YouTube, Amazon, X/Twitter).
- Implemented `UrlCleaner.clean` and `UrlCleaner.cleanAll` supporting query parameter parsing, fragment preservation, and parameter order preservation.
- Added 30 table-driven unit tests.

### Milestone 2: Share Target & Result UI
- Implemented Jetpack Compose UI with Material 3 dynamic color, light/dark mode support, and Navigation Compose.
- Added intent filter for `ACTION_SEND` (`text/plain`) share target.
- Created result bottom-sheet UI showing original link, cleaned link, removed parameter chips, and Copy/Share actions.

### Milestone 3: Shlink Integration
- Added Shlink REST API client targeting `/rest/v3/short-urls`.
- Stored Shlink API key securely using Android Keystore-backed `EncryptedSharedPreferences`.
- Added Settings screen with server base URL, API key, cleartext HTTP toggle, default tags, and "Test connection" button.
- Added unit tests for Shlink API client using Ktor MockEngine.

### Milestone 4: Quick Settings Tile
- Added `LinkScrubTileService` and transparent `TileHandlerActivity` for Android 10+ clipboard access.
- Enabled one-tap clipboard URL cleaning, optional auto-shortening, toast feedback, and immediate activity finishing.

### Milestone 5: Text-Selection Action ("Clean link")
- Registered `android.intent.action.PROCESS_TEXT` intent filter for "Clean link" text selection action.
- Added in-place text replacement for editable text views and bottom sheet UI for read-only selections.
- Added `activity-alias` for "Clean & shorten" action with a 5-second timeout fallback.

### Milestone 6: History & Polish
- Implemented Room database for local history capped at 50 recent items with swipe-to-delete and clear-all features.
- Added settings toggle to enable/disable history.
- Added edge-to-edge layout, TalkBack accessibility labels, empty/error state handling, README documentation, and changelog.
