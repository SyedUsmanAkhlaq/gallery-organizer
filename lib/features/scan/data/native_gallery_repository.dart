import '../../../bridge/native_api.g.dart';
import '../../albums/domain/media.dart';
import '../domain/analysis.dart';
import '../domain/scan_snapshot.dart';

/// Wrapper over the Pigeon-generated API: maps generated types to the
/// domain types the UI uses. Only IDs/labels/scores/counts cross the bridge.
class NativeGalleryRepository {
  NativeGalleryRepository([GalleryHostApi? api]) : _api = api ?? GalleryHostApi();

  final GalleryHostApi _api;

  GalleryHostApi get api => _api;

  Future<void> onAppStarted() => _api.onAppStarted();

  Future<void> startScan() => _api.startScan();

  Future<void> pauseScan() => _api.pauseScan();

  Future<ScanSnapshot> status() async => toSnapshot(await _api.getScanStatus());

  Stream<ScanSnapshot> progress() => scanProgress().map(toSnapshot);

  // ---- categories ----

  Future<List<CategorySummary>> categories() async => [
        for (final c in await _api.getCategorySummaries())
          CategorySummary(
            key: c.key,
            label: c.label,
            isClothing: c.isClothing,
            count: c.count,
            cover: _cover(c.coverAssetId, c.coverWidth, c.coverHeight),
          ),
      ];

  Future<List<MediaItem>> categoryPage(String key, int page, int size) async =>
      (await _api.getAssetIdsByCategory(key, page, size)).map(toMediaItem).toList();

  // ---- people ----

  /// [includeSingles]: also people seen in only one photo (pickers, titles).
  Future<List<Person>> people({bool includeHidden = false, bool includeSingles = false}) async =>
      (await _api.getPeople(includeHidden, includeSingles)).map(toPerson).toList();

  Future<List<MediaItem>> personPage(int personId, int page, int size) async =>
      (await _api.getAssetIdsByPerson(personId, page, size)).map(toMediaItem).toList();

  Future<void> renamePerson(int id, String? name) => _api.renamePerson(id, name);

  Future<void> mergePeople(int sourceId, int targetId) => _api.mergePeople(sourceId, targetId);

  Future<void> removeFromPerson(int personId, String assetId, {int? moveTo}) =>
      _api.removeFaceFromPerson(personId, assetId, moveTo);

  Future<void> setPersonHidden(int id, bool hidden) => _api.setPersonHidden(id, hidden);

  // ---- misc ----

  Future<AssetDetails> details(String assetId) async {
    final d = await _api.getAssetDetails(assetId);
    return AssetDetails(
      analyzed: d.analyzed,
      labels: [for (final l in d.labels) LabelScore(l.key, l.label, l.score)],
      people: d.people.map(toPerson).toList(),
    );
  }

  Future<AnalysisSettings> settings() async {
    final s = await _api.getSettings();
    return AnalysisSettings(
      clusteringSensitivity: s.clusteringSensitivity,
      clothingThreshold: s.clothingThreshold,
      showSinglePhotoPeople: s.showSinglePhotoPeople,
      useAcceleration: s.accelerator != 'cpu',
    );
  }

  Future<void> saveSettings(AnalysisSettings s) => _api.updateSettings(SettingsMsg(
        clusteringSensitivity: s.clusteringSensitivity,
        clothingThreshold: s.clothingThreshold,
        showSinglePhotoPeople: s.showSinglePhotoPeople,
        accelerator: s.useAcceleration ? 'auto' : 'cpu',
      ));

  /// Returns how many groups were merged with the new sensitivity.
  Future<int> setClusteringSensitivity(double v) => _api.setClusteringSensitivity(v);

  Future<void> deleteAllAnalysisData() => _api.deleteAllAnalysisData();

  static CoverRef? _cover(String? id, int w, int h) =>
      id == null ? null : CoverRef(assetId: id, width: w, height: h);
}

MediaItem toMediaItem(AssetRefMsg m) => MediaItem(
      id: m.assetId,
      kind: m.isVideo ? MediaKind.video : MediaKind.image,
      width: m.width,
      height: m.height,
      score: m.score,
      albumName: m.albumName,
    );

Person toPerson(PersonMsg p) => Person(
      id: p.id,
      name: p.name,
      hidden: p.hidden,
      photoCount: p.photoCount,
      cover: p.coverAssetId == null
          ? null
          : CoverRef(assetId: p.coverAssetId!, width: p.coverWidth, height: p.coverHeight),
      coverBox: p.coverBox == null
          ? null
          : FaceBox(p.coverBox!.left, p.coverBox!.top, p.coverBox!.right, p.coverBox!.bottom),
    );

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
