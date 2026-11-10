import 'package:flutter_test/flutter_test.dart';
import 'package:gallery_organizer/features/media/media_source.dart';
import 'package:gallery_organizer/features/scan/domain/analysis.dart';

void main() {
  test('media source keys round-trip', () {
    for (final s in const <MediaSourceKey>[
      AlbumSource('isAll'),
      AlbumSource('-1739773001'),
      CategorySource('shalwar_kameez'),
      CategorySource('_uncertain'),
      PersonSource(42),
    ]) {
      final back = MediaSourceKey.decode(s.encoded);
      expect(back.runtimeType, s.runtimeType);
      expect(back.encoded, s.encoded);
    }
    // Album IDs may themselves contain ':'.
    expect((MediaSourceKey.decode('a:x:y') as AlbumSource).albumId, 'x:y');
    expect(() => MediaSourceKey.decode('nope'), throwsFormatException);
  });

  test('formatScore and display names', () {
    expect(formatScore(0.834), '83%');
    expect(formatScore(1), '100%');
    expect(
      const Person(id: 1, name: null, hidden: false, photoCount: 3).displayName,
      'Unnamed',
    );
    expect(
      const Person(id: 1, name: 'Ali', hidden: false, photoCount: 3).displayName,
      'Ali',
    );
  });
}
