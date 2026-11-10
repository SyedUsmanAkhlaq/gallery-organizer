import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/permissions/presentation/permission_gate.dart';
import 'router.dart';
import 'theme.dart';

class GalleryApp extends ConsumerWidget {
  const GalleryApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    return MaterialApp.router(
      title: 'Gallery Organizer',
      debugShowCheckedModeBanner: false,
      theme: buildBlackTheme(),
      themeMode: ThemeMode.dark,
      darkTheme: buildBlackTheme(),
      routerConfig: router,
      // The gate sits above the router: until the user grants (full or
      // partial) media access, the navigator is not shown at all.
      builder: (context, child) =>
          PermissionGate(child: child ?? const SizedBox.shrink()),
    );
  }
}
