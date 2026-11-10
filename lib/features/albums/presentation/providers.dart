import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../permissions/presentation/permission_controller.dart';
import '../data/photo_manager_media_repository.dart';
import '../domain/media.dart';

final mediaRepositoryProvider = Provider<MediaRepository>(
  (ref) => PhotoManagerMediaRepository(),
);

final albumsProvider = FutureProvider<List<Album>>((ref) {
  ref.watch(galleryEpochProvider);
  return ref.read(mediaRepositoryProvider).fetchAlbums();
});
