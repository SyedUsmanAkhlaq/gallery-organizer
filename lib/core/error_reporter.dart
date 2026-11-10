import 'dart:async';

import 'package:flutter/foundation.dart';

import '../bridge/native_api.g.dart';

/// Sends Flutter/Dart errors to the native error log (one log for both
/// sides, viewable on the debug screen and shareable as a file).
class ErrorReporter {
  ErrorReporter(this._api);

  final GalleryHostApi _api;

  void install() {
    final previous = FlutterError.onError;
    FlutterError.onError = (details) {
      previous?.call(details);
      report('flutter', details.exceptionAsString(), details.stack);
    };
    PlatformDispatcher.instance.onError = (error, stack) {
      report('dart', '$error', stack);
      return true;
    };
  }

  void report(String source, String message, StackTrace? stack) {
    unawaited(_send(source, message, stack));
  }

  Future<void> _send(String source, String message, StackTrace? stack) async {
    try {
      await _api.logError(source, message, stack?.toString());
    } catch (_) {
      // Never let error reporting itself throw.
    }
  }
}
