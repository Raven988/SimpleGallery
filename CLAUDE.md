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

- **`AlbumsActivity`** — entry point, requests media permissions + MANAGE_MEDIA. Toolbar has a dropdown spinner (right side) to switch display modes: Все / Альбомы / По дате / Фото / Видео. Flat modes (Все/Фото/Видео) show a 3-col media grid with multi-select + bulk delete directly in this activity. Group modes (Альбомы/По дате) show a 2-col card grid and navigate to `MainActivity` on tap.
- **`MainActivity`** — grid of thumbnails for one album or date group, handles multi-select + bulk delete
- **`ViewerActivity`** — fullscreen pager (ViewPager2), starts in immersive mode (bars hidden), swipe from edge to show bars transiently
- **`MediaRepository`** — all MediaStore queries; images use `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, videos use `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` (important: NOT the generic Files URI — using Files URI breaks deletion)
- **`MediaAdapter`** — RecyclerView adapter for the thumbnail grid, supports selection mode (long-press to enter, tap to toggle, onSelectionChanged callback); `selectionEnabled = false` disables long-press selection (used in AlbumsActivity... currently unused since all modes support selection)
- **`AlbumAdapter`** — 2-col grid of album cards (cover image, name, count)
- **`DateGroupAdapter`** — 2-col grid of date group cards, reuses `item_album.xml`; groups media by month/year
- **`ViewerPagerAdapter`** — RecyclerView.Adapter for ViewPager2 pages; photos use ZoomageView (pinch-zoom), videos use VideoView with custom play/pause button + seekbar; controls auto-hide after 2.5s during playback

### Display modes (AlbumsActivity spinner)

| Mode | Layout | Content | Tap action |
|---|---|---|---|
| Все | 3-col grid | All photos + videos | Open ViewerActivity |
| Альбомы | 2-col cards | Folders (buckets) | Open MainActivity |
| По дате | 2-col cards | Month/year groups | Open MainActivity with date filter |
| Фото | 3-col grid | Images only | Open ViewerActivity |
| Видео | 3-col grid | Videos only | Open ViewerActivity |

### Deletion flow

- **Android 11+ (API 30+):** `MediaStore.createDeleteRequest` — system dialog appears (unavoidable unless MANAGE_MEDIA is granted)
- **Android 12+ with MANAGE_MEDIA:** direct `contentResolver.delete()`, no dialog
- **Android <11:** direct `contentResolver.delete()` with `WRITE_EXTERNAL_STORAGE` permission
- Deletion is accessible via multi-select in `MainActivity` (album/date group view) and in `AlbumsActivity` flat modes (Все/Фото/Видео)

### MediaRepository queries

- `queryAlbums(context)` — groups all media by bucket (folder), returns `List<Album>`
- `queryMedia(context, bucketId, mediaType?, dateFrom?, dateTo?)` — flat media list; `bucketId=null` = all media; `mediaType` = `MEDIA_TYPE_IMAGE`/`MEDIA_TYPE_VIDEO`/null; date range in Unix seconds
- `queryMediaGroupedByDate(context)` — groups all media by month/year, returns `List<DateGroup>` with `dateFrom`/`dateTo` boundaries for use as `queryMedia` filter

### Passing filters to MainActivity

`MainActivity` reads these extras from the launching intent:
- `EXTRA_BUCKET_ID` (Long, -1 = all)
- `EXTRA_BUCKET_NAME` (String, shown as toolbar title)
- `EXTRA_MEDIA_TYPE` (Int, -1 = both; use `MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE/VIDEO`)
- `EXTRA_DATE_FROM` / `EXTRA_DATE_TO` (Long, Unix seconds, -1 = no filter)

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
