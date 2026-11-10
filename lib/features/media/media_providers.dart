import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/paging.dart';
import '../albums/domain/media.dart';
import '../albums/presentation/providers.dart';
import '../permissions/presentation/permission_controller.dart';
import '../scan/presentation/providers.dart';
import 'media_source.dart';

/// Paged contents of any media source (album, category, person), keyed by
/// [MediaSourceKey.encoded]. Shared by the grid and the viewer on top of it.
final mediaPageProvider = NotifierProvider.autoDispose
    .family<MediaPageNotifier, PagedState<MediaItem>, String>(
  MediaPageNotifier.new,
);

class MediaPageNotifier extends Notifier<PagedState<MediaItem>> {
  MediaPageNotifier(this.source);

  final String source;

  @override
  PagedState<MediaItem> build() {
    ref.watch(galleryEpochProvider);
    if (MediaSourceKey.decode(source) is! AlbumSource) {
      // Grids reload after user corrections, not on every scan tick (that
      // would make them jump while browsing). Pull to refresh for the rest.
      ref.watch(analysisEditsProvider);
    }
    Future.microtask(loadMore);
    return PagedState<MediaItem>.initial();
  }

  Future<List<MediaItem>> _fetch(int page) {
    final native = ref.read(nativeRepositoryProvider);
    return switch (MediaSourceKey.decode(source)) {
      AlbumSource(:final albumId) => ref
          .read(mediaRepositoryProvider)
          .fetchPage(albumId, page: page, size: kPageSize),
      CategorySource(:final key) => native.categoryPage(key, page, kPageSize),
      PersonSource(:final personId) =>
        native.personPage(personId, page, kPageSize),
    };
  }

  Future<void> loadMore() async {
    final current = state;
    if (current.isLoading || !current.hasMore) return;
    state = current.loading();
    try {
      final page = await _fetch(current.nextPage);
      if (!ref.mounted) return;
      state = appendPage(state, page, kPageSize);
    } catch (e) {
      if (!ref.mounted) return;
      state = state.failed(e);
    }
  }

  Future<void> retry() async {
    state = PagedState<MediaItem>(
      items: state.items,
      nextPage: state.nextPage,
      hasMore: true,
      isLoading: false,
    );
    await loadMore();
  }

  /// Drops everything and loads from the first page again.
  Future<void> reload() async {
    state = PagedState<MediaItem>.initial();
    await loadMore();
  }
}
