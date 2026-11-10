import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/native_gallery_repository.dart';
import '../domain/analysis.dart';
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

/// Changes when the scan makes meaningful progress (state/stage change or
/// every 100 items), so analysis lists refresh while a scan runs.
final analysisTickProvider = Provider<int>((ref) {
  return ref.watch(scanStatusProvider.select((a) {
    final s = a.value;
    if (s == null) return 0;
    return s.state.index * 10000000 + s.stage.index * 1000000 + s.scanned ~/ 100;
  }));
});

/// Bumped after user corrections (rename, merge, hide, delete data…).
final analysisEditsProvider =
    NotifierProvider<AnalysisEdits, int>(AnalysisEdits.new);

class AnalysisEdits extends Notifier<int> {
  @override
  int build() => 0;

  void bump() => state++;
}

final categorySummariesProvider =
    FutureProvider.autoDispose<List<CategorySummary>>((ref) {
  ref.watch(analysisTickProvider);
  ref.watch(analysisEditsProvider);
  return ref.watch(nativeRepositoryProvider).categories();
});

/// Whether the People tab also lists hidden people.
final showHiddenPeopleProvider =
    NotifierProvider<ShowHiddenPeople, bool>(ShowHiddenPeople.new);

class ShowHiddenPeople extends Notifier<bool> {
  @override
  bool build() => false;

  void toggle() => state = !state;
}

final peopleProvider = FutureProvider.autoDispose<List<Person>>((ref) {
  ref.watch(analysisTickProvider);
  ref.watch(analysisEditsProvider);
  final includeHidden = ref.watch(showHiddenPeopleProvider);
  return ref.watch(nativeRepositoryProvider).people(includeHidden: includeHidden);
});

final analysisSettingsProvider =
    FutureProvider.autoDispose<AnalysisSettings>((ref) {
  ref.watch(analysisEditsProvider);
  return ref.watch(nativeRepositoryProvider).settings();
});
