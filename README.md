# LinkScrub

LinkScrub is a privacy-first Android application that cleans tracking parameters from URLs and optionally shortens them using your self-hosted Shlink instance.

## Features
- **Cleaner Engine**: Pure Kotlin module stripping global tracking junk (`utm_*`, `fbclid`, `gclid`, `dclid`, `msclkid`, `igshid`, `igsh`, `mc_eid`, `mc_cid`, `_hsenc`, `_hsmi`, `ref_src`, `ref_url`, `si`) and domain-specific rules (e.g. YouTube keeping `v`, `t`, `list`, Amazon stripping tracking path/params).
- **Share Sheet Target**: Receives shared plain text, cleans embedded URLs, and presents a bottom sheet with original link, cleaned link, removed parameter chips, and Copy/Share/Shorten actions.
- **Shlink Integration**: Configurable server URL, API key (stored securely using Android Keystore-backed EncryptedSharedPreferences), optional tags, custom slug support, and cleartext HTTP setting toggle.
- **Quick Settings Tile**: One-tap tile to clean and shorten URLs from your clipboard with zero background monitoring.
- **Text-Selection Action ("Clean link" / "Clean & shorten")**: Standard Android text-selection toolbar actions. Replaces messy links in place in editable text fields and displays clean results for read-only selections.
- **Local History**: Room-backed local storage of up to 50 recent links with swipe-to-delete and clear-all functionality. Can be toggled off in Settings.

## Architecture
- **Tech Stack**: Kotlin, Jetpack Compose, Material 3 (dynamic color, light/dark mode), MVVM with ViewModel and StateFlow, Navigation Compose, Ktor HTTP Client, DataStore Preferences, Room DB, Android Security Crypto.
- **Privacy & Security**: Zero backend of ours, zero analytics, zero ad SDKs, zero crash reporters. Requests only `INTERNET` permission. API keys and full URLs are never logged.

## Setup & Building
1. Open the project in Android Studio or build via command line.
2. Build debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run tests:
   ```bash
   ./gradlew :cleaner:test :app:test
   ```

## Rules Format (`rules.json`)
The rule configuration resides in `cleaner/src/main/resources/rules.json`:
```json
{
  "globalRules": {
    "removeParams": [
      "utm_*",
      "fbclid",
      "gclid",
      ...
    ]
  },
  "domainRules": [
    {
      "domains": ["x.com", "twitter.com"],
      "removeParams": ["s"]
    },
    {
      "domains": ["youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"],
      "keepOnlyParams": ["v", "t", "list"]
    },
    {
      "domains": ["amazon.com", "www.amazon.com", "amazon.co.uk", "amazon.de", "amazon.ca", "amazon.co.jp"],
      "stripPathAfterPattern": "^(.*/dp/[A-Z0-9]{10})",
      "removeAllParams": true
    }
  ]
}
```

## Shlink Setup Note
To use link shortening:
1. Set up a self-hosted Shlink server (version 3.x+ recommended).
2. Generate an API key from your Shlink instance (`shlink api-key:generate`).
3. Enter your Shlink server Base URL and API key in LinkScrub Settings and tap **Test Connection**.

## Tested Apps for Text Selection
The text-selection action (`android.intent.action.PROCESS_TEXT`) was tested for compatibility:
- **Editable text fields**: Google Messages, AOSP Messaging, Note applications.
- **Read-only text**: Google Chrome, Android WebViews, system text views.
*Note: Some apps using non-standard custom text rendering engines (e.g. certain custom web views or flutter views) may not expose the system text-selection toolbar options.*

## Stated Assumptions
- Shlink API follows standard REST v3 specifications (`POST /rest/v3/short-urls`).
- Devices running Android 10+ enforce clipboard access restrictions; clipboard cleaning from Quick Settings Tile is accomplished by launching a transparent foreground Activity (`TileHandlerActivity`).
