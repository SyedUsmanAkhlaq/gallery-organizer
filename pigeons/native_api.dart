// Pigeon contract between Flutter and the Kotlin native layer.
//
// Code generation (CI does this before analyze/build; outputs are gitignored):
//   dart run pigeon --input pigeons/native_api.dart
//
// Rules:
// - Only small data crosses the bridge: IDs, labels, scores, counts, timings.
//   Never image bytes or embeddings.
// - Asset IDs are MediaStore `_ID` values as decimal strings (same value as
//   photo_manager's `AssetEntity.id` on Android).
import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/bridge/native_api.g.dart',
    dartOptions: DartOptions(),
    kotlinOut:
        'android/app/src/main/kotlin/dev/personal/galleryorganizer/bridge/NativeApi.g.kt',
    kotlinOptions: KotlinOptions(package: 'dev.personal.galleryorganizer.bridge'),
    dartPackageName: 'gallery_organizer',
  ),
)
enum ScanState { idle, running, paused, completed, failed }

/// `indexing` = MediaStore enumeration (M2), `clothing` (M3), `faces` (M4).
enum ScanStage { indexing, clothing, faces }

class ScanProgressMsg {
  ScanProgressMsg({
    required this.state,
    required this.stage,
    required this.scanned,
    required this.total,
    required this.avgMsPerImage,
  });

  ScanState state;
  ScanStage stage;
  int scanned;
  int total;
  String? currentAssetId;
  double avgMsPerImage;

  /// Short human-readable status or error summary (no paths).
  String? message;
}

class StageStatsMsg {
  StageStatsMsg({
    required this.stage,
    required this.processed,
    required this.avgMsPerImage,
  });

  ScanStage stage;

  /// Items processed in the last completed run of this stage.
  int processed;
  double avgMsPerImage;

  /// Inference backend in use (CPU / GPU / NNAPI); null for non-ML stages.
  String? delegate;
  int? lastRunAtMillis;
}

class DebugInfoMsg {
  DebugInfoMsg({
    required this.indexedImages,
    required this.indexedVideos,
    required this.dbSizeBytes,
    required this.workState,
    required this.stages,
    required this.errorCount,
    required this.clothingProcessed,
    required this.faceScanned,
    required this.faceCount,
    required this.peopleCount,
    required this.faceModelVersion,
    required this.categories,
  });

  int clothingProcessed;
  int faceScanned;
  int faceCount;
  int peopleCount;
  String? clothingEncoderVersion;
  String? clothingLabelsVersion;
  String faceModelVersion;

  /// All categories with counts at the current threshold (incl. negatives).
  List<CategorySummaryMsg> categories;

  int indexedImages;
  int indexedVideos;
  int dbSizeBytes;

  /// WorkManager state of the unique scan work (e.g. RUNNING, ENQUEUED).
  String workState;
  List<StageStatsMsg> stages;
  int errorCount;
}

class ErrorEntryMsg {
  ErrorEntryMsg({
    required this.timestampMillis,
    required this.source,
    required this.message,
  });

  int timestampMillis;
  String source;
  String message;
  String? stack;
}

class AppInfoMsg {
  AppInfoMsg({
    required this.versionName,
    required this.versionCode,
    required this.sdkInt,
    required this.device,
  });

  String versionName;
  int versionCode;
  int sdkInt;
  String device;
}

class IdCheckMsg {
  IdCheckMsg({
    required this.checked,
    required this.matched,
    required this.missing,
  });

  int checked;
  int matched;
  List<String> missing;
}

/// Category card. Special key `_uncertain`: clothing photos whose best
/// score is below the confidence threshold.
class CategorySummaryMsg {
  CategorySummaryMsg({
    required this.key,
    required this.label,
    required this.isClothing,
    required this.count,
    required this.coverWidth,
    required this.coverHeight,
  });

  String key;
  String label;
  bool isClothing;
  int count;
  String? coverAssetId;
  int coverWidth;
  int coverHeight;
}

/// A gallery item returned by native queries (ID + what Flutter needs to
/// build a photo_manager thumbnail).
class AssetRefMsg {
  AssetRefMsg({
    required this.assetId,
    required this.width,
    required this.height,
    required this.isVideo,
  });

  String assetId;
  int width;
  int height;
  bool isVideo;
  double? score;
  String? albumName;
}

/// Normalized square crop around a face (0..1 of image width/height).
class FaceBoxMsg {
  FaceBoxMsg({
    required this.left,
    required this.top,
    required this.right,
    required this.bottom,
  });

  double left;
  double top;
  double right;
  double bottom;
}

class PersonMsg {
  PersonMsg({
    required this.id,
    required this.hidden,
    required this.photoCount,
    required this.faceCount,
    required this.coverWidth,
    required this.coverHeight,
  });

  int id;
  String? name;
  bool hidden;
  int photoCount;
  int faceCount;
  String? coverAssetId;
  int coverWidth;
  int coverHeight;
  FaceBoxMsg? coverBox;
}

class LabelScoreMsg {
  LabelScoreMsg({required this.key, required this.label, required this.score});

  String key;
  String label;
  double score;
}

class AssetDetailsMsg {
  AssetDetailsMsg({
    required this.analyzed,
    required this.labels,
    required this.people,
  });

  bool analyzed;
  List<LabelScoreMsg> labels;
  List<PersonMsg> people;
}

class SettingsMsg {
  SettingsMsg({
    required this.clusteringSensitivity,
    required this.clothingThreshold,
    required this.showSinglePhotoPeople,
    required this.accelerator,
  });

  /// 0 = strict (fewer merges) .. 1 = loose.
  double clusteringSensitivity;

  /// Minimum top-1 score for a clothing category (default 0.5).
  double clothingThreshold;
  bool showSinglePhotoPeople;

  /// "auto" or "cpu".
  String accelerator;
}

@HostApi()
abstract class GalleryHostApi {
  /// Enqueues the background scan (no-op if already running). On Android 13+
  /// also asks for POST_NOTIFICATIONS so the progress notification shows.
  void startScan();

  /// Stops the scan. Work done so far is kept; the next start resumes.
  void pauseScan();

  @async
  ScanProgressMsg getScanStatus();

  @async
  DebugInfoMsg getDebugInfo();

  /// Checks that photo_manager IDs exist as MediaStore `_ID`s.
  @async
  IdCheckMsg verifyAssetIds(List<String> ids);

  @async
  void logError(String source, String message, String? stack);

  @async
  List<ErrorEntryMsg> getRecentErrors();

  @async
  void clearErrors();

  /// Opens the Android share sheet with the error log as a text file.
  @async
  void shareLog();

  AppInfoMsg getAppInfo();

  /// First scan if never done + automatic pick-up of new photos.
  void onAppStarted();

  // ---- Categories ----------------------------------------------------------

  @async
  List<CategorySummaryMsg> getCategorySummaries();

  @async
  List<AssetRefMsg> getAssetIdsByCategory(String category, int page, int size);

  // ---- People --------------------------------------------------------------

  @async
  List<PersonMsg> getPeople(bool includeHidden, bool includeSingles);

  @async
  List<AssetRefMsg> getAssetIdsByPerson(int personId, int page, int size);

  @async
  void renamePerson(int personId, String? name);

  /// Moves everything of [sourceId] into [targetId].
  @async
  void mergePeople(int sourceId, int targetId);

  /// "Not this person": the photo's faces leave [personId] for
  /// [targetPersonId], or a new person when null.
  @async
  void removeFaceFromPerson(int personId, String assetId, int? targetPersonId);

  @async
  void setPersonHidden(int personId, bool hidden);

  /// Saves the sensitivity and re-runs the merge pass; returns merges made.
  @async
  int setClusteringSensitivity(double sensitivity);

  // ---- Misc ----------------------------------------------------------------

  @async
  AssetDetailsMsg getAssetDetails(String assetId);

  @async
  SettingsMsg getSettings();

  @async
  void updateSettings(SettingsMsg settings);

  /// Stops the scan and deletes every analysis table (keeps settings).
  @async
  void deleteAllAnalysisData();
}

@EventChannelApi()
abstract class GalleryEvents {
  /// Live scan progress, throttled on the native side.
  ScanProgressMsg scanProgress();
}
