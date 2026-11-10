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

  /// True while the current photo is zoomed in or two fingers are down.
  /// Paging is disabled then, otherwise the PageView's horizontal drag wins
  /// the gesture arena and pinch-zoom never starts.
  bool _pagingLocked = false;

  void _setPagingLocked(bool locked) {
    if (locked != _pagingLocked) setState(() => _pagingLocked = locked);
  }

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
              physics: _pagingLocked
                  ? const NeverScrollableScrollPhysics()
                  : null,
              itemCount: items.length,
              onPageChanged: (i) {
                setState(() {
                  _index = i;
                  _pagingLocked = false;
                });
                if (shouldPrefetch<MediaItem>(paged, i)) {
                  ref.read(provider.notifier).loadMore();
                }
              },
              itemBuilder: (context, i) {
                final item = items[i];
                return item.isVideo
                    ? _VideoPage(key: ValueKey('v${item.id}'), item: item)
                    : _PhotoPage(
                        key: ValueKey('p${item.id}'),
                        item: item,
                        onLockChanged: _setPagingLocked,
                      );
              },
            ),
    );
  }
}

/// Max zoom for photos, and the zoom level a double-tap jumps to.
const double kMaxPhotoZoom = 5;
const double kDoubleTapZoom = 2.5;

class _PhotoPage extends StatefulWidget {
  const _PhotoPage({
    super.key,
    required this.item,
    required this.onLockChanged,
  });

  final MediaItem item;
  final ValueChanged<bool> onLockChanged;

  @override
  State<_PhotoPage> createState() => _PhotoPageState();
}

class _PhotoPageState extends State<_PhotoPage> {
  final _transform = TransformationController();
  int _pointers = 0;
  Offset _doubleTapAt = Offset.zero;

  bool get _zoomed => _transform.value.getMaxScaleOnAxis() > 1.01;

  void _reportLock() => widget.onLockChanged(_pointers >= 2 || _zoomed);

  void _pointerDown(PointerDownEvent _) {
    _pointers++;
    _reportLock();
  }

  void _pointerUp(PointerEvent _) {
    _pointers = (_pointers - 1).clamp(0, 10);
    _reportLock();
  }

  void _toggleZoom() {
    if (_zoomed) {
      _transform.value = Matrix4.identity();
    } else {
      // Scale around the tapped point: x' = s*x + (1 - s)*p.
      const s = kDoubleTapZoom;
      final p = _doubleTapAt;
      _transform.value = Matrix4.identity()
        ..setEntry(0, 0, s)
        ..setEntry(1, 1, s)
        ..setEntry(0, 3, (1 - s) * p.dx)
        ..setEntry(1, 3, (1 - s) * p.dy);
    }
    _reportLock();
  }

  @override
  void dispose() {
    _transform.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    return Listener(
      onPointerDown: _pointerDown,
      onPointerUp: _pointerUp,
      onPointerCancel: _pointerUp,
      child: GestureDetector(
        onDoubleTapDown: (d) => _doubleTapAt = d.localPosition,
        onDoubleTap: _toggleZoom,
        child: InteractiveViewer(
          transformationController: _transform,
          maxScale: kMaxPhotoZoom,
          onInteractionEnd: (_) => _reportLock(),
          child: SizedBox.expand(
            child: Image(
              image: viewerImageProvider(item),
              fit: BoxFit.contain,
              gaplessPlayback: true,
              // Show the (already cached) grid thumbnail until the large
              // image is decoded.
              frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
                if (wasSynchronouslyLoaded || frame != null) return child;
                return Image(
                  image: thumbnailProvider(item),
                  fit: BoxFit.contain,
                );
              },
              errorBuilder: (context, error, stackTrace) => const Icon(
                Icons.broken_image_outlined,
                color: Colors.white54,
                size: 64,
              ),
            ),
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
