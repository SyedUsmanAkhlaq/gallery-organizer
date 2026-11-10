import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';
import 'bridge/native_api.g.dart';
import 'core/error_reporter.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  ErrorReporter(GalleryHostApi()).install();
  runApp(const ProviderScope(child: GalleryApp()));
}
