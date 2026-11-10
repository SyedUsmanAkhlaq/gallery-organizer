import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../data/gallery_images.dart';
import '../domain/media.dart';

/// Square grid cell: 250 px thumbnail plus a duration badge for videos.
class MediaThumbnail extends StatelessWidget {
  const MediaThumbnail({super.key, required this.item});

  final MediaItem item;

  @override
  Widget build(BuildContext context) {
    final placeholder = Theme.of(context).colorScheme.surfaceContainerHighest;
    return Stack(
      fit: StackFit.expand,
      children: [
        ColoredBox(color: placeholder),
        Image(
          image: thumbnailProvider(item),
          fit: BoxFit.cover,
          gaplessPlayback: true,
          errorBuilder: (context, error, stackTrace) =>
              const Center(child: Icon(Icons.broken_image_outlined)),
        ),
        if (item.isVideo)
          Positioned(
            right: 4,
            bottom: 4,
            child: DurationBadge(seconds: item.durationSeconds),
          ),
      ],
    );
  }
}

class DurationBadge extends StatelessWidget {
  const DurationBadge({super.key, required this.seconds});

  final int seconds;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.play_arrow, size: 12, color: Colors.white),
            Text(
              formatDuration(seconds),
              style: const TextStyle(color: Colors.white, fontSize: 11),
            ),
          ],
        ),
      ),
    );
  }
}
