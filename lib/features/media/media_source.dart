/// What a paged media grid (and the viewer opened from it) shows. Encoded
/// as a short string so it can live in routes and provider family keys.
sealed class MediaSourceKey {
  const MediaSourceKey();

  String get encoded;

  static MediaSourceKey decode(String s) {
    final i = s.indexOf(':');
    if (i <= 0) throw FormatException('bad media source: $s');
    final value = s.substring(i + 1);
    return switch (s.substring(0, i)) {
      'a' => AlbumSource(value),
      'c' => CategorySource(value),
      'p' => PersonSource(int.parse(value)),
      _ => throw FormatException('bad media source: $s'),
    };
  }
}

final class AlbumSource extends MediaSourceKey {
  const AlbumSource(this.albumId);

  final String albumId;

  @override
  String get encoded => 'a:$albumId';
}

final class CategorySource extends MediaSourceKey {
  const CategorySource(this.key);

  final String key;

  @override
  String get encoded => 'c:$key';
}

final class PersonSource extends MediaSourceKey {
  const PersonSource(this.personId);

  final int personId;

  @override
  String get encoded => 'p:$personId';
}
