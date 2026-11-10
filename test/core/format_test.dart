import 'package:flutter_test/flutter_test.dart';
import 'package:gallery_organizer/core/format.dart';

void main() {
  test('formatDuration', () {
    expect(formatDuration(0), '0:00');
    expect(formatDuration(5), '0:05');
    expect(formatDuration(65), '1:05');
    expect(formatDuration(600), '10:00');
    expect(formatDuration(3723), '1:02:03');
    expect(formatDuration(-3), '0:00');
  });
}
