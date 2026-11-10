import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/router.dart';
import '../../permissions/presentation/limited_access_banner.dart';
import '../../permissions/presentation/permission_controller.dart';
import '../domain/media.dart';
import 'media_thumbnail.dart';
import 'providers.dart';

class AlbumsScreen extends ConsumerWidget {
  const AlbumsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final albums = ref.watch(albumsProvider);
    final epoch = ref.watch(galleryEpochProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Albums')),
      body: RefreshIndicator(
        onRefresh: () async => ref.read(galleryEpochProvider.notifier).bump(),
        child: albums.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _ErrorView(
            message: 'Could not read albums: $error',
            onRetry: () => ref.invalidate(albumsProvider),
          ),
          data: (list) => ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            itemCount: list.length + 1,
            itemBuilder: (context, index) {
              if (index == 0) {
                return list.isEmpty
                    ? const Column(children: [
                        LimitedAccessBanner(),
                        Padding(
                          padding: EdgeInsets.all(32),
                          child: Text('No photos or videos found.'),
                        ),
                      ])
                    : const LimitedAccessBanner();
              }
              final album = list[index - 1];
              return AlbumTile(
                key: ValueKey('${album.id}#$epoch'),
                album: album,
              );
            },
          ),
        ),
      ),
    );
  }
}

/// One album row. Count and cover are loaded lazily per row via
/// [FutureBuilder]; the futures are created once in [initState] so scrolling
/// does not re-query MediaStore.
class AlbumTile extends ConsumerStatefulWidget {
  const AlbumTile({super.key, required this.album});

  final Album album;

  @override
  ConsumerState<AlbumTile> createState() => _AlbumTileState();
}

class _AlbumTileState extends ConsumerState<AlbumTile> {
  late final Future<int> _count;
  late final Future<MediaItem?> _cover;

  @override
  void initState() {
    super.initState();
    final repo = ref.read(mediaRepositoryProvider);
    _count = repo.countAssets(widget.album.id);
    _cover = repo.cover(widget.album.id);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      leading: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: SizedBox.square(
          dimension: 64,
          child: FutureBuilder<MediaItem?>(
            future: _cover,
            builder: (context, snap) {
              final item = snap.data;
              if (item == null) {
                return ColoredBox(
                  color: theme.colorScheme.surfaceContainerHighest,
                  child: const Icon(Icons.photo_outlined),
                );
              }
              return MediaThumbnail(item: item);
            },
          ),
        ),
      ),
      title: Text(widget.album.name, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: FutureBuilder<int>(
        future: _count,
        builder: (context, snap) => Text(
          snap.hasData
              ? '${snap.data} items'
              : snap.hasError
                  ? 'Count unavailable'
                  : '…',
        ),
      ),
      onTap: () => context.push(Routes.album(widget.album.id, widget.album.name)),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(32),
      children: [
        Text(message, textAlign: TextAlign.center),
        const SizedBox(height: 16),
        Center(
          child: FilledButton(onPressed: onRetry, child: const Text('Retry')),
        ),
      ],
    );
  }
}
