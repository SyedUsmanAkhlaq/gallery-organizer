import '../../../bridge/native_api.g.dart';
import '../domain/scan_snapshot.dart';

/// Thin wrapper over the Pigeon-generated API: maps generated types to the
/// domain types the UI uses.
class NativeGalleryRepository {
  NativeGalleryRepository([GalleryHostApi? api]) : _api = api ?? GalleryHostApi();

  final GalleryHostApi _api;

  GalleryHostApi get api => _api;

  Future<void> startScan() => _api.startScan();

  Future<void> pauseScan() => _api.pauseScan();

  Future<ScanSnapshot> status() async => toSnapshot(await _api.getScanStatus());

  Stream<ScanSnapshot> progress() => scanProgress().map(toSnapshot);
}

ScanSnapshot toSnapshot(ScanProgressMsg m) => ScanSnapshot(
      state: switch (m.state) {
        ScanState.idle => ScanRunState.idle,
        ScanState.running => ScanRunState.running,
        ScanState.paused => ScanRunState.paused,
        ScanState.completed => ScanRunState.completed,
        ScanState.failed => ScanRunState.failed,
      },
      stage: switch (m.stage) {
        ScanStage.indexing => ScanStageKind.indexing,
        ScanStage.clothing => ScanStageKind.clothing,
        ScanStage.faces => ScanStageKind.faces,
      },
      scanned: m.scanned,
      total: m.total,
      avgMsPerImage: m.avgMsPerImage,
      currentAssetId: m.currentAssetId,
      message: m.message,
    );
