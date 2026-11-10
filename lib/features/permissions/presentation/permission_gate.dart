import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/gallery_access.dart';
import 'permission_controller.dart';

/// Shows [child] only once the user granted full or partial media access.
class PermissionGate extends ConsumerStatefulWidget {
  const PermissionGate({super.key, required this.child});

  final Widget child;

  @override
  ConsumerState<PermissionGate> createState() => _PermissionGateState();
}

class _PermissionGateState extends ConsumerState<PermissionGate>
    with WidgetsBindingObserver {
  /// Set when we sent the user to system settings; on return we re-check.
  bool _awaitingSettings = false;

  PermissionController get _controller =>
      ref.read(permissionControllerProvider.notifier);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // Providers must not be modified during build: defer to after frame.
    WidgetsBinding.instance.addPostFrameCallback((_) => _controller.request());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) return;
    if (_awaitingSettings) {
      _awaitingSettings = false;
      _controller.request();
    } else if (ref.read(permissionControllerProvider) ==
        GalleryAccess.limited) {
      // The partial selection may have been edited in the system picker.
      ref.read(galleryEpochProvider.notifier).bump();
    }
  }

  Future<void> _openSettings() async {
    _awaitingSettings = true;
    await _controller.openSettings();
  }

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(permissionControllerProvider);
    // Expose "open settings" to descendants (limited-access banner).
    return _GateActions(
      openSettings: _openSettings,
      child: switch (access) {
        GalleryAccess.unknown =>
          const Scaffold(body: Center(child: CircularProgressIndicator())),
        GalleryAccess.denied => _DeniedScreen(
            onOpenSettings: _openSettings,
            onRetry: _controller.request,
          ),
        GalleryAccess.full || GalleryAccess.limited => widget.child,
      },
    );
  }
}

class _GateActions extends InheritedWidget {
  const _GateActions({required this.openSettings, required super.child});

  final Future<void> Function() openSettings;

  @override
  bool updateShouldNotify(_GateActions oldWidget) => false;
}

/// Opens system settings and re-checks permission when the user comes back.
Future<void> openPermissionSettings(BuildContext context) {
  final actions = context.getInheritedWidgetOfExactType<_GateActions>();
  return actions?.openSettings() ?? Future.value();
}

class _DeniedScreen extends StatelessWidget {
  const _DeniedScreen({required this.onOpenSettings, required this.onRetry});

  final VoidCallback onOpenSettings;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.photo_library_outlined,
                  size: 72, color: theme.colorScheme.primary),
              const SizedBox(height: 24),
              Text('Photo access needed',
                  style: theme.textTheme.headlineSmall),
              const SizedBox(height: 12),
              Text(
                'Gallery Organizer needs to read your photos and videos to '
                'show and organize them. Everything stays on this phone; the '
                'app has no internet permission.',
                textAlign: TextAlign.center,
                style: theme.textTheme.bodyMedium,
              ),
              const SizedBox(height: 32),
              FilledButton.icon(
                onPressed: onOpenSettings,
                icon: const Icon(Icons.settings),
                label: const Text('Open settings'),
              ),
              const SizedBox(height: 8),
              TextButton(onPressed: onRetry, child: const Text('Try again')),
            ],
          ),
        ),
      ),
    );
  }
}
