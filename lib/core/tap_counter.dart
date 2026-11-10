/// Counts rapid taps (e.g. 7 taps on the version label opens the debug
/// screen). Taps further apart than [window] restart the count.
class TapCounter {
  TapCounter({this.target = 7, this.window = const Duration(seconds: 2)});

  final int target;
  final Duration window;
  int _count = 0;
  DateTime? _last;

  /// Registers a tap at [now]; returns true when [target] is reached
  /// (and resets).
  bool tap(DateTime now) {
    final last = _last;
    _count = (last != null && now.difference(last) <= window) ? _count + 1 : 1;
    _last = now;
    if (_count >= target) {
      _count = 0;
      _last = null;
      return true;
    }
    return false;
  }

  /// Taps still needed (for "N taps away" hints).
  int get remaining => target - _count;
}
