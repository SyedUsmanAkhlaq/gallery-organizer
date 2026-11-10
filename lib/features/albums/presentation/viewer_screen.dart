import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:video_player/video_player.dart';

import '../../../core/format.dart';
import '../../../core/paging.dart';
import '../data/gallery_images.dart';
import '../domain/media.dart';
import 'providers.dart';

/// Full-screen swipeable viewer over the same paged list as the album grid.
class ViewerScreen extends ConsumerStatefulWidget {
  const ViewerScreen({
    super.key,
    required this.albumId,
    required this.initialIndex,
  });

  final String albumId;
  final int initialIndex;

  @override
  ConsumerState<ViewerScreen> createState() => _ViewerScreenState();
}

class _ViewerScreenState extends ConsumerState<ViewerScreen> {
  late final PageController _controller =
      PageController(initialPage: widget.initialIndex);
  late int _index = widget.initialIndex;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final provider = albumMediaProvider(widget.albumId);
    final paged = ref.watch(provider);
    final items = paged.items;

    return Scaffold(
      backgroundColor: Colors.black,
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        backgroundColor: Colors.black38,
        foregroundColor: Colors.white,
        title: Text(
          items.isEmpty
              ? ''
              : '${_index + 1} / ${items.length}${paged.hasMore ? '+' : ''}',
        ),
      ),
      body: items.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : PageView.builder(
              controller: _controller,
              itemCount: items.length,
              onPageChanged: (i) {
                setState(() => _index = i);
                if (shouldPrefetch<MediaItem>(paged, i)) {
                  ref.read(provider.notifier).loadMore();
                }
              },
              itemBuilder: (context, i) {
                final item = items[i];
                return item.isVideo
                    ? _VideoPage(key: ValueKey('v${item.id}'), item: item)
                    : _PhotoPage(key: ValueKey('p${item.id}'), item: item);
              },
            ),
    );
  }
}

class _PhotoPage extends StatelessWidget {
  const _PhotoPage({super.key, required this.item});

  final MediaItem item;

  @override
  Widget build(BuildContext context) {
    return InteractiveViewer(
      maxScale: 5,
      child: Center(
        child: Image(
          image: viewerImageProvider(item),
          fit: BoxFit.contain,
          gaplessPlayback: true,
          // Show the (already cached) grid thumbnail until the large image
          // is decoded.
          frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
            if (wasSynchronouslyLoaded || frame != null) return child;
            return Image(image: thumbnailProvider(item), fit: BoxFit.contain);
          },
          errorBuilder: (context, error, stackTrace) => const Icon(
            Icons.broken_image_outlined,
            color: Colors.white54,
            size: 64,
          ),
        ),
      ),
    );
  }
}

class _VideoPage extends ConsumerStatefulWidget {
  const _VideoPage({super.key, required this.item});

  final MediaItem item;

  @override
  ConsumerState<_VideoPage> createState() => _VideoPageState();
}

class _VideoPageState extends ConsumerState<_VideoPage> {
  VideoPlayerController? _video;
  bool _starting = false;
  String? _error;

  Future<void> _start() async {
    if (_starting) return;
    setState(() => _starting = true);
    try {
      final file =
          await ref.read(mediaRepositoryProvider).videoFile(widget.item.id);
      if (file == null) throw StateError('Video file not accessible');
      final controller = VideoPlayerController.file(file);
      await controller.initialize();
      if (!mounted) {
        await controller.dispose();
        return;
      }
      await controller.setLooping(true);
      await controller.play();
      setState(() => _video = controller);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _starting = false);
    }
  }

  @override
  void dispose() {
    _video?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final video = _video;
    if (video != null) {
      return GestureDetector(
        onTap: () async {
          if (video.value.isPlaying) {
            await video.pause();
          } else {
            await video.play();
          }
          if (mounted) setState(() {});
        },
        child: Stack(
          alignment: Alignment.bottomCenter,
          children: [
            Center(
              child: AspectRatio(
                aspectRatio: video.value.aspectRatio,
                child: VideoPlayer(video),
              ),
            ),
            SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: VideoProgressIndicator(video, allowScrubbing: true),
              ),
            ),
          ],
        ),
      );
    }
    return Stack(
      fit: StackFit.expand,
      children: [
        Image(
          image: viewerImageProvider(widget.item),
          fit: BoxFit.contain,
          errorBuilder: (context, error, stackTrace) => const SizedBox.shrink(),
        ),
        Center(
          child: _starting
              ? const CircularProgressIndicator()
              : IconButton.filled(
                  iconSize: 48,
                  onPressed: _start,
                  icon: const Icon(Icons.play_arrow),
                ),
        ),
        Positioned(
          left: 16,
          right: 16,
          bottom: 32,
          child: Text(
            _error ?? formatDuration(widget.item.durationSeconds),
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colors.white70),
          ),
        ),
      ],
    );
  }
}
