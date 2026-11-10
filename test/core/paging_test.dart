import 'package:flutter_test/flutter_test.dart';
import 'package:gallery_organizer/core/paging.dart';

void main() {
  group('appendPage', () {
    test('full page keeps hasMore and advances page', () {
      final s = appendPage(PagedState<int>.initial(), List.filled(60, 1), 60);
      expect(s.items.length, 60);
      expect(s.nextPage, 1);
      expect(s.hasMore, isTrue);
      expect(s.isLoading, isFalse);
    });

    test('short page marks the end', () {
      var s = appendPage(PagedState<int>.initial(), List.filled(60, 1), 60);
      s = appendPage(s, List.filled(7, 2), 60);
      expect(s.items.length, 67);
      expect(s.nextPage, 2);
      expect(s.hasMore, isFalse);
    });

    test('empty first page means empty album', () {
      final s = appendPage(PagedState<int>.initial(), const <int>[], 60);
      expect(s.items, isEmpty);
      expect(s.hasMore, isFalse);
    });

    test('clears loading and error', () {
      final loading = PagedState<int>.initial().failed('x').loading();
      expect(loading.error, isNull);
      final s = appendPage(loading, const [1], 60);
      expect(s.isLoading, isFalse);
      expect(s.error, isNull);
    });
  });

  group('shouldPrefetch', () {
    final loaded = appendPage(PagedState<int>.initial(), List.filled(60, 0), 60);

    test('triggers within threshold of the end', () {
      expect(shouldPrefetch(loaded, 47), isFalse);
      expect(shouldPrefetch(loaded, 48), isTrue); // 60 - 12
      expect(shouldPrefetch(loaded, 59), isTrue);
    });

    test('not while loading, after error, or when exhausted', () {
      expect(shouldPrefetch(loaded.loading(), 59), isFalse);
      expect(shouldPrefetch(loaded.failed('boom'), 59), isFalse);
      final done = appendPage(loaded, const [1], 60);
      expect(shouldPrefetch(done, 60), isFalse);
    });

    test('empty initial state prefetches immediately', () {
      expect(shouldPrefetch(PagedState<int>.initial(), 0), isTrue);
    });
  });
}
