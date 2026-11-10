import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/router.dart';
import '../../core/paging.dart';
import '../albums/domain/media.dart';
import '../albums/presentation/media_thumbnail.dart';
import '../scan/domain/analysis.dart';
import 'media_providers.dart';
import 'media_source.dart';

/// Paged grid (60 per page, prefetch 12 from the end) for any media source.
/// Tapping opens the swipeable viewer over the same list.
class MediaGrid extends ConsumerWidget {
  const MediaGrid({
    super.key,
    required this.source,
    this.onLongPress,
    this.showAlbumName = false,
    this.showScore = false,
    this.emptyText = 'Nothing here yet.',
  });

  final MediaSourceKey source;
  final void Function(MediaItem item)? onLongPress;
  final bool showAlbumName;
  final bool showScore;
  final String emptyText;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final provider = mediaPageProvider(source.encoded);
    final paged = ref.watch(provider);
    final notifier = ref.read(provider.notifier);

    return RefreshIndicator(
      onRefresh: notifier.reload,
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.all(2),
            sliver: SliverGrid.builder(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 4,
                mainAxisSpacing: 2,
                crossAxisSpacing: 2,
              ),
              itemCount: paged.items.length,
              itemBuilder: (context, index) {
                if (shouldPrefetch<MediaItem>(paged, index)) {
                  // Never mutate providers during build.
                  WidgetsBinding.instance
                      .addPostFrameCallback((_) => notifier.loadMore());
                }
                final item = paged.items[index];
                return GestureDetector(
                  onTap: () =>
                      context.push(Routes.viewer(source.encoded, index)),
                  onLongPress:
                      onLongPress == null ? null : () => onLongPress!(item),
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      MediaThumbnail(key: ValueKey(item.id), item: item),
                      if (showScore && item.score != null)
                        Positioned(
                          top: 3,
                          right: 3,
                          child: _Chip(formatScore(item.score!)),
                        ),
                      if (showAlbumName && item.albumName != null)
                        Positioned(
                          left: 0,
                          right: 0,
                          bottom: 0,
                          child: _AlbumLabel(item.albumName!),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),
          SliverToBoxAdapter(
            child: _Footer(
              paged: paged,
              onRetry: notifier.retry,
              emptyText: emptyText,
            ),
          ),
        ],
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
        child: Text(
          text,
          style: const TextStyle(color: Colors.white, fontSize: 10),
        ),
      ),
    );
  }
}

class _AlbumLabel extends StatelessWidget {
  const _AlbumLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Colors.transparent, Colors.black87],
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(4, 8, 4, 2),
        child: Text(
          text,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(color: Colors.white, fontSize: 9),
        ),
      ),
    );
  }
}

class _Footer extends StatelessWidget {
  const _Footer({
    required this.paged,
    required this.onRetry,
    required this.emptyText,
  });

  final PagedState<MediaItem> paged;
  final VoidCallback onRetry;
  final String emptyText;

  @override
  Widget build(BuildContext context) {
    if (paged.error != null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Text('Loading failed: ${paged.error}', textAlign: TextAlign.center),
            TextButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      );
    }
    if (paged.isLoading) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (!paged.hasMore && paged.items.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(32),
        child: Center(child: Text(emptyText, textAlign: TextAlign.center)),
      );
    }
    return const SizedBox(height: 24);
  }
}
