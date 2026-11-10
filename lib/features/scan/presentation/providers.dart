import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/native_gallery_repository.dart';
import '../domain/scan_snapshot.dart';

final nativeRepositoryProvider =
    Provider<NativeGalleryRepository>((ref) => NativeGalleryRepository());

/// Current scan status: an initial `getScanStatus()` snapshot, then live
/// events from the native progress stream.
final scanStatusProvider = StreamProvider<ScanSnapshot>((ref) async* {
  final repo = ref.watch(nativeRepositoryProvider);
  final live = StreamController<ScanSnapshot>();
  final sub = repo.progress().listen(live.add, onError: live.addError);
  ref.onDispose(() {
    sub.cancel();
    live.close();
  });
  yield await repo.status();
  yield* live.stream;
});
