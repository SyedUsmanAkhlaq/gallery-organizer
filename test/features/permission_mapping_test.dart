import 'package:flutter_test/flutter_test.dart';
import 'package:gallery_organizer/features/permissions/data/photo_manager_permission_repository.dart';
import 'package:gallery_organizer/features/permissions/domain/gallery_access.dart';
import 'package:photo_manager/photo_manager.dart';

void main() {
  test('maps photo_manager states using hasAccess semantics', () {
    expect(mapPermissionState(PermissionState.authorized), GalleryAccess.full);
    expect(mapPermissionState(PermissionState.limited), GalleryAccess.limited);
    expect(mapPermissionState(PermissionState.denied), GalleryAccess.denied);
    expect(
      mapPermissionState(PermissionState.notDetermined),
      GalleryAccess.denied,
    );
    expect(mapPermissionState(PermissionState.restricted), GalleryAccess.denied);
  });

  test('canBrowse covers full and partial access only', () {
    expect(GalleryAccess.full.canBrowse, isTrue);
    expect(GalleryAccess.limited.canBrowse, isTrue);
    expect(GalleryAccess.denied.canBrowse, isFalse);
    expect(GalleryAccess.unknown.canBrowse, isFalse);
  });
}
