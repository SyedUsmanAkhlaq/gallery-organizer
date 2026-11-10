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
  });

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
}

@EventChannelApi()
abstract class GalleryEvents {
  /// Live scan progress, throttled on the native side.
  ScanProgressMsg scanProgress();
}
