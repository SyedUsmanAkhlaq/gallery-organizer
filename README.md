# Gallery Organizer (personal, on-device)

An Android app that browses the phone gallery by album, groups photos by **clothing style** (Categories tab) and by **person** (People tab). Everything runs on the phone.

**Privacy guarantee:** the release manifest removes the `INTERNET` permission (`tools:node="remove"`), even if a library declares it. Android then blocks every network socket the app opens, so no photo, thumbnail, embedding or telemetry can leave the device. Models are bundled in the APK; there is no download step. App data is excluded from cloud backup and from device-to-device transfer.

## Status

| Milestone | Scope | State |
|---|---|---|
| 1 | Skeleton, permissions, Albums tab, viewer, CI + Release APK | done |
| 2 | Pigeon contract, Room, WorkManager scan loop, progress stream, debug screen | **done (awaiting phone test)** |
| 3 | Clothing classification (MobileCLIP-S0, ONNX Runtime), Categories tab | planned |
| 4 | Face detection (ML Kit) + recognition + clustering, People tab | planned |
| 5 | Settings, delete-all-data, incremental detection, performance pass | planned |

## Install on the phone

1. Push to `main` (or run the workflow manually from the Actions tab).
2. On the phone, open
   `https://github.com/<owner>/<repo>/releases/latest/download/gallery-organizer.apk`
   This works without logging in **only if the repository is public** (assets in private repos need GitHub auth).
3. Allow "install unknown apps" for your browser once. Later builds install over the old one, because every build is signed with the same committed key.

Each build is also available as a workflow artifact (zip, needs GitHub login).

## Architecture

```
┌──────────────────────── Flutter (Dart) ────────────────────────┐
│ presentation   Riverpod providers, go_router, Material 3        │
│   Albums tab ── album grid (paged 60, prefetch at 12) ── viewer │
│   Categories tab (M3)        People tab (M4)       Debug (M2)   │
│ domain         entities (Album, MediaItem), PagedState, enums   │
│ data           photo_manager adapters (albums, thumbnails)      │
│                Pigeon client (M2) + EventChannel progress (M2)  │
└───────────────┬────────────────────────────────────────────────┘
                │ IDs, labels, scores, counts only — never pixels
                │ or embeddings
┌───────────────▼──────────── Kotlin (android/app) ──────────────┐
│ ScanWorker (WorkManager, foreground, dataSync)        (M2)      │
│   MediaStore enumerate → queue → batches of 20                  │
│   stage 1: clothing  — 224px thumb → MobileCLIP-S0 (ONNX RT)    │
│   stage 2: faces     — 512px thumb → ML Kit detect → align 112  │
│                        → face embedder (ONNX RT) → clustering   │
│ Room cache: (assetId, dateModified, modelVersion) per stage     │
└─────────────────────────────────────────────────────────────────┘
```

### Shared asset ID

The asset key everywhere is the MediaStore `_ID`, as a decimal string. On Android, photo_manager's `AssetEntity.id` is that same `_ID`, so the Kotlin scanner can return IDs and Flutter can rebuild a thumbnail from just `id` + type (`assetFor()` in `lib/features/albums/data/gallery_images.dart`). Milestone 2 adds a runtime check on the debug screen that compares IDs from both sides.

## Native bridge (milestone 2)

- **Contract:** `pigeons/native_api.dart`. CI runs `dart run pigeon --input pigeons/native_api.dart`, which generates `lib/bridge/native_api.g.dart` and `android/.../bridge/NativeApi.g.kt`. Generated files are gitignored. Pigeon is pinned to an exact version, so the generated code stays stable.
- **Host API** (`GalleryHostApi`): `startScan`, `pauseScan`, `getScanStatus`, `getDebugInfo`, `verifyAssetIds`, `logError`, `getRecentErrors`, `clearErrors`, `shareLog`, `getAppInfo`. Category and people queries are added in milestones 3 and 4. Async methods are Kotlin `suspend` functions, and DB work runs on `Dispatchers.IO`.
- **Progress stream:** a Pigeon `@EventChannelApi` (`scanProgress()`). It is a typed EventChannel, so there are no hand-written channel names. Events are throttled to about 7/s and carry `{state, stage, scanned, total, currentAssetId, avgMsPerImage, message}`.
- **Scan engine** (`android/.../scan/`):
  - `ScanWorker` is a `CoroutineWorker` running as a foreground worker (type `dataSync`) with a notification that has a Pause action. It is unique work with `KEEP`.
  - `IndexStage` mirrors MediaStore into Room table `media_assets`:
    1. `ScanPlanner` diffs MediaStore against stored `(assetId, dateModified)` keys.
    2. Rows for deleted assets are removed.
    3. New and changed rows are written newest first, in batches of 20.
  - Pausing cancels the work. Starting again resumes, because finished rows match the cache key.
- **Error log:** Kotlin crashes, scan errors and Flutter/Dart errors (`FlutterError.onError`, `PlatformDispatcher.onError`, forwarded over Pigeon) all go into one log of the last 100 errors, stored in `files/logs/errors.log`. File-system paths are stripped before anything is stored.

### Debug screen

Settings (gear icon on Albums) → tap **Version** 7 times. It shows:
- live scan state and per-stage processed count, ms/item and delegate
- DB counts and size, and the WorkManager state
- an **asset ID check**: the first 50 photo_manager IDs are looked up as MediaStore `_ID`s
- the error list, with **Share log**, **Copy**, **Clear** and **Log test error** buttons

## Decisions

- **Inference runtime: ONNX Runtime Mobile**, not LiteRT/TFLite. MobileCLIP and other `open_clip` models export to ONNX with a single `torch.onnx.export`. CLIP→TFLite goes through onnx2tf or ai-edge-torch and often breaks on attention or LayerNorm ops. Most MobileFaceNet-class face models also ship as ONNX. ONNX Runtime has NNAPI and XNNPACK execution providers with CPU fallback.
- **arm64-only APK** (`--target-platform android-arm64`).
- **Theme:** always black, with a monochrome Material 3 scheme (`lib/app/theme.dart`).
- **No R8/minify:** avoids keep-rule breakage in Room, ONNX Runtime and ML Kit.
- **Toolchain pinned in `android/settings.gradle.kts`:** AGP 8.11.1, Kotlin 2.2.20, Gradle 8.14.3, compile/target SDK 36, minSdk 26. Pinning lets milestone 2 pick a matching KSP for Room.
- **Signing:** `android/app/debug.keystore` is a throwaway PKCS12 key (alias `androiddebugkey`, password `android`), committed on purpose. It was generated with `openssl` (the Mac had no JDK); CI checks it with `keytool` before building.

## Permissions

`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14 partial access) and `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`). The app uses `PhotoManager.requestPermissionExtend()` and treats `hasAccess` (full **or** limited) as "can browse". It does not use `isAuth` or `permission_handler`. With partial access, a banner offers "Change selection" (system picker) and "Grant full access" (app settings). On return to the app it re-checks permission.

## Regenerating models / categories / thresholds

Milestone 3 adds `tools/export_models.py`, a Colab notebook and `categories.yaml`.

## Model licenses

Milestones 3 and 4 fill this in (MobileCLIP-S0 weights, face recognition weights). Personal use only.

## Known limitations

- Classification is whole-image: it struggles when the person is small in the frame. The classifier interface is designed so a person-crop stage can be added later. (M3)
- Videos are only browsed, not classified, in v1.
- Video playback reads the file directly; very large 4K/HDR files depend on the phone's decoder.
- `pubspec.lock` is generated in CI, not committed, because there is no local Flutter. Dependency resolution may drift within the caret ranges.

## Development without a local Flutter SDK

CI runs these steps in order:
1. `flutter pub get`
2. Pigeon codegen
3. `flutter analyze`
4. `flutter test`
5. `flutter build apk --release`
6. Kotlin unit tests (`./gradlew :app:testReleaseUnitTest`): JUnit for pure logic, plus Robolectric with in-memory Room for the index stage

Each step runs through `tools/ci/run.sh`. On failure, the script posts the relevant log lines as a GitHub error annotation, which can be read without logging in. Pure logic (paging, formatting, permission mapping; later clustering, softmax/thresholds, DB queries) lives in Flutter-free Dart or Kotlin files with unit tests.
