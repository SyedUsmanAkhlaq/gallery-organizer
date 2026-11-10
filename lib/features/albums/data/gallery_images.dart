import 'package:flutter/widgets.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:photo_manager_image_provider/photo_manager_image_provider.dart';

import '../domain/media.dart';

/// Grid thumbnails: 250 px, never the original file.
const int kGridThumbnailPx = 250;

/// Viewer images: decoded via the system thumbnailer at this bound instead of
/// the original file (keeps memory flat and handles HEIC).
const int kViewerImagePx = 1920;

/// Rebuilds a lightweight [AssetEntity] from a [MediaItem]. Only the ID and
/// type are needed for photo_manager to fetch a thumbnail, which is what lets
/// IDs coming from the Kotlin side (milestone 2+) render directly.
AssetEntity assetFor(MediaItem item) => AssetEntity(
      id: item.id,
      typeInt: switch (item.kind) {
        MediaKind.image => AssetType.image.index,
        MediaKind.video => AssetType.video.index,
        MediaKind.other => AssetType.other.index,
      },
      width: item.width,
      height: item.height,
      duration: item.durationSeconds,
      orientation: item.orientation,
    );

ImageProvider thumbnailProvider(MediaItem item) => AssetEntityImageProvider(
      assetFor(item),
      isOriginal: false,
      thumbnailSize: const ThumbnailSize.square(kGridThumbnailPx),
      thumbnailFormat: ThumbnailFormat.jpeg,
    );

ImageProvider viewerImageProvider(MediaItem item) => AssetEntityImageProvider(
      assetFor(item),
      isOriginal: false,
      thumbnailSize: const ThumbnailSize.square(kViewerImagePx),
      thumbnailFormat: ThumbnailFormat.jpeg,
    );
