import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/router.dart';
import '../../../core/paging.dart';
import '../domain/media.dart';
import 'media_thumbnail.dart';
import 'providers.dart';

/// Paged grid of one album: 60 items per page, next page requested when the
/// grid builds a cell within 12 items of the loaded end.
class AlbumScreen extends ConsumerWidget {
  const AlbumScreen({super.key, required this.albumId, required this.title});

  final String albumId;
  final String title;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final provider = albumMediaProvider(albumId);
    final paged = ref.watch(provider);
    final notifier = ref.read(provider.notifier);

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: CustomScrollView(
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
                  onTap: () => context.push(Routes.viewer(albumId, index)),
                  child: MediaThumbnail(key: ValueKey(item.id), item: item),
                );
              },
            ),
          ),
          SliverToBoxAdapter(child: _Footer(paged: paged, onRetry: notifier.retry)),
        ],
      ),
    );
  }
}

class _Footer extends StatelessWidget {
  const _Footer({required this.paged, required this.onRetry});

  final PagedState<MediaItem> paged;
  final VoidCallback onRetry;

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
      return const Padding(
        padding: EdgeInsets.all(32),
        child: Center(child: Text('This album is empty.')),
      );
    }
    return const SizedBox(height: 24);
  }
}
