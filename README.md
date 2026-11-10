# Gallery Organizer (personal, on-device)

An Android app with three tabs:

- **Albums** lists every album, photo and video.
- **Categories** groups photos by clothing style: shalwar kameez, saree, lehenga, kurta, abaya/hijab, western casual, formal suit, swimwear, other clothing.
- **People** groups photos by face, across all albums.

All analysis runs on the phone.

**Privacy guarantee:** the release manifest removes the `INTERNET` permission (`tools:node="remove"`), even if a library declares it. Android then blocks every socket the app opens, so no photo, thumbnail, embedding or telemetry (including ML Kit's) can leave the device. The models are bundled in the APK; nothing is downloaded at runtime. App data is excluded from cloud backup and device-to-device transfer. Logs never contain file paths, embeddings or face data.

## Install on the phone

1. Push to `main`, or run the **build** workflow from the Actions tab.
2. On the phone, open
   `https://github.com/SyedUsmanAkhlaq/gallery-organizer/releases/latest/download/gallery-organizer.apk`
3. Every build is signed with the same committed key, so new builds install over old ones and keep the analysis database.

## How to use

- Analysis starts by itself the first time, after you grant photo access. A notification with a **Pause** button shows progress; the notification shows only after you allow notifications.
- New photos are picked up automatically: MediaStore change trigger, plus a 12-hourly check. Only new or changed photos are processed.
- **Categories:** tap a card to see its photos; each thumbnail shows the model's confidence. **Low confidence** collects clothing photos below the threshold. **Not clothing** is the negative bucket.
- **People:** tap a person to see all their photos, with the source album under each one.
  - ✏️ Rename.
  - ⋮ → **Merge with another person…**, or **Hide person**.
  - **Long-press a photo** → **Not this person** (remove it, or move it to someone else).
  - The 👁 button in the People tab shows hidden people.
- **Viewer:** pinch or double-tap to zoom. The **ⓘ** button shows the top-3 clothing labels with scores and the people found in that photo.
- **Settings** (gear icon on Albums):
  - scan controls
  - face grouping sensitivity
  - category confidence
  - show single-photo people
  - hardware acceleration
  - **Delete all analysis data**
- **Debug screen:** Settings → tap **Version** 7 times. It shows:
  - per-stage progress and ms/image
  - the delegate in use (NNAPI / XNNPACK / CPU)
  - counts per category
  - faces and clusters
  - model versions
  - an asset ID check
  - the last 100 errors, with **Share log** and **Copy**

## Architecture

```
┌──────────────────────────── Flutter (Dart) ─────────────────────────────┐
│ presentation  Riverpod · go_router · Material 3 (black theme)            │
│   Albums / Categories / People ── MediaGrid (paged 60, prefetch 12)      │
│                                   └─ Viewer (zoom, video, ⓘ info sheet)  │
│   Settings · Debug                                                       │
│ domain        MediaItem, PagedState, ScanSnapshot, CategorySummary, Person│
│ data          photo_manager (albums, thumbnails, face crops)             │
│               Pigeon GalleryHostApi + typed progress EventChannel        │
└───────────────┬──────────────────────────────────────────────────────────┘
                │ IDs, labels, scores, counts, timings — never pixels/embeddings
┌───────────────▼────────────────── Kotlin ───────────────────────────────┐
│ ScanWorker (WorkManager, foreground dataSync, notification + Pause)      │
│   1 IndexStage     MediaStore → media_assets (cache key id+dateModified) │
│   2 ClothingStage  320px thumb → center 256² → MobileCLIP2-S0 (ONNX RT,   │
│                    batch 20) → cosine × 100 → stable softmax → top-3     │
│   3 FaceStage      512px thumb → ML Kit (bundled) → quality filter →     │
│                    5-point align 112² → MobileFaceNet (ONNX RT) → assign │
│   4 merge pass     agglomerative average linkage over people             │
│ MediaChangeWorker  content-URI trigger + 12 h periodic → incremental scan│
│ Room v2: media_assets · clothing_results · face_scans · faces ·          │
│          persons · face_exclusions                                       │
└──────────────────────────────────────────────────────────────────────────┘
```

### Shared asset ID

The asset key everywhere is the MediaStore `_ID`, as a decimal string. On Android, photo_manager's `AssetEntity.id` is that same `_ID`. Kotlin returns IDs plus width, height and type, and Flutter builds thumbnails from them directly (`assetFor()` in `lib/features/albums/data/gallery_images.dart`). The debug screen's **Verify** button checks this at runtime.

### Bridge

- **Contract:** `pigeons/native_api.dart`. CI runs `dart run pigeon --input pigeons/native_api.dart` and generates `lib/bridge/native_api.g.dart` and `android/.../bridge/NativeApi.g.kt`. Both are gitignored. Pigeon is pinned to an exact version.
- **Main calls:**
  - `startScan`, `pauseScan`, `getScanStatus`
  - `getCategorySummaries`, `getAssetIdsByCategory`
  - `getPeople`, `getAssetIdsByPerson`, `renamePerson`, `mergePeople`, `removeFaceFromPerson`, `setPersonHidden`
  - `setClusteringSensitivity`, `getSettings`, `updateSettings`
  - `getAssetDetails`, `deleteAllAnalysisData`
  - debug and log calls
- **Progress stream:** a Pigeon `@EventChannelApi` sending `{state, stage, scanned, total, currentAssetId, avgMsPerImage, message}`, throttled to about 7 events per second.

## Models

| Model | Use | Size | Source | License |
|---|---|---|---|---|
| **MobileCLIP2-S0** (`dfndr2b`), image tower | clothing embedding (512-d, 256 px) | ~45 MB fp32 | Apple, via open_clip / HF `timm/MobileCLIP2-S0-OpenCLIP` | Apple ML Research Model Terms of Use (research/non-commercial) |
| MobileCLIP2-S0 text tower | text embeddings, computed **offline** only | — | same | same |
| **ML Kit face detection** (bundled) | boxes + landmarks, offline | in APK | Google | ML Kit terms |
| **MobileFaceNet `w600k_mbf`** | face identity embedding (512-d, 112 px) | 13.6 MB | InsightFace `buffalo_s` (v0.7 release) | **InsightFace pretrained models: non-commercial research use only** |

Personal use is fine under these terms. Because of these licenses, the weights are **not committed** to this public repo. CI exports and downloads them at build time, verifies them, caches them, and bundles them into the APK.

**Why ONNX Runtime and not LiteRT/TFLite:** open_clip models export to ONNX with a single `torch.onnx.export`, with a parity check against PyTorch. CLIP→TFLite goes through onnx2tf or ai-edge-torch and often breaks on attention or LayerNorm. The face model is published as ONNX. ONNX Runtime has NNAPI and XNNPACK execution providers with CPU fallback.

- **Delegates:** with "Hardware acceleration" on, the app times NNAPI, XNNPACK and CPU once per model version and keeps the fastest. If NNAPI ever crashes the process during that probe, the next start notices the unfinished probe and disables NNAPI.
- **Model choice:**
  - MobileCLIP2-S0 is about 10× smaller than ViT-B/32 and more accurate zero-shot.
  - open_clip 4.x replaced MobileCLIP-S0 with it.
  - Its preprocessing is 256 px, mean 0, std 1, read from open_clip. That differs from the original CLIP mean/std, but must match the weights.

## Regenerating models, adjusting categories and thresholds

- **Categories:** edit `tools/categories.yaml`. Each entry has a key, a label, a `clothing` flag and several prompts; the prompt embeddings are L2-normalized, averaged and normalized again. Adding a category is one block. Push, and CI re-runs `tools/export_models.py` because its cache key includes the YAML. The app sees a new `labels_version` and **re-labels from the stored embeddings** without re-running the image model, so it's fast.
- **Export by hand:**
  - `pip install -r tools/requirements.txt && python tools/export_models.py`
  - or Colab: `tools/export_models_colab.ipynb`, which also has a one-image classify cell for testing prompts
- **Face model:** `tools/fetch_face_model.sh`, which verifies the hashes of both the archive and the model.
- **Thresholds and constants:**
  - Clothing confidence (default **0.5**): `DEFAULT_CLOTHING_THRESHOLD` in `ml/ZeroShot.kt`. Settings overrides it at query time, so no re-scan is needed.
  - Face grouping: `FaceClustering.kt`.
    - Assign threshold: `ASSIGN_STRICT` 0.55 → `ASSIGN_LOOSE` 0.35, from the sensitivity slider; the default is 0.45.
    - Merge threshold: assign threshold − `MERGE_OFFSET` (0.05).
  - Face quality filter: `FaceQuality` (`FaceGeometry.kt`).
    - minimum face width 10% of the image and at least 40 px
    - |yaw| ≤ 35°, |pitch| ≤ 30°, |roll| ≤ 45°
    - ML Kit gives no detection confidence, so pose and size are used instead
- **Versioning and targeted re-scans:**
  - `encoder_version` change: the clothing model re-runs.
  - `labels_version` change: photos are re-labelled from stored embeddings.
  - `FaceAnalyzer.MODEL_VERSION` change: face detection and embedding re-run.
  - Each stage is resumable and keyed on `(assetId, dateModified, version)`.

## Face grouping behaviour

- **Incremental:** each new face joins the most similar person (cosine to the person's mean) if it is above the assign threshold; otherwise it becomes a new person.
- **Merge pass** after every scan, and immediately when sensitivity is changed: agglomerative clustering with average linkage. It merges people split by angle or lighting.
- **Stable IDs:** people are only ever merged, never split or renumbered. The survivor keeps its ID; the order of preference is named, then larger, then older.
- **Your corrections are data the algorithm respects:**
  - Names and hidden flags persist.
  - Two named people are never auto-merged.
  - "Not this person" stores an exclusion that the merge pass never violates.
  - Your merges and moves are kept.
- A photo with several faces appears under each recognized person.

## Development without a local Flutter SDK

CI (`.github/workflows/build.yml`) runs these steps:
1. Model export and download, cached
2. `flutter pub get`
3. Pigeon codegen
4. `flutter analyze`
5. `flutter test`
6. `flutter build apk --release` (arm64)
7. Kotlin unit tests: JUnit for the math, clustering and geometry; Robolectric with in-memory Room for the index stage and the people corrections

It then publishes the GitHub Release and artifact.

Every step runs through `tools/ci/run.sh`, which posts the relevant log lines as a GitHub error annotation on failure. Annotations can be read without logging in.

Toolchain, pinned in `android/settings.gradle.kts`:
- AGP 8.13.2, Kotlin 2.2.20, KSP 2.3.12, Gradle 8.14.3
- compile/target SDK 36, minSdk 26
- arm64-v8a only
- no R8, so reflection-heavy native libraries keep working without keep rules

The signing key `android/app/debug.keystore` is a throwaway PKCS12 key (alias `androiddebugkey`, password `android`), committed on purpose.

## Known limitations

- **Whole-image classification:** clothing classification looks at the whole image. It struggles when the person is small in the frame, in group photos with mixed outfits, and with mirror selfies dominated by the room. `RegionProvider` in `ClothingClassifier.kt` is the hook for a later "person crop" stage, which would classify each detected person and keep the best.
- **Zero-shot accuracy:** prompts work well for distinctive garments (saree, abaya, suit) and less well for close pairs such as kurta vs shalwar kameez. Use the ⓘ sheet and **Low confidence** to tune prompts or the threshold. Each photo's raw embedding is stored, so a user-trained linear probe can be added later without re-running the model.
- **Face grouping is imperfect:**
  - Babies, heavy makeup and big age gaps may split one person into several.
  - Look-alike relatives may merge.
  - Use rename, merge and "not this person". Profiles and tiny faces are skipped on purpose.
- **Videos** are browsed only, not classified or face-scanned. A video poster frame could be added later.
- **Background limits:**
  - Android may delay the automatic re-scan.
  - On Android 12+, a re-scan started in the background can't show the foreground notification and may be stopped after about 10 minutes; it resumes next time.
  - Android 15 limits `dataSync` foreground work to 6 h a day.
- **Schema upgrades:** the database schema went from v1 to v2 destructively, because v1 held only the rebuildable index. Future versions must add real migrations, since v2 holds your corrections.
- **Locked dependencies:** `pubspec.lock` is generated in CI, not committed.
