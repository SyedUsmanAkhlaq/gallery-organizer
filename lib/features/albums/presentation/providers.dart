import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/paging.dart';
import '../../permissions/presentation/permission_controller.dart';
import '../data/photo_manager_media_repository.dart';
import '../domain/media.dart';

final mediaRepositoryProvider = Provider<MediaRepository>(
  (ref) => PhotoManagerMediaRepository(),
);

final albumsProvider = FutureProvider<List<Album>>((ref) {
  ref.watch(galleryEpochProvider);
  return ref.read(mediaRepositoryProvider).fetchAlbums();
});

final albumMediaProvider = NotifierProvider.autoDispose
    .family<AlbumMediaNotifier, PagedState<MediaItem>, String>(
  AlbumMediaNotifier.new,
);

/// Paged contents of one album. Kept alive while the album grid (and the
/// viewer pushed on top of it) is on screen.
class AlbumMediaNotifier extends Notifier<PagedState<MediaItem>> {
  AlbumMediaNotifier(this.albumId);

  final String albumId;

  @override
  PagedState<MediaItem> build() {
    ref.watch(galleryEpochProvider);
    Future.microtask(loadMore);
    return PagedState<MediaItem>.initial();
  }

  Future<void> loadMore() async {
    final current = state;
    if (current.isLoading || !current.hasMore) return;
    state = current.loading();
    try {
      final page = await ref.read(mediaRepositoryProvider).fetchPage(
            albumId,
            page: current.nextPage,
            size: kPageSize,
          );
      if (!ref.mounted) return;
      state = appendPage(state, page, kPageSize);
    } catch (e) {
      if (!ref.mounted) return;
      state = state.failed(e);
    }
  }

  /// Clears a previous error and tries the failed page again.
  Future<void> retry() async {
    state = PagedState<MediaItem>(
      items: state.items,
      nextPage: state.nextPage,
      hasMore: true,
      isLoading: false,
    );
    await loadMore();
  }
}
