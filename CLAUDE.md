# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git commits

Do not add `Co-Authored-By` or any Claude/Anthropic attribution lines to commit messages.

## Build commands

All commands require environment setup (tools installed locally, not system-wide):

```bash
export JAVA_HOME=~/tools/jdk17/jdk-17.0.19+10
export ANDROID_SDK_ROOT=~/tools/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH
cd ~/Downloads/GalleryAPK
```

```bash
# Build debug APK
./gradlew assembleDebug --no-daemon

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch on device
adb shell am start -n com.example.simplegallery/.AlbumsActivity

# View app logs
adb logcat -s Gallery,AndroidRuntime

# Build + install in one step
./gradlew assembleDebug --no-daemon && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK output is at `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

Single-module Android app (Kotlin, minSdk 24, targetSdk 34, ViewBinding, no Jetpack Compose).

**Screen flow:** `AlbumsActivity` → `MainActivity` → `ViewerActivity`

### Key classes

- **`AlbumsActivity`** — entry point, requests media permissions + MANAGE_MEDIA, shows album grid
- **`MainActivity`** — grid of thumbnails for one album, handles multi-select + bulk delete
- **`ViewerActivity`** — fullscreen pager (ViewPager2), starts in immersive mode (bars hidden), swipe from edge to show bars transiently
- **`MediaRepository`** — all MediaStore queries; images use `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, videos use `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` (important: NOT the generic Files URI — using Files URI breaks deletion)
- **`MediaAdapter`** — RecyclerView adapter for the thumbnail grid, supports selection mode (long-press to enter, tap to toggle, onSelectionChanged callback)
- **`ViewerPagerAdapter`** — RecyclerView.Adapter for ViewPager2 pages; photos use ZoomageView (pinch-zoom), videos use VideoView with custom play/pause button + seekbar; controls auto-hide after 2.5s during playback

### Deletion flow

- **Android 11+ (API 30+):** `MediaStore.createDeleteRequest` — system dialog appears (unavoidable unless MANAGE_MEDIA is granted)
- **Android 12+ with MANAGE_MEDIA:** direct `contentResolver.delete()`, no dialog
- **Android <11:** direct `contentResolver.delete()` with `WRITE_EXTERNAL_STORAGE` permission
- Deletion is only accessible via multi-select in `MainActivity` (not in the viewer)

### Permissions

| Permission | Purpose |
|---|---|
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Android 13+ media access |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Android 12 and below |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 28) | Deletion on Android 9 and below |
| `MANAGE_MEDIA` | Silent deletion on Android 12+ (user grants once via Settings) |

### Video controls lifecycle

`ViewerPagerAdapter` owns two `Runnable`s per `PageViewHolder`:
- `hideRunnable` — fades out play button + seekbar after 2.5s
- `progressRunnable` — updates seekbar every 500ms while playing

Both are cancelled in `onViewRecycled` and `onViewDetachedFromWindow` to prevent leaks.

## Dependencies

- `Glide 4.16.0` — thumbnail loading
- `ZoomageView 1.3.1` (jsibbold) — pinch-to-zoom for photos in the viewer
- Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24
