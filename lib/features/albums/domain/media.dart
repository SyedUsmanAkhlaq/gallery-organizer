import 'dart:io';

/// A gallery album (MediaStore bucket), or the virtual "all media" album.
class Album {
  const Album({required this.id, required this.name, required this.isAll});

  final String id;
  final String name;
  final bool isAll;
}

enum MediaKind { image, video, other }

/// A single photo or video.
///
/// [id] is the MediaStore `_ID` (as a decimal string). photo_manager uses the
/// same value for `AssetEntity.id` on Android, and the Kotlin scanner
/// (milestone 2+) keys every row on it, so IDs returned from native code can
/// be turned straight back into thumbnails here.
class MediaItem {
  const MediaItem({
    required this.id,
    required this.kind,
    required this.width,
    required this.height,
    this.durationSeconds = 0,
    this.orientation = 0,
  });

  final String id;
  final MediaKind kind;
  final int width;
  final int height;
  final int durationSeconds;
  final int orientation;

  bool get isVideo => kind == MediaKind.video;
}

abstract interface class MediaRepository {
  Future<List<Album>> fetchAlbums();

  Future<int> countAssets(String albumId);

  /// First (newest) item, used as the album cover.
  Future<MediaItem?> cover(String albumId);

  Future<List<MediaItem>> fetchPage(
    String albumId, {
    required int page,
    required int size,
  });

  /// Local file for video playback.
  Future<File?> videoFile(String mediaId);
}
