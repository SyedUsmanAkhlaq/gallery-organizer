import 'package:flutter_test/flutter_test.dart';
import 'package:gallery_organizer/core/tap_counter.dart';

void main() {
  final t0 = DateTime(2026);
  DateTime at(int ms) => t0.add(Duration(milliseconds: ms));

  test('fires on the 7th rapid tap and resets', () {
    final c = TapCounter();
    for (var i = 0; i < 6; i++) {
      expect(c.tap(at(i * 200)), isFalse);
    }
    expect(c.remaining, 1);
    expect(c.tap(at(1200)), isTrue);
    expect(c.remaining, 7);
  });

  test('a slow tap restarts the count', () {
    final c = TapCounter(target: 3, window: const Duration(seconds: 1));
    expect(c.tap(at(0)), isFalse);
    expect(c.tap(at(500)), isFalse);
    expect(c.tap(at(3000)), isFalse); // too slow: count restarts at 1
    expect(c.tap(at(3500)), isFalse);
    expect(c.tap(at(4000)), isTrue);
  });
}
