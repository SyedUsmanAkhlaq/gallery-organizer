import 'package:flutter_test/flutter_test.dart';
import 'package:gallery_organizer/features/scan/domain/scan_snapshot.dart';

ScanSnapshot snap({
  ScanRunState state = ScanRunState.running,
  int scanned = 0,
  int total = 0,
  double avg = 0,
  String? message,
}) =>
    ScanSnapshot(
      state: state,
      stage: ScanStageKind.indexing,
      scanned: scanned,
      total: total,
      avgMsPerImage: avg,
      message: message,
    );

void main() {
  test('fraction is null until total is known, then clamped', () {
    expect(snap().fraction, isNull);
    expect(snap(scanned: 25, total: 100).fraction, 0.25);
    expect(snap(scanned: 120, total: 100).fraction, 1.0);
  });

  test('eta from average ms per item, only while running', () {
    expect(snap(scanned: 0, total: 1000, avg: 120).eta,
        const Duration(seconds: 120));
    expect(snap(state: ScanRunState.paused, total: 10, avg: 5).eta, isNull);
    expect(snap(total: 10, avg: 0).eta, isNull);
  });

  test('describe and formatEta', () {
    expect(describeSnapshot(snap(scanned: 3, total: 9)), 'Indexing 3 / 9');
    expect(describeSnapshot(snap(state: ScanRunState.failed, message: 'X')),
        'Failed: X');
    expect(formatEta(const Duration(minutes: 75)), '1 h 15 min');
    expect(formatEta(const Duration(seconds: 42)), '42 s');
  });
}
