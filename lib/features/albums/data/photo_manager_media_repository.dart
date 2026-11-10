import 'dart:io';

import 'package:photo_manager/photo_manager.dart';

import '../domain/media.dart';

class PhotoManagerMediaRepository implements MediaRepository {
  final Map<String, AssetPathEntity> _paths = {};

  static final FilterOptionGroup _filter = FilterOptionGroup(
    orders: [const OrderOption(type: OrderOptionType.createDate, asc: false)],
  );

  @override
  Future<List<Album>> fetchAlbums() async {
    final paths = await PhotoManager.getAssetPathList(
      type: RequestType.common, // images + videos
      hasAll: true,
      filterOption: _filter,
    );
    _paths
      ..clear()
      ..addEntries(paths.map((p) => MapEntry(p.id, p)));
    return [
      for (final p in paths)
        Album(
          id: p.id,
          name: p.isAll ? 'All photos & videos' : p.name,
          isAll: p.isAll,
        ),
    ];
  }

  Future<AssetPathEntity> _path(String albumId) async {
    var path = _paths[albumId];
    if (path == null) {
      await fetchAlbums();
      path = _paths[albumId];
    }
    if (path == null) throw StateError('Album no longer exists');
    return path;
  }

  @override
  Future<int> countAssets(String albumId) async =>
      (await _path(albumId)).assetCountAsync;

  @override
  Future<MediaItem?> cover(String albumId) async {
    final list = await (await _path(albumId)).getAssetListRange(
      start: 0,
      end: 1,
    );
    return list.isEmpty ? null : toMediaItem(list.first);
  }

  @override
  Future<List<MediaItem>> fetchPage(
    String albumId, {
    required int page,
    required int size,
  }) async {
    final list = await (await _path(albumId)).getAssetListPaged(
      page: page,
      size: size,
    );
    return list.map(toMediaItem).toList(growable: false);
  }

  @override
  Future<File?> videoFile(String mediaId) async {
    final entity = await AssetEntity.fromId(mediaId);
    return entity?.file;
  }
}

MediaItem toMediaItem(AssetEntity e) => MediaItem(
      id: e.id,
      kind: switch (e.type) {
        AssetType.image => MediaKind.image,
        AssetType.video => MediaKind.video,
        _ => MediaKind.other,
      },
      width: e.width,
      height: e.height,
      durationSeconds: e.duration,
      orientation: e.orientation,
    );
