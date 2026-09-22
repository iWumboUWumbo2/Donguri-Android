# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working in this repository.

## Commands

```bash
./gradlew :app:assembleDebug        # build the debug APK (also builds the native engine)
./gradlew :app:testDebugUnitTest    # run the unit tests
./gradlew :app:assembleRelease      # R8-minified release build; runs lintVital
```

Install and launch on a connected device or emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n world.wumbo.donguri.debug/world.wumbo.donguri.MainActivity
```

There is a local AVD named `Donguri_API36` (android-36, google_apis, arm64-v8a):

```bash
$ANDROID_HOME/emulator/emulator -avd Donguri_API36 -no-snapshot -no-audio -no-boot-anim
```

Compose draws no resource ids, so tapping through `adb shell input tap` needs real
coordinates: `adb shell uiautomator dump /sdcard/u.xml && adb shell cat /sdcard/u.xml`
gives each text node's `bounds`. Estimating from a screenshot misses often enough to
look like a broken feature.

AnkiDroid 2.24.1 is installed on that AVD with a local collection, so the mining path is
testable. It needs all-files access before it will finish its first run — grant it with
`adb shell appops set --uid com.ichi2.anki MANAGE_EXTERNAL_STORAGE allow`, then restart
the app, since that screen does not re-read the permission on resume.

The submodules are load-bearing — `git clone --recurse-submodules`, or `git submodule update --init --recursive` after a plain clone. `third_party/hoshidicts-kotlin-bridge` carries the dictionary engine's C++ source and its JNI layer, and the build will not configure without it.

The first build downloads the NDK (29.0.14206865) and CMake (3.31.6) through the SDK manager, which takes a few minutes.

## Architecture

Two distinct layers live in this app, and they meet in exactly one place. This mirrors [Donguri on iOS](reference/Donguri-iOS), which is the source of truth for user-visible behaviour.

**1. The 5ch reader** (`bbs/`). Plain `HttpURLConnection` + Compose, no third-party networking:

- `bbs/net/BbsMenuService` fetches the board list from `menu.5ch.io/bbsmenu.json` — the one endpoint that is UTF-8 JSON.
- `bbs/net/ThreadService` parses `{boardURL}/subject.txt`.
- `bbs/net/PostService` parses `{boardURL}/dat/{id}.dat`, and on 404 (dat落ち — the thread aged out) falls back to scraping the server's `read.cgi` HTML archive. **Both sources are Shift-JIS**, not UTF-8.
- `bbs/text/HtmlDecoding` normalises the raw dat/HTML into markdown-ish text; `>>N` reply anchors become `[>>N](donguri://res/N)` links, which `bbs/text/PostText` turns back into tappable ranges over the visible text.
- Screens are `BbsMenuScreen → BoardScreen → ThreadScreen`. `ThreadScreen` also owns reply-preview cards, ID/trip highlighting, and read-position tracking (`bbs/store/ReadStateStore`, DataStore-backed).
- NG (あぼーん) filtering lives in `bbs/store/NgFilterStore`. Hidden posts are never removed from the list — `>>N` links and `Post.replies` are plain indices into it — so `ThreadScreen` renders an `AbornPlaceholder` in their place.

**2. The dictionary + Anki stack** (`dictionary/`, `popup/`, `features/dictionary/`, `features/anki/`), ported from [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android) and [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader), GPL-3.0. `README.md` has a file-by-file table of what came from where. Ported files keep their SPDX/copyright headers — preserve them.

- `dictionary/LookupEngine` owns the hoshidicts session, reached through `de.manhhao.hoshi.HoshiDicts` (JNI). It builds a new session and only swaps it in once ready, so a settings change never leaves a tapped word looking at a half-built query.
- `dictionary/DictionaryRepository` is the single entry point: import, ordering, updates, lookup.
- `features/anki/AnkiRepository` mines through **AnkiDroid's ContentProvider** or **AnkiConnect over HTTP**. iOS uses AnkiMobile's `x-callback-url`; Android has no such thing, and AnkiDroid's provider API is the local equivalent.

**Where they meet — tap-to-lookup.** On iOS, post text has to render in a `WKWebView` because SwiftUI's `Text` has no per-character hit testing, and `selection.js` does the hit test inside the page. **Compose does not have that limitation**: `TextLayoutResult.getOffsetForPosition` maps a tap straight to a character offset. So the Android port keeps post text native — no WebView per row — and ports only selection.js's *scanning* logic:

`PostText` (Compose `Text` + `detectTapGestures`) → character offset → `lookup/TextScanner` (selection.js's scan-window and sentence-extraction rules, character for character) → `ThreadViewModel.onWordTap` → `DictionaryRepository.lookup` → `popup/LookupPopup` → `popup/DictionaryEntriesWebView` (renders entries with the unmodified `popup.js`/`popup.css`) → `+` button → `AnkiRepository.mineEntry`.

`popup.js`, `popup.css` and `popup-gestures.js` under `app/src/main/assets/donguri-web/` are copied verbatim from Hoshi Reader Android and are the real rendering engine — prefer configuring them through the `window.*` globals `popup/PopupHtml` sets over editing them.

**The pop-up bridge.** `popup.js` talks to its host through `webkit.messageHandlers.*` (the WKWebView API). Hoshi Reader Android shims that onto `window.parent.postMessage`, because its pop-ups are iframes stacked over the reader's own WebView. Donguri renders posts natively, so each pop-up is its own WebView in a Compose overlay and `popup/PopupHtml` shims the same calls straight onto a `@JavascriptInterface` object (`popup/PopupBridge`) — no iframe, no parent frame. Handlers that `await` a value go through `requestMessage`, which the host answers with `resolveMessage`.

## Project conventions

- **UI**: Jetpack Compose and Material 3. **Navigation**: Navigation3 with typed routes (`navigation/AppRoute`), one back stack — Donguri has no top-level tabs.
- **State**: ViewModels expose an immutable UI state over `StateFlow`; screens are state-down, events-up.
- **DI**: Hilt. New dependencies go through constructor injection and a module in `di/`.
- **Settings**: DataStore-backed repositories (`config/UserConfigRepository`, `features/anki/AnkiSettingsRepository`), never read or written directly from a composable.
- **Strings**: every user-visible string is in `res/values/strings.xml` (English, the default) and `res/values-ja/strings.xml`. Keep placeholders and escaping consistent across both.
- **WebView**: local resources are served by `popup/PopupResourceHandler` through `shouldInterceptRequest` from the `appassets.androidplatform.net` origin. Do not enable file URL access.

## CI

`.github/workflows/ci.yml` builds debug, runs the unit tests, and then builds
release — the release build is where R8, resource shrinking and `lintVital` run, and
it has broken on its own before while debug stayed green, so it is not optional.

`.github/workflows/release.yml` runs on a `v*` tag and publishes a **draft** release.

Both check out **only** `third_party/hoshidicts-kotlin-bridge` (recursively — it has
its own `hoshidicts`, `xxHash` and `zstd` submodules). Never switch these to a blanket
recursive checkout: `reference/Hoshi-Reader-Android` pins a hoshidicts commit that has
been force-pushed off its remote, and recursing into it fails the whole checkout.

## Deliberate divergences from iOS

Record any new one here.

- Post text is native Compose, not a WebView — see above. `selection.js` still ships, but only for lookups *inside* the pop-up's own glossaries.
- Mining goes through AnkiDroid's ContentProvider rather than `x-callback-url`.
- "Translate" opens the `ACTION_PROCESS_TEXT` chooser; Android has no system translation sheet like iOS 17.4's `translationPresentation`.
- Kanji dictionaries are not wired up, matching iOS Donguri (which drives hoshidicts' term/frequency/pitch APIs only). The Android JNI bridge does expose them.

## Toolchain pins that matter

- AGP 9.4.1 uses **built-in Kotlin** and ships a Kotlin **2.2.10** compiler. The Compose compiler plugin resolves its `compose-group-mapping` artifact against *that* version, so `kotlin` in the version catalog must match it or `assembleRelease` fails to resolve. KSP is on the newer `2.3.9` scheme, because the `2.2.10-2.0.x` line registers its generated sources through the `kotlin.sourceSets` DSL, which built-in Kotlin rejects.
- `res/resources.properties` (`unqualifiedResLocale=en-US`) is required by `generateLocaleConfig`.
- The `ankidroid-api` artifact ships AnkiDroid's own lint checks, which encode conventions internal to that app; `DirectSystemCurrentTimeMillisUsage` and `DuplicateCrowdInStrings` are disabled in `app/build.gradle.kts`.
- `proguard-rules.pro` keeps `de.manhhao.hoshi.**` — JNI constructs those classes by their JVM names — and every `@JavascriptInterface` method.
- `app/src/main/java/de/manhhao/hoshi/HoshiDicts.kt` **must stay byte-identical to the submodule's copy**, and the `checkHoshiDictsBindings` Gradle task enforces it. `hoshidicts_jni.cpp` resolves those classes by constructor signature, so bindings taken from a different revision still compile and then abort the process on the first native call (`JNI DETECTED ERROR … NoSuchMethodError`). AGP's built-in Kotlin will not compile sources from outside the module, which is why this is a copy plus a check rather than an extra source directory. The revision in use has no `error` field on `ImportResult` and no frequency-sort `LookupOptions`; Hoshi Reader Android tracks a newer bridge commit that is no longer on the remote.
