/// Flutter-side view of scan progress (mirrors the Pigeon message, but keeps
/// presentation and tests independent of generated code).
enum ScanRunState { idle, running, paused, completed, failed }

enum ScanStageKind { indexing, clothing, faces }

class ScanSnapshot {
  const ScanSnapshot({
    required this.state,
    required this.stage,
    required this.scanned,
    required this.total,
    required this.avgMsPerImage,
    this.currentAssetId,
    this.message,
  });

  static const unknown = ScanSnapshot(
    state: ScanRunState.idle,
    stage: ScanStageKind.indexing,
    scanned: 0,
    total: 0,
    avgMsPerImage: 0,
  );

  final ScanRunState state;
  final ScanStageKind stage;
  final int scanned;
  final int total;
  final double avgMsPerImage;
  final String? currentAssetId;
  final String? message;

  bool get isRunning => state == ScanRunState.running;

  /// 0..1, or null when the total is not known yet (indeterminate).
  double? get fraction =>
      total <= 0 ? null : (scanned / total).clamp(0.0, 1.0).toDouble();

  /// Remaining time estimate from the average per-item time.
  Duration? get eta {
    if (!isRunning || total <= 0 || avgMsPerImage <= 0) return null;
    final remaining = total - scanned;
    if (remaining <= 0) return Duration.zero;
    return Duration(milliseconds: (remaining * avgMsPerImage).round());
  }
}

String stageLabel(ScanStageKind stage) => switch (stage) {
      ScanStageKind.indexing => 'Indexing',
      ScanStageKind.clothing => 'Clothing',
      ScanStageKind.faces => 'Faces',
    };

String describeSnapshot(ScanSnapshot s) {
  final counts = s.total > 0 ? ' ${s.scanned} / ${s.total}' : '';
  return switch (s.state) {
    ScanRunState.idle => s.message ?? 'Not scanned yet',
    ScanRunState.running => '${stageLabel(s.stage)}$counts',
    ScanRunState.paused => 'Paused$counts',
    ScanRunState.completed => 'Up to date$counts',
    ScanRunState.failed => 'Failed: ${s.message ?? 'unknown error'}',
  };
}

String formatEta(Duration d) {
  if (d.inHours > 0) return '${d.inHours} h ${d.inMinutes % 60} min';
  if (d.inMinutes > 0) return '${d.inMinutes} min';
  return '${d.inSeconds} s';
}
