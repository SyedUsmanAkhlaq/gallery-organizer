import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/photo_manager_permission_repository.dart';
import '../domain/gallery_access.dart';

final permissionRepositoryProvider = Provider<PermissionRepository>(
  (ref) => PhotoManagerPermissionRepository(),
);

final permissionControllerProvider =
    NotifierProvider<PermissionController, GalleryAccess>(
  PermissionController.new,
);

/// Bumped whenever the set of visible media may have changed (permission
/// upgraded, partial selection edited). Gallery providers watch it.
final galleryEpochProvider =
    NotifierProvider<GalleryEpoch, int>(GalleryEpoch.new);

class GalleryEpoch extends Notifier<int> {
  @override
  int build() => 0;

  void bump() => state++;
}

class PermissionController extends Notifier<GalleryAccess> {
  bool _inFlight = false;

  @override
  GalleryAccess build() => GalleryAccess.unknown;

  PermissionRepository get _repo => ref.read(permissionRepositoryProvider);

  Future<void> request() async {
    if (_inFlight) return;
    _inFlight = true;
    try {
      final previous = state;
      final next = await _repo.request();
      state = next;
      if (previous != GalleryAccess.unknown) {
        ref.read(galleryEpochProvider.notifier).bump();
      }
    } catch (_) {
      state = GalleryAccess.denied;
    } finally {
      _inFlight = false;
    }
  }

  Future<void> openSettings() => _repo.openSettings();

  Future<void> changeSelection() async {
    await _repo.changeSelection();
    ref.read(galleryEpochProvider.notifier).bump();
  }
}
