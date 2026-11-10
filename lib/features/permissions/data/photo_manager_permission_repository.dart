import 'package:photo_manager/photo_manager.dart';

import '../domain/gallery_access.dart';

/// Maps photo_manager's permission state to [GalleryAccess].
///
/// We deliberately use `hasAccess` semantics (full OR limited) instead of
/// `isAuth` (full only): with Android 14 partial access `isAuth` is false even
/// though the user can see some photos.
GalleryAccess mapPermissionState(PermissionState state) {
  if (state == PermissionState.authorized) return GalleryAccess.full;
  if (state == PermissionState.limited) return GalleryAccess.limited;
  return state.hasAccess ? GalleryAccess.limited : GalleryAccess.denied;
}

class PhotoManagerPermissionRepository implements PermissionRepository {
  static const _option = PermissionRequestOption(
    androidPermission: AndroidPermission(
      // Images + videos (READ_MEDIA_IMAGES + READ_MEDIA_VIDEO on API 33+).
      type: RequestType.common,
      mediaLocation: false,
    ),
  );

  @override
  Future<GalleryAccess> request() async {
    final state = await PhotoManager.requestPermissionExtend(
      requestOption: _option,
    );
    return mapPermissionState(state);
  }

  @override
  Future<void> openSettings() => PhotoManager.openSetting();

  @override
  Future<void> changeSelection() async {
    try {
      await PhotoManager.presentLimited();
    } catch (_) {
      // Not supported on this OS version: fall back to system settings.
      await PhotoManager.openSetting();
    }
  }
}
