/// What the app is allowed to see in the user's gallery.
enum GalleryAccess {
  /// Not asked yet (app just started).
  unknown,

  /// All photos and videos (READ_MEDIA_IMAGES/VIDEO granted).
  full,

  /// Android 14+ "Select photos and videos": only user-picked items.
  limited,

  /// Nothing visible.
  denied;

  bool get canBrowse => this == full || this == limited;
}

abstract interface class PermissionRepository {
  /// Asks for media access (shows the system dialog when needed).
  Future<GalleryAccess> request();

  /// Opens the app's system settings page.
  Future<void> openSettings();

  /// Android 14+: re-opens the system photo picker so the user can change the
  /// partial selection (or choose "Allow all").
  Future<void> changeSelection();
}
